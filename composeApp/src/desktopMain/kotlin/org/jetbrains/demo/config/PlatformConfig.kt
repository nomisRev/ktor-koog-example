package org.jetbrains.demo.config

object DesktopConfig {
    val clientId: String =
        System.getProperty("AUTH_CLIENT_ID")
            ?: System.getenv("AUTH_CLIENT_ID")
            ?: ""

    val clientSecret =
        System.getProperty("AUTH_CLIENT_SECRET")
            ?: System.getenv("AUTH_CLIENT_SECRET")
            ?: ""
}
