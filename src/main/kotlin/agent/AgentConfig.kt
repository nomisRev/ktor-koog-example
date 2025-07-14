package com.example.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Companion.invoke
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import com.example.AppConfig
import com.example.agent.WeatherTool
import com.example.toToolRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.serialization.json.Json
import org.testcontainers.containers.DockerMcpGatewayContainer

class AgentConfig(val executor: PromptExecutor, val registry: ToolRegistry)

suspend fun Application.agentConfig(config: AppConfig): AgentConfig {
    val executor = executor(config.apiKey)
    val googleMaps = mcpGoogleMaps(config).toToolRegistry()
    val weather = ToolRegistry { tools(WeatherTool(httpClient(), config.weatherApiUrl).asTools()) }
    val allTools = googleMaps + weather
    environment.log.info(allTools.tools.joinToString(prefix = "Running with tools:") { it.name })

    return AgentConfig(executor, allTools)
}

private fun executor(apiKey: String): MultiLLMPromptExecutor {
    val openAIClient = OpenAILLMClient(apiKey)
    val ollama = OllamaClient()
    return MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient, LLMProvider.Ollama to ollama)
}

private fun Application.httpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
}.closeOnStop(this)

private fun Application.mcpGoogleMaps(config: AppConfig): DockerMcpGatewayContainer =
    DockerMcpGatewayContainer(config.mcpGatewayImage).withServer(
        "google-maps",
        "maps_directions",
        "maps_distance_matrix",
        "maps_elevation",
        "maps_geocode",
        "maps_place_details",
        "maps_reverse_geocode",
        "maps_search_places"
    ).also { container ->
        container.start()
        container.closeOnStop(this)
    }

fun <A : AutoCloseable> A.closeOnStop(application: Application): A = apply {
    application.monitor.subscribe(ApplicationStopped) {
        application.environment.log.info("Closing ${this::class.simpleName}...")
        close()
        application.environment.log.info("Closed ${this::class.simpleName}")
    }
}