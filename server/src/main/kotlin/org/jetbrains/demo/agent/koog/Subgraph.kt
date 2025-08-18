package org.jetbrains.demo.agent.koog

import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlin.reflect.KType
import kotlin.reflect.typeOf

inline fun <reified Input, reified Output> subgraph(
    name: String? = null,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    noinline apply: AIAgentSubgraphBuilder<Input, Output>.() -> Unit
) = subgraph(typeOf<Input>(), typeOf<Output>(), name, toolSelectionStrategy, llmModel, llmParams, apply)

fun <Input, Output> subgraph(
    inputType: KType,
    outputType: KType,
    name: String? = null,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    apply: AIAgentSubgraphBuilder<Input, Output>.() -> Unit
) = AIAgentSubgraphBuilder<Input, Output>(
    name,
    inputType,
    outputType,
    toolSelectionStrategy,
    llmModel,
    llmParams
).apply(apply).build()