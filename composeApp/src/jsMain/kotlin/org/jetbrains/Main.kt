package org.jetbrains

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavController
import androidx.navigation.bindToBrowserNavigation
import org.jetbrains.demo.config.WebAuthState
import org.jetbrains.demo.di.appModule
import org.jetbrains.demo.ui.App
import org.jetbrains.skiko.wasm.onWasmReady
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    onWasmReady {
        startKoin { modules(appModule) }
        ComposeViewport("ComposeApp") {
            App(NavController::bindToBrowserNavigation, WebAuthState)
        }
    }
}
