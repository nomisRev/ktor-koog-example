package org.jetbrains.demo.di

import org.jetbrains.demo.auth.AndroidTokenProvider
import org.jetbrains.demo.auth.TokenProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val androidModule = module {
    singleOf(::AndroidTokenProvider).bind<TokenProvider>()
}