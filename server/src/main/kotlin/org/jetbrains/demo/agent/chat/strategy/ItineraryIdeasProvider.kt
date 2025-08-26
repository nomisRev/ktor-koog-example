package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.PointOfInterest

@Serializable
data class ItineraryIdeas(val pointsOfInterest: List<PointOfInterest>) : SubgraphResult {
    override fun toStringDefault(): String =
        Json.encodeToString(serializer(), this)
}

val ItineraryIdeasProvider = SubgraphResultProvider<ItineraryIdeas>(
    name = "provide_itinerary_ideas",
    description = """
            Finish tool to compile final suggestion for the user's itinerary.
            Call to provide the final conclusion suggestion itinerary ideas result.
        """.trimIndent(),
)
