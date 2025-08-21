package org.jetbrains.demo.agent.koog.tools

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import ai.koog.agents.core.tools.Tool as KoogTool

inline fun <reified A, reified B> Tool(
    name: String,
    description: String,
    noinline invoke: suspend (A) -> B
): Tool<A, B> = Tool(name, description, serializer<A>(), serializer<B>(), invoke)

class Tool<A, B>(
    val name: String,
    val description: String,
    val inputSerializer: KSerializer<A>,
    val outputSerializer: KSerializer<B>,
    val invoke: suspend (input: @Serializable A) -> @Serializable B
)

fun <Input, Output> Tool<Input, Output>.asKoogTool(format: StringFormat):
        KoogTool<GenericKoogTool.SerializedToolArgs<Input>, GenericKoogTool.SerializedResult<Output>> =
    GenericKoogTool(format, this)

class GenericKoogTool<Input, Output>(
    private val format: StringFormat,
    private val tool: Tool<Input, Output>,
) : KoogTool<GenericKoogTool.SerializedToolArgs<Input>,
        GenericKoogTool.SerializedResult<Output>>() {

    override suspend fun execute(args: SerializedToolArgs<Input>): SerializedResult<Output> =
        SerializedResult(tool.invoke(args.value), format, tool.outputSerializer)

    override val argsSerializer: KSerializer<SerializedToolArgs<Input>> =
        SerializedToolArgsSerializer(tool.inputSerializer)

    @JvmInline
    value class SerializedToolArgs<Input>(val value: Input) : ToolArgs

    class SerializedResult<Output>(
        val value: Output,
        private val format: StringFormat,
        private val serializer: KSerializer<Output>,
    ) : ToolResult {
        override fun toStringDefault(): String = format.encodeToString(serializer, value)
    }

    override val descriptor: ToolDescriptor = TODO()
}

private class SerializedToolArgsSerializer<Input>(
    private val inputSerializer: KSerializer<Input>
) : KSerializer<GenericKoogTool.SerializedToolArgs<Input>> {

    override val descriptor = inputSerializer.descriptor

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: GenericKoogTool.SerializedToolArgs<Input>
    ) {
        encoder.encodeSerializableValue(inputSerializer, value.value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): GenericKoogTool.SerializedToolArgs<Input> {
        val input = decoder.decodeSerializableValue(inputSerializer)
        return GenericKoogTool.SerializedToolArgs(input)
    }
}
