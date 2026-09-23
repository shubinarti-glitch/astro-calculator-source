package ru.astrosmap.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.SyncManager
import ru.astrosmap.app.ui.AstroRoot
import ru.astrosmap.app.ui.theme.AstroTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationRoute = mutableStateOf<String?>(null)

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var analytics: ru.astrosmap.app.data.Analytics

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ru.astrosmap.app.ui.LangPref.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системная заставка (иконка на тёмном) висит до первого кадра. Ловим её уход,
        // чтобы своя анимация стартовала ровно тогда, когда станет видимой, а не раньше.
        val splashScreen = installSplashScreen()
        val systemSplashGone = mutableStateOf(false)
        splashScreen.setOnExitAnimationListener { provider ->
            provider.remove()
            systemSplashGone.value = true
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        notificationRoute.value = intent?.getStringExtra(ru.astrosmap.app.data.DailyNotify.ROUTE)
        // Заставка — только на холодном запуске. При пересоздании активности (смена темы,
        // поворот, системный шрифт) системная заставка не показывается, слушатель её ухода
        // не срабатывает — и заставка висела бы вечно.
        val coldStart = savedInstanceState == null
        setContent {
            AstroTheme {
                var showSplash by rememberSaveable { mutableStateOf(coldStart) }
                Crossfade(targetState = showSplash, label = "splash") { splash ->
                    if (splash) {
                        ru.astrosmap.app.ui.SplashScreen(
                            start = systemSplashGone.value,
                            onFinished = { showSplash = false },
                        )
                    } else {
                        AstroRoot(notificationRoute = notificationRoute.value) { notificationRoute.value = null }
                    }
                }
            }
        }
        val fromNotification = intent?.getBooleanExtra(ru.astrosmap.app.data.DailyNotify.FROM_NOTIFICATION, false) == true
        analytics.track(if (fromNotification) "notif_opened" else "app_open")
        // Переустановка/обновление сбрасывает очередь WorkManager — восстанавливаем расписание.
        if (ru.astrosmap.app.data.DailyNotify.isEnabled(this)) {
            ru.astrosmap.app.data.DailyNotify.schedule(this)
        }
        // Синхронизация карт при каждом запуске (если выполнен вход и есть сеть).
        lifecycleScope.launch { syncManager.sync() }
        // Обновить виджеты на домашнем экране — подхватить свежие данные и выбранный язык.
        if (ru.astrosmap.app.widget.WidgetUpdater.hasAnyWidget(this)) {
            ru.astrosmap.app.widget.WidgetUpdater.refreshNow(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRoute.value = intent.getStringExtra(ru.astrosmap.app.data.DailyNotify.ROUTE)
        if (intent.getBooleanExtra(ru.astrosmap.app.data.DailyNotify.FROM_NOTIFICATION, false)) {
            analytics.track("notif_opened")
        }
    }
}
