@file:OptIn(ExperimentalSerializationApi::class)

package org.jetbrains.demo.agent.koog.tools

import com.xemantic.ai.tool.schema.JsonSchema
import com.xemantic.ai.tool.schema.generator.generateSchema
import com.xemantic.ai.tool.schema.meta.Description
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.*

@Serializable
data class Input<A>(val value: A)

class Schema<I, O>(
    val inputSerializer: KSerializer<I>,
    val outputSerializer: KSerializer<O>,
) {
    fun inputFromJsonString(value: String): I =
        json.decodeFromString(inputSerializer, value)

    fun inputFromMap(value: Map<String, String>): I {
        val jsonElement = json.encodeToJsonElement<Map<String, String>>(value)
        return json.decodeFromJsonElement(inputSerializer, jsonElement)
    }

    fun inputJsonSchema(): JsonSchema =
        generateSchema(descriptor = inputSerializer.descriptor)

    private fun jsonSchema(serializer: KSerializer<*>): JsonObject {
        val finalSerializer = if (serializerRequiresWrapping(serializer)) Input.serializer(serializer)
        else serializer
        val jsonSchema = generateSchema(descriptor = finalSerializer.descriptor)
        return json.parseToJsonElement(jsonSchema.toString()).jsonObject
    }

    private fun serializerRequiresWrapping(serializer: KSerializer<*>): Boolean =
        when (serializer.descriptor.kind) {
            PolymorphicKind.OPEN,
            PolymorphicKind.SEALED -> false

            PrimitiveKind.BOOLEAN,
            PrimitiveKind.BYTE,
            PrimitiveKind.CHAR,
            PrimitiveKind.DOUBLE,
            PrimitiveKind.FLOAT,
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            PrimitiveKind.SHORT,
            StructureKind.LIST,
            PrimitiveKind.STRING -> true

            SerialKind.CONTEXTUAL,
            SerialKind.ENUM,
            StructureKind.CLASS,
            StructureKind.MAP,
            StructureKind.OBJECT -> false
        }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
            encodeDefaults = true
        }

        inline operator fun <reified I, reified O> invoke(): Schema<I, O> =
            Schema(inputSerializer = serializer<I>(), outputSerializer = serializer<O>())
    }
}
