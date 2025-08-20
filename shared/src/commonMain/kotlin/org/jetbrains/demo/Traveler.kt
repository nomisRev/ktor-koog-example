package org.jetbrains.demo

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

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
data class ResearchedPointOfInterest(
    val pointOfInterest: PointOfInterest,
    val research: String,
    val links: List<InternetResource>,
    @LLMDescription("Links to images. Links must be the images themselves, not just links to them.")
    val imageLinks: List<InternetResource>,
)

@Serializable
data class PointOfInterestFindings(val pointsOfInterest: List<ResearchedPointOfInterest>, )

@Serializable
data class Day(
    val date: kotlinx.datetime.LocalDate,
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
data class ProposedTravelPlan(
    @LLMDescription("Catchy title appropriate to the travelers and travel brief")
    val title: String,
    @LLMDescription("Detailed travel plan")
    val plan: String,
    @LLMDescription("List of days in the travel plan")
    val days: List<Day>,
    @LLMDescription("Links to images")
    val imageLinks: List<InternetResource>,
    @LLMDescription("Links to pages with more information about the travel plan")
    val pageLinks: List<InternetResource>,
    @LLMDescription("List of country names that the travelers will visit")
    val countriesVisited: List<String>,
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
