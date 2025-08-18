package org.jetbrains.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.demo.auth.AuthViewModel
import org.jetbrains.demo.auth.DesktopTokenProvider
import org.jetbrains.demo.di.appModule
import org.jetbrains.demo.di.desktopModule
import org.jetbrains.demo.ui.App
import org.jetbrains.demo.ui.AuthSession
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(desktopModule, appModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Demo App",
    ) {
        App({ }, AuthSession(koinViewModel<AuthViewModel>()))
    }
}