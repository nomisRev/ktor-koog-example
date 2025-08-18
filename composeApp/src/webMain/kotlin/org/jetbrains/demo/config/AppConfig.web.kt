package org.jetbrains.demo.config

actual fun AppConfig(): AppConfig = Config

private object Config: AppConfig {
    override val apiBaseUrl: String = "http://localhost:8080"
}
