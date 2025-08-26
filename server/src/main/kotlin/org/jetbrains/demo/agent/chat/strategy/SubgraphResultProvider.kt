package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.jetbrains.demo.agent.koog.tools.toolDescription

@Suppress("FunctionName")
inline fun <reified A : SubgraphResult> SubgraphResultProvider(
    name: String,
    description: String
): ProvideSubgraphResult<A> =
    object : ProvideSubgraphResult<A>() {
        override val argsSerializer: KSerializer<A> = serializer<A>()
        override val descriptor: ToolDescriptor = argsSerializer.descriptor.toolDescription(
            name = name,
            description = description,
        )

        override suspend fun execute(args: A): A = args
    }
