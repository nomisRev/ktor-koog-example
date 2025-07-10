package com.example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.*
import ai.koog.prompt.executor.llms.*
import ai.koog.prompt.markdown.markdown
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
        promptExecutor = executor,
        strategy = singleRunStrategy(),
        toolRegistry = allTools,
        agentConfig = AIAgentConfig(
            prompt = prompt("travel-assistant-agent") {
                system(markdown {
                    +"You're an expert travel assistant helping users reach their destination in a reliable way."
                    newline()
                    +"You have the following tools available to you:"
                    bulleted {
                        for (tool in allTools.tools) {
                            item(tool.name)
                        }
                    }
                    newline()
                    header(1, "Task description:")
                    +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
                    +"DO NOT STOP UNTIL YOU'VE FOUND ALL THE ANSWERS!"
                })
            },
            model = OpenAIModels.Reasoning.GPT4oMini,
            maxAgentIterations = 100
        ),
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

    agent.run(question)
}
