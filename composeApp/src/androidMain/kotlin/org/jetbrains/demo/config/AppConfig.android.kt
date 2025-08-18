package org.jetbrains.demo.config

import org.jetbrains.BuildConfig

actual fun AppConfig() = object : AppConfig {
    override val apiBaseUrl: String = BuildConfig.API_BASE_URL
}