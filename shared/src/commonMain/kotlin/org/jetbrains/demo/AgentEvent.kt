package org.jetbrains.demo

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias SerializableImmutableList<T> = @Serializable(ImmutableListSerializer::class) ImmutableList<T>

class ImmutableListSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<ImmutableList<T>> {
    @OptIn(SealedSerializationApi::class)
    private class PersistentListDescriptor : SerialDescriptor by serialDescriptor<List<String>>() {
        override val serialName: String = "kotlinx.serialization.immutable.ImmutableList"
    }

    override val descriptor: SerialDescriptor = PersistentListDescriptor()
    override fun serialize(encoder: Encoder, value: ImmutableList<T>) =
        ListSerializer(dataSerializer).serialize(encoder, value.toList())

    override fun deserialize(decoder: Decoder): ImmutableList<T> =
        ListSerializer(dataSerializer).deserialize(decoder).toPersistentList()
}

@Serializable
data class Tool(val id: String, val name: String)

@Serializable
sealed interface AgentEvent {
    @Serializable
    data class AgentStarted(val agentId: String, val runId: String) : AgentEvent

    @Serializable
    data class ToolStarted(val ids: SerializableImmutableList<Tool>) : AgentEvent

    @Serializable
    data class ToolFinished(val ids: SerializableImmutableList<Tool>) : AgentEvent

    @Serializable
    data class Message(val message: List<String>) : AgentEvent

    @Serializable
    data class AgentFinished(
        val agentId: String,
        val runId: String,
        val result: String
    ) : AgentEvent
}

@Serializable
data class AgentGraph(
    val started: Boolean,
    val finished: Boolean,
    val result: String?,
    val columns: SerializableImmutableList<AgentColumn>,
) {
    companion object {
        fun empty(): AgentGraph = AgentGraph(
            started = false,
            finished = false,
            result = null,
            columns = persistentListOf(),
        )
    }
}

@Serializable

sealed interface AgentColumn {
    @Serializable
    data class Single(val node: ToolNode) : AgentColumn

    @Serializable
    data class Parallel(val nodes: SerializableImmutableList<ToolNode>) : AgentColumn
}

@Serializable
enum class ToolStatus { Pending, Running, Finished }

@Serializable
data class ToolNode(val tool: Tool, val status: ToolStatus)

/**
 * Transport types supported by the planner.
 */
@Serializable
enum class TransportType {
    Plane, Train, Bus, Car, Boat
}

/**
 * Immutable traveler model.
 */
@Serializable
data class Traveler(
    val id: String,
    val name: String,
    val about: String? = null
)

/**
 * Form model kept in UiState.Success; all immutable for Compose stability.
 */
@Serializable
data class JourneyForm(
    val fromCity: String,
    val toCity: String,
    val transport: TransportType,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val travelers: SerializableImmutableList<Traveler>,
    val details: String?,
)
