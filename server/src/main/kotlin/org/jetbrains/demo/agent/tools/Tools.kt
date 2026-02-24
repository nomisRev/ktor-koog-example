package org.jetbrains.demo.agent.tools

import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.mcp.McpToolRegistryProvider
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AppConfig

data class Tools(
    val duckDuckGoSearchTool: DuckDuckGoSearchTool,
    val weatherTool: WeatherTool,
    val googleMaps: ToolRegistry,
) {
    fun registry() = ToolRegistry {
        tools(duckDuckGoSearchTool)
        tools(googleMaps.tools)
        tools(weatherTool)
        tool(::addDate)
    }

    fun mapsAndWeather() = ToolSelectionStrategy.Tools(
        ToolRegistry {
            tools(duckDuckGoSearchTool)
            tools(googleMaps.tools)
            tools(weatherTool)
            tool(::addDate)
        }.tools.map { it.descriptor }
    )

    fun mapsAndWeb() = ToolSelectionStrategy.Tools(
        ToolRegistry {
            tools(googleMaps.tools)
            tools(duckDuckGoSearchTool)
            tool(::addDate)
        }.tools.map { it.descriptor }
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
        }, contentType = ContentType.Application.Json)
        json(Json {
            prettyPrint = false
            isLenient = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
        }, contentType = ContentType("application", "x-javascript"))
    }
}.closeOnStop(this)

private fun <A : AutoCloseable> A.closeOnStop(application: Application): A = apply {
    application.monitor.subscribe(ApplicationStopped) {
        application.environment.log.info("Closing ${this::class.simpleName}...")
        close()
        application.environment.log.info("Closed ${this::class.simpleName}")
    }
}
