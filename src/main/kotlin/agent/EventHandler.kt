package com.example.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.mcp.McpTool
import com.example.agent.WeatherTool
import com.example.agent.AgentEvent.AgentFinished
import com.example.agent.AgentEvent.GenericToolCall
import com.example.agent.AgentEvent.GenericToolResult
import com.example.agent.AgentEvent.MapsToolCall
import com.example.agent.AgentEvent.MapsToolResult
import com.example.agent.AgentEvent.WeatherToolCall
import com.example.agent.AgentEvent.WeatherToolFailureResult
import com.example.agent.AgentEvent.WeatherToolSuccessResult
import kotlinx.serialization.json.JsonObject

fun AIAgent.FeatureContext.eventHandler(handler: suspend (AgentEvent) -> Unit) {
    install(EventHandler) {
        onToolCall { tool, args -> onToolCalled(tool, args, handler) }
        onToolCallResult { tool, args, result -> onToolCallResult(tool, args, result, handler) }
        onAgentFinished { name, result -> onAgentFinished(name, result, handler) }
    }
}

fun Tool.Args.latitude(): Double? = fromCallable("latitude") { it as? Double }

fun Tool.Args.longitude(): Double? = fromCallable("longitude") { it as? Double }

suspend fun onToolCalled(
    tool: Tool<*, *>,
    toolArgs: Tool.Args,
    handler: suspend (AgentEvent) -> Unit
) {
    val event = when {
        tool.name == "getWeather" -> WeatherToolCall(longitude = toolArgs.longitude(), latitude = toolArgs.latitude())
        tool.name.startsWith("maps_") -> MapsToolCall(
            name = tool.name,
            arguments = toolArgs.mcpArguments(),
            description = "Using Google Maps ${tool.name.removePrefix("maps_")} service"
        )

        else -> GenericToolCall(name = tool.name)
    }

    handler(event)
}

suspend fun onToolCallResult(
    tool: Tool<*, *>,
    toolArgs: Tool.Args,
    result: ToolResult?,
    handler: suspend (AgentEvent) -> Unit
) {
    val event = when {
        tool.name == "getWeather" -> {
            when (result) {
                is WeatherTool.CurrentWeather -> WeatherToolSuccessResult(
                    name = tool.name,
                    longitude = toolArgs.longitude(),
                    latitude = toolArgs.latitude(),
                    currentWeather = result
                )

                is WeatherTool.Text -> WeatherToolFailureResult(
                    name = tool.name, longitude = toolArgs.longitude(),
                    latitude = toolArgs.latitude(), result = result.text
                )

                else -> WeatherToolFailureResult(
                    name = tool.name, longitude = toolArgs.longitude(),
                    latitude = toolArgs.latitude(), result = "No result"
                )
            }
        }

        tool.name.startsWith("maps_") -> MapsToolResult(name = tool.name, result = result?.toStringDefault())
        else -> GenericToolResult(name = tool.name, result = result?.toStringDefault())
    }

    handler(event)
}

suspend fun onAgentFinished(name: String, result: String?, handler: suspend (AgentEvent) -> Unit): Unit =
    handler(AgentFinished(name, result))
