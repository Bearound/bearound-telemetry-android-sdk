package io.bearound.telemetry.models

import io.bearound.telemetry.BuildConfig

/**
 * SDK information sent with each request
 */
data class SDKInfo(
    val version: String = BuildConfig.SDK_VERSION,
    val platform: String = "android",
    val appId: String,
    val build: Int,
    val technology: String = "android-native"
)

