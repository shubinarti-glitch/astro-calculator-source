package ru.astrosmap.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileReportRequest(
    val id: String,
    val description: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("version_code") val versionCode: Int,
    val store: String,
    @SerialName("android_api") val androidApi: Int,
    val consent: Boolean,
)

@Serializable
data class MobileReportReceipt(val id: String)
