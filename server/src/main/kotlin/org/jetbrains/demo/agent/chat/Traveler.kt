package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.Traveler

@Serializable
data class Travelers(val travelers: List<Traveler>) {
    fun contribution(): String =
        if (travelers.isEmpty()) "No information could be found about travelers"
        else "${travelers.size} travelers:\n" + travelers.joinToString(separator = "\n") {
            "${it.name}: ${it.about}"
        }
}

@Serializable
data class PointOfInterest(
    val name: String,
    val description: String,
    val location: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
)

@Serializable
data class PointOfInterestFindings(
    val pointsOfInterest: List<ResearchedPointOfInterest>,
)

@Serializable
data class Day(
    val date: LocalDate,
    @LLMDescription("Location where the traveler will stay on this day in Google Maps friendly format 'City,+Country'")
    val locationAndCountry: String,
) {
    /**
     * More readable location name, e.g. "Paris" rather than "Paris,+FR".
     */
    val stayingAt: String = locationAndCountry.split(",").firstOrNull()?.trim() ?: "Unknown location"
}

@Serializable
data class InternetResource(
    val url: String,
    val summary: String,
)

@Serializable
data class Stay(
    val days: List<Day>,
    val airbnbUrl: String? = null,
) {

    fun stayingAt(): String {
        return days.firstOrNull()?.stayingAt ?: "Unknown location"
    }

    fun locationAndCountry(): String {
        return days.firstOrNull()?.locationAndCountry ?: "Unknown location"
    }
}

@Serializable
/**
 * Note created by an LLM but assembled in code.
 */
data class TravelPlan(
    val brief: JourneyForm,
    val proposal: ProposedTravelPlan,
    val stays: List<Stay>,
    val travelers: Travelers,
) {

    /**
     * Google Maps link for the whole journey. Computed from days.
     * Even good LLMs seem to get map links wrong, so we compute it here.
     */
    val journeyMapUrl: String
        get() {
            TODO()
        }

    val content: String
        get() = """
            ${proposal.title}
            ${proposal.plan}
            Days: ${proposal.days.joinToString(separator = "\n") { "${it.date} - ${it.stayingAt}" }}
            Map:
            $journeyMapUrl
            Pages:
            ${proposal.pageLinks.joinToString("\n") { "${it.url} - ${it.summary}" }}
            Images:
            ${proposal.imageLinks.joinToString("\n") { "${it.url} - ${it.summary}" }}
        """.trimIndent()
}
