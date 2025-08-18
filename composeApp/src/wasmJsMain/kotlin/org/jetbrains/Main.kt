package org.jetbrains

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.bindToNavigation
import kotlinx.browser.window
import org.jetbrains.demo.config.WebAuthState
import org.jetbrains.demo.di.appModule
import org.jetbrains.demo.ui.App
import org.jetbrains.demo.ui.AuthState
import org.koin.core.context.startKoin

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalBrowserHistoryApi::class
)
fun main() {
    startKoin { modules(appModule) }
    ComposeViewport("ComposeApp") {
        App({ controller ->
            controller.bindToBrowserNavigation { entry ->
                val route = entry.destination.route.orEmpty()
                when {
                    route == "chat" -> ""
                    else -> route
                }
            }
        }, WebAuthState)
    }
}
