package org.jetbrains.demo.di

import org.jetbrains.demo.auth.DesktopTokenProvider
import org.jetbrains.demo.auth.EncryptedPreferences
import org.jetbrains.demo.auth.TokenProvider
import org.jetbrains.demo.config.DesktopConfig
import org.koin.dsl.module

val desktopModule = module {
    single<TokenProvider> {
        DesktopTokenProvider(
            DesktopConfig,
            EncryptedPreferences("auth", "my-super-secret"),
            get()
        )
    }
}