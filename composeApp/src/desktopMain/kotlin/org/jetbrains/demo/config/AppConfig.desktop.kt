package org.jetbrains.demo.config

actual fun AppConfig(): AppConfig = Config

private object Config: AppConfig {
    override val apiBaseUrl: String =
        System.getProperty("API_BASE_URL")
            ?: System.getenv("API_BASE_URL")
            ?: "http://localhost:8080"
}
