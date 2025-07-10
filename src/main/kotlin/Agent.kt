package com.example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.clients.openai.*
import ai.koog.prompt.executor.llms.*
import kotlinx.serialization.Serializable

@Serializable
sealed interface AgentEvent {

    @Serializable
    data class ToolCall(val name: String) : AgentEvent

    @Serializable
    data class ToolCallResult(val name: String, val result: String?) : AgentEvent

    @Serializable
    data class AgentFinished(val name: String, val result: String?) : AgentEvent
}

suspend fun agent(
    executor: MultiLLMPromptExecutor,
    allTools: ToolRegistry,
    question: String,
    event: suspend (event: AgentEvent) -> Unit
) {
    val agent = AIAgent(
        executor = executor,
        systemPrompt = "You're an AI assistant that helps developers with Kotlin development." +
                "You use the tools at your disposal to get accurate up-to-date information, and answer my questions.",
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
        toolRegistry = allTools,
    ) {
        install(EventHandler) {
            onToolCall { tool, _ -> event(AgentEvent.ToolCall(tool.name)) }
            onToolCallResult { tool, args, result ->
                event(
                    AgentEvent.ToolCallResult(
                        tool.name,
                        result?.toStringDefault()
                    )
                )
            }
            onAgentFinished { name, result -> event(AgentEvent.AgentFinished(name, result)) }
        }
    }

    agent.run {
        +question
        +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
        +"DO NOT STOP UNTIL YOU'VE FOUND ALL THE ANSWERS!"
    }
}
