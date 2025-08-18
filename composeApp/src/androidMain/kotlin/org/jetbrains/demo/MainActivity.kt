package org.jetbrains.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.jetbrains.demo.auth.AuthViewModel
import org.jetbrains.demo.di.androidModule
import org.jetbrains.demo.di.appModule
import org.jetbrains.demo.ui.App
import org.jetbrains.demo.ui.AuthSession
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinMultiplatformApplication
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinConfiguration

class MainActivity : ComponentActivity() {

    @OptIn(KoinExperimentalAPI::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KoinMultiplatformApplication(
                koinConfiguration {
                    modules(androidModule, appModule)
                }) {
                App({  }, AuthSession(koinViewModel<AuthViewModel>()))
            }
        }
    }
}
