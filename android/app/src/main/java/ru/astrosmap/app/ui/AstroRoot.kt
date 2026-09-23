package ru.astrosmap.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.astrosmap.app.R
import ru.astrosmap.app.ui.form.ChartFormScreen
import ru.astrosmap.app.ui.saved.SavedScreen
import ru.astrosmap.app.ui.view.ChartViewScreen

/** Корневые разделы приложения (нижняя навигация). */
enum class Section(val route: String, val titleRes: Int, val iconRes: Int) {
    Today("today", R.string.section_today, R.drawable.ic_today),
    Chart("chart", R.string.section_chart, R.drawable.ic_chart),
    Saved("saved", R.string.section_saved, R.drawable.ic_saved),
    Tools("tools", R.string.section_tools, R.drawable.ic_tools),
    Account("account", R.string.section_account, R.drawable.ic_account),
}

@Composable
fun AstroRoot(notificationRoute: String? = null, onNotificationRouteHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var hintedIndex by remember { mutableIntStateOf(-1) }
    val compactLabels = LocalDensity.current.fontScale >= 1.3f

    NotificationOptInPrompt()

    LaunchedEffect(notificationRoute) {
        val route = notificationRoute ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onNotificationRouteHandled()
    }

    // Один ненавязчивый проход после входа: показывает, что нижняя панель интерактивна.
    // Любой тап немедленно прекращает подсказку.
    LaunchedEffect(Unit) {
        delay(3_000)
        var index = 0
        while (true) {
            hintedIndex = index
            delay(850)
            hintedIndex = -1
            index = (index + 1) % Section.entries.size
            delay(9_150)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Section.entries.forEach { section ->
                    val index = Section.entries.indexOf(section)
                    val pulse by animateFloatAsState(
                        targetValue = if (hintedIndex == index) 1f else 0f,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        label = "bottom_nav_hint_${section.route}",
                    )
                    NavigationBarItem(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        selected = currentRoute == section.route,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        alwaysShowLabel = !compactLabels,
                        onClick = {
                            navController.navigate(section.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val defaultTint = LocalContentColor.current
                            Box(
                                Modifier
                                    .size(width = 48.dp, height = 36.dp)
                                    .graphicsLayer {
                                        scaleX = 1f + pulse * 0.16f
                                        scaleY = scaleX
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (currentRoute == section.route) 0.13f else pulse * 0.12f,
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(section.iconRes),
                                    contentDescription = stringResource(section.titleRes),
                                    tint = if (pulse > 0.01f) MaterialTheme.colorScheme.primary
                                    else defaultTint,
                                )
                            }
                        },
                        label = {
                            // Двухстрочные подписи допускаем осознанно — иначе длинные обрезаются.
                            Text(
                                stringResource(section.titleRes),
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ru.astrosmap.app.ui.theme.StarryBackground()
            NavHost(
                navController = navController,
                startDestination = Section.Today.route,
                modifier = Modifier.fillMaxSize(),
            ) {
            composable(Section.Today.route) {
                ru.astrosmap.app.ui.today.TodayScreen(
                    onCreateChart = {
                        navController.navigate(Section.Chart.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Section.Chart.route) {
                ChartFormScreen(onCalculated = { navController.navigate("view/0") })
            }
            composable(Section.Saved.route) {
                SavedScreen(onOpen = { id -> navController.navigate("view/$id") })
            }
            composable(Section.Account.route) {
                ru.astrosmap.app.ui.account.AccountScreen(onMaterials = { navController.navigate("materials") })
            }
            composable("materials") { ru.astrosmap.app.ui.saved.SavedMaterialsScreen() }
            composable("tarot") { ru.astrosmap.app.ui.tarot.TarotScreen() }
            composable(Section.Tools.route) {
                ru.astrosmap.app.ui.tools.ToolsScreen(
                    onTransits = { id -> navController.navigate("transit/$id") },
                    onProgression = { id -> navController.navigate("progression/$id") },
                    onForecast = { id -> navController.navigate("forecast/$id") },
                    onSolar = { id -> navController.navigate("return/solar/$id") },
                    onLunar = { id -> navController.navigate("return/lunar/$id") },
                    onSynastry = { a, b -> navController.navigate("synastry/$a/$b") },
                    onLunarCalendar = { navController.navigate("luncal") },
                    onTarot = { navController.navigate("tarot") },
                    onJournal = { navController.navigate("journal") },
                )
            }
            composable("luncal") { ru.astrosmap.app.ui.tools.LunarCalendarScreen() }
            composable("journal") { ru.astrosmap.app.ui.journal.JournalScreen() }
            composable("transit/{id}") { ru.astrosmap.app.ui.tools.TransitScreen() }
            composable("progression/{id}") { ru.astrosmap.app.ui.tools.ProgressionScreen() }
            composable("forecast/{id}") { ru.astrosmap.app.ui.tools.ForecastScreen() }
            composable("return/{type}/{id}") { ru.astrosmap.app.ui.tools.ReturnScreen() }
            composable("synastry/{a}/{b}") { ru.astrosmap.app.ui.tools.SynastryScreen() }
            composable("view/{id}") {
                ChartViewScreen(
                    onEdit = {
                        navController.navigate(Section.Chart.route) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onClosed = { navController.popBackStack() },
                )
            }
            }
            ru.astrosmap.app.ui.assistant.FloatingAssistant(currentRoute)
        }
    }
}

/** Заглушка раздела — заменяется реальным экраном на своём этапе. */
@Composable
private fun PlaceholderScreen(titleRes: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
