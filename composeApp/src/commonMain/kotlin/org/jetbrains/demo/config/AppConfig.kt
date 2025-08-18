package org.jetbrains.demo.config

interface AppConfig {
    val apiBaseUrl: String
}

expect fun AppConfig(): AppConfig
