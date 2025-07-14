package com.example.agent

import ai.koog.agents.core.tools.ToolResult
import kotlinx.serialization.Serializable

//TODO Properly remodel these events according to the actual tools that can be called
//   The tools that can be called are the defined google maps tools, and our custom weather tool
//   We should also include some information / descriptions when this starts-and-stops and for which values
//   This will be used by a UI to nicely implement a nice UX.
@Serializable
sealed interface AgentEvent {
    @Serializable
    data class ToolCall(val name: String) : AgentEvent

    @Serializable
    data class ToolCallResult(val name: String, val result: String?) : AgentEvent {
        constructor(name: String, result: ToolResult?) : this(name, result?.toStringDefault())
    }

    @Serializable
    data class AgentFinished(val name: String, val result: String?) : AgentEvent
}