package org.jetbrains.demo.agent.tools

import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AppConfig
import org.jetbrains.demo.agent.koog.descriptors

data class Tools(
    val duckDuckGoSearchTool: DuckDuckGoSearchTool,
    val weatherTool: WeatherTool,
    val googleMaps: ToolRegistry,
) {
    fun registry() = ToolRegistry {
        tools(MathTools)
        tools(weatherTool)
        tools(duckDuckGoSearchTool)
        tools(googleMaps.tools)
    }

    fun mathWebAndMaps() = ToolSelectionStrategy.Tools(
        ToolRegistry {
            tools(duckDuckGoSearchTool)
            tools(MathTools)
            tools(googleMaps.tools)
        }.descriptors()
    )

    fun web() = ToolSelectionStrategy.Tools(
        ToolRegistry { tools(duckDuckGoSearchTool) }.descriptors()
    )
}

suspend fun Application.tools(config: AppConfig): Tools {
    val googleMaps = McpToolRegistryProvider.fromSseTransport("http://localhost:9011")
    val weather = WeatherTool(httpClient(), config.weatherApiUrl)
    val duckDuckGoSearchTool = DuckDuckGoSearchTool(httpClient())
    return Tools(googleMaps = googleMaps, weatherTool = weather, duckDuckGoSearchTool = duckDuckGoSearchTool)
}

private suspend fun McpToolRegistryProvider.fromSseTransport(url: String): ToolRegistry =
    fromTransport(McpToolRegistryProvider.defaultSseTransport(url))

private fun Application.httpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
        })
    }
}.closeOnStop(this)

private fun <A : AutoCloseable> A.closeOnStop(application: Application): A = apply {
    application.monitor.subscribe(ApplicationStopped) {
        application.environment.log.info("Closing ${this::class.simpleName}...")
        close()
        application.environment.log.info("Closed ${this::class.simpleName}")
    }
}