package ru.astrosmap.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Открывает сайт или его страницу (оплата подписки идёт там — RuStore/APK это разрешают). */
fun openSite(context: Context, url: String = "https://astrosmap.ru/") {
    CustomTabsIntent.Builder().build()
        .launchUrl(context, Uri.parse(url))
}

const val PRIVACY_URL = "https://astrosmap.ru/privacy.html"
const val TERMS_URL = "https://astrosmap.ru/terms.html"
