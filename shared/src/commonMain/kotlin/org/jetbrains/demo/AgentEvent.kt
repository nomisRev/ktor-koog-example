package org.jetbrains.demo

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlin.jvm.JvmStatic

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

/**
 * KotlinX Serialization supports sealed interfaces auto-of-the-box.
 * By default `@JsonClassDiscriminator` will be "message_type", and the `@SerialName` will be
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("event_type")
@Serializable
sealed interface AgentEvent {
    @SerialName("started")
    @Serializable
    data class AgentStarted(val agentId: String, val runId: String) : AgentEvent

    @SerialName("finished")
    @Serializable
    data class AgentFinished(val agentId: String, val runId: String, val plan: ProposedTravelPlan) : AgentEvent

    @SerialName("error")
    @Serializable
    data class AgentError(val agentId: String, val runId: String, val result: String?) : AgentEvent

    @SerialName("tool")
    @Serializable
    data class Tool(
        val id: String,
        val name: String,
        val type: Type,
    ) : AgentEvent {
        enum class Type {
            Maps, Weather, Search, Other;

            companion object Companion {
                @JvmStatic
                fun fromToolName(name: String): Type = when {
                    name.startsWith("maps") -> Maps
                    name.startsWith("weather") -> Weather
                    name.startsWith("search") -> Search
                    else -> Other
                }
            }
        }
    }

    @SerialName("tool_result")
    @Serializable
    data class ToolFinished(
        val id: String,
        val name: String,
        val success: Boolean
    ) : AgentEvent

    @SerialName("message")
    @Serializable
    data class Message(val message: List<String>) : AgentEvent

    @SerialName("step1")
    @Serializable
    data class Step1(val ideas: List<PointOfInterest>) : AgentEvent

    @SerialName("step2")
    @Serializable
    data class Step2(val researchedPointOfInterest: ResearchedPointOfInterest) : AgentEvent
}

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
