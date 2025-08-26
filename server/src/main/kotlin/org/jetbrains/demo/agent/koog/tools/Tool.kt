package org.jetbrains.demo.agent.koog.tools

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import com.sun.beans.introspect.PropertyInfo
import com.sun.tools.javac.tree.TreeInfo.args
import com.xemantic.ai.tool.schema.ArraySchema
import com.xemantic.ai.tool.schema.BooleanSchema
import com.xemantic.ai.tool.schema.IntegerSchema
import com.xemantic.ai.tool.schema.JsonSchema
import com.xemantic.ai.tool.schema.NumberSchema
import com.xemantic.ai.tool.schema.ObjectSchema
import com.xemantic.ai.tool.schema.StringSchema
import com.xemantic.ai.tool.schema.generator.generateSchema
import com.xemantic.ai.tool.schema.generator.jsonSchemaOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.StructureKind
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
        when (tool.inputSerializer.descriptor.kind) {
            is PrimitiveKind,
            is StructureKind.LIST,
                -> SerializedToolArgs.serializer(tool.inputSerializer)

            else -> SerializedToolArgsSerializer(tool.inputSerializer)
        }

    @Serializable
    @JvmInline
    value class SerializedToolArgs<Input>(val value: Input) : ToolArgs

    class SerializedResult<Output>(
        val value: Output,
        private val format: StringFormat,
        private val serializer: KSerializer<Output>,
    ) : ToolResult {
        override fun toStringDefault(): String = format.encodeToString(serializer, value)
    }

    fun JsonSchema.toToolParameterType(): ToolParameterType = when (this) {
        is ArraySchema -> ToolParameterType.List(this.items.toToolParameterType())
        is BooleanSchema -> ToolParameterType.Boolean
        is IntegerSchema -> ToolParameterType.Integer
        is NumberSchema -> ToolParameterType.Float
        is ObjectSchema -> ToolParameterType.Object(
            properties = this.properties.orEmpty().map { (name, schema) ->
                ToolParameterDescriptor(
                    name = name,
                    description = "TODO: @LLMDescription",
                    type = schema.toToolParameterType()
                )
            },
            requiredProperties = required.orEmpty(),
            additionalProperties = false,
            additionalPropertiesType = null
        )

        is StringSchema -> ToolParameterType.String
        is JsonSchema.Const -> ToolParameterType.String
        is JsonSchema.Ref -> TODO("Impossible, always resolved")
    }

    override val descriptor: ToolDescriptor
        get() = when (val schema =
            generateSchema(tool.inputSerializer.descriptor, inlineRefs = true, additionalProperties = false)) {
            is ArraySchema -> ToolParameterType.List(schema.items.toToolParameterType()).asValueTool()
            is BooleanSchema -> ToolParameterType.Boolean.asValueTool()
            is NumberSchema -> ToolParameterType.Float.asValueTool()
            is JsonSchema.Const, is StringSchema -> ToolParameterType.String.asValueTool()
            is IntegerSchema -> ToolParameterType.Integer.asValueTool()
            is ObjectSchema -> ToolDescriptor(
                name = tool.inputSerializer.descriptor.serialName,
                description = "TODO: @LLMDescription",
                requiredParameters = schema.properties.orEmpty()
                    .filter { schema.required.orEmpty().contains(it.key) }.map {
                        ToolParameterDescriptor(
                            name = it.key,
                            description = "TODO: @LLMDescription",
                            type = it.value.toToolParameterType()
                        )
                    }
            )

            is JsonSchema.Ref -> TODO("Impossible, always resolved")
        }

    private fun ToolParameterType.asValueTool() =
        ToolDescriptor(
            name = tool.name,
            description = tool.description,
            requiredParameters = listOf(
                ToolParameterDescriptor(name = "value", description = "TODO: @LLMDescription", this)
            )
        )
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
