package org.jetbrains.demo

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.ktor.server.application.Application
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.demo.agent.PostgresPersistenceStorageProvider
import org.jetbrains.demo.agent.chat.KoogTravelAgent
import org.jetbrains.demo.agent.chat.TravelAgent
import org.jetbrains.demo.agent.tools.tools
import org.jetbrains.demo.user.ExposedUserRepository
import org.jetbrains.demo.user.UserRepository

class Module(val userRepository: UserRepository, val travelAgent: TravelAgent)

suspend fun Application.module(config: AppConfig): Module {
    val database = database(config.database)
    val userRepository: UserRepository = ExposedUserRepository(database)
    val executor = MultiLLMPromptExecutor(OpenAILLMClient(config.openAIKey), AnthropicLLMClient(config.anthropicKey))
    val travelAgent = KoogTravelAgent(
        config,
        executor,
        async { tools(config) },
        userRepository,
        PostgresPersistenceStorageProvider(database).also { it.migrate() }
    )
    return Module(userRepository, travelAgent)
}
