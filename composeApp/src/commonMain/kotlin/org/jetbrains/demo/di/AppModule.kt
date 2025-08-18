package org.jetbrains.demo.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import org.jetbrains.demo.auth.AuthViewModel
import org.jetbrains.demo.chat.ChatViewModel
import org.jetbrains.demo.chat.repository.ChatRepository
import org.jetbrains.demo.chat.repository.HttpChatRepository
import org.jetbrains.demo.config.AppConfig
import org.jetbrains.demo.journey.JourneyPlannerViewModel
import org.jetbrains.demo.agent.AgentPlannerViewModel
import org.jetbrains.demo.network.HttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    single<Logger> { Logger(config = StaticConfig(minSeverity = Severity.Debug)) }
    singleOf(::AppConfig)
    single { HttpClient(get(), get(), getOrNull()) }
    singleOf(::HttpChatRepository) bind ChatRepository::class
    viewModelOf(::AuthViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::JourneyPlannerViewModel)
    viewModelOf(::AgentPlannerViewModel)
}
