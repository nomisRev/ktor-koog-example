package com.example

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.server.util.getOrFail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.testcontainers.containers.DockerMcpGatewayContainer

@Serializable
data class AppConfig(
    val host: String,
    val port: Int,
    val apiKey: String,
)

fun main() {
    val config = ApplicationConfig("application.yaml")
        .property("app")
        .getAs<AppConfig>()

    embeddedServer(Netty, host = config.host, port = config.port) {
        app(config)
    }.start(wait = true)
}

suspend fun Application.app(config: AppConfig) {
    val executor = executor(config.apiKey)
    val googleMaps = mcpGoogleMaps()
    val weather = ToolRegistry { tools(WeatherTool(httpClient()).asTools()) }
    val allTools = (googleMaps + weather)
    environment.log.info(allTools.tools.joinToString(prefix = "Running with tools:") { it.name })

    install(SSE)
    routing {
        sse("/plan") {
            val userQuestion = call.request.queryParameters.getOrFail("question")
            agent(executor, allTools, userQuestion) {
                println(it)
                send(data = Json.encodeToString(AgentEvent.serializer(), it))
            }
        }
    }
}

private fun executor(apiKey: String): MultiLLMPromptExecutor {
    val openAIClient = OpenAILLMClient(apiKey)
    val ollama = OllamaClient()
    return MultiLLMPromptExecutor(LLMProvider.OpenAI to openAIClient, LLMProvider.Ollama to ollama)
}

private fun httpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
}

private suspend fun mcpGoogleMaps(): ToolRegistry =
    DockerMcpGatewayContainer("docker/mcp-gateway:latest").withServer(
        "google-maps",
        "maps_directions",
        "maps_distance_matrix",
        "maps_elevation",
        "maps_geocode",
        "maps_place_details",
        "maps_reverse_geocode",
        "maps_search_places"
    ).also { it.start() }.toToolRegistry()
