package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.*
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.json.JsonStructuredData
import org.jetbrains.demo.agent.koog.subgraph

inline fun <reified Input, reified Output> simpleStructuredInputOutput(
    name: String? = null,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline prompt: suspend AIAgentContextBase.(input: Input) -> Message.Response,
): AIAgentSubgraphDelegate<Input, Output> = subgraph(name, toolSelectionStrategy, llmModel, llmParams) {
    val sendInput by node<Input, Message.Response> { prompt(it) }

    val executeTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()
    val structuredOutput by nodeLLMRequestStructured(
        "research-points-of-interest-output",
        JsonStructuredData.createJsonStructure<Output>(),
        5,
        OpenAIModels.Reasoning.O1
    )

    edge(nodeStart forwardTo sendInput)
    edge(sendInput forwardTo executeTool onToolCall { true })
    edge(sendInput forwardTo structuredOutput onAssistantMessage { true })

    edge(executeTool forwardTo sendToolResult)
    edge(sendToolResult forwardTo structuredOutput onAssistantMessage { true })
    edge(sendToolResult forwardTo executeTool onToolCall { true })
    edge(structuredOutput forwardTo nodeFinish transformed { it.getOrThrow().structure })
}
