package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.demo.PointOfInterest

@Serializable
data class Test(val example: String) {
}

@Serializable
data class ItineraryIdeas(val pointsOfInterest: List<PointOfInterest>) : SubgraphResult {
    override fun toStringDefault(): String =
        Json.encodeToString(serializer(), this)
}

object ItineraryIdeasProvider : ProvideSubgraphResult<ItineraryIdeas>() {
    override val argsSerializer = ItineraryIdeas.serializer()

    override val descriptor = ToolDescriptor(
        name = "provide_itinerary_ideas",
        description = """
            Finish tool to compile final suggestion for the user's itinerary.
            Call to provide the final conclusion suggestion itinerary ideas result.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "pointsOfInterest",
                description = "The points of interest in the suggested itinerary",
                type = ToolParameterType.List(PointOfInterestType())
            )
        ),
    )

    override suspend fun execute(args: ItineraryIdeas): ItineraryIdeas = args
}

fun PointOfInterestType(): ToolParameterType.Object = ToolParameterType.Object(
    properties = listOf(
        ToolParameterDescriptor(
            name = "name",
            description = "The name of the point of interest",
            type = ToolParameterType.String
        ),
        ToolParameterDescriptor(
            name = "description",
            description = "Description of the point of interest",
            type = ToolParameterType.String
        ),
        ToolParameterDescriptor(
            name = "location",
            description = "The location of the point of interest",
            type = ToolParameterType.String
        ),
        ToolParameterDescriptor(
            name = "fromDate",
            description = "Start date for visiting this point of interest in the ISO format, e.g. 2022-01-01",
            type = ToolParameterType.String
        ),
        ToolParameterDescriptor(
            name = "toDate",
            description = "End date for visiting this point of interest in the ISO format, e.g. 2022-01-01",
            type = ToolParameterType.String
        )
    ),
    requiredProperties = listOf("name", "description", "location", "fromDate", "toDate")
)
