package org.jetbrains.demo.config

import kotlinx.browser.window
import org.jetbrains.demo.ui.AuthState

object WebAuthState : AuthState {
    override fun hasToken(): Boolean {
        val currentUrl = window.location.href
        return when {
            currentUrl.contains("login") -> false
            else -> true
        }
    }

    override fun clearToken() {
        window.location.href = "/logout"
    }
}