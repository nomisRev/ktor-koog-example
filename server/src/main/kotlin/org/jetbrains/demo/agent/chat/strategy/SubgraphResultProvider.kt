package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.jetbrains.demo.agent.koog.tools.toolDescriptor

@Suppress("FunctionName")
inline fun <reified A : SubgraphResult> SubgraphResultProvider(
    name: String,
    description: String
): ProvideSubgraphResult<A> =
    SubgraphResultProvider(name, description, serializer<A>())

fun <A : SubgraphResult> SubgraphResultProvider(
    name: String,
    description: String,
    serializer: KSerializer<A>
): ProvideSubgraphResult<A> =
    DefaultProvideSubgraphResult(name, description, serializer)

private class DefaultProvideSubgraphResult<A : SubgraphResult>(
    toolName: String,
    private val description: String,
    override val argsSerializer: KSerializer<A>
) : ProvideSubgraphResult<A>() {
    override val descriptor: ToolDescriptor = argsSerializer.descriptor.toolDescriptor(
        name = toolName,
        description = description,
    )

    override suspend fun execute(args: A): A = args
}
