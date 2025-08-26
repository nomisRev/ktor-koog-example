package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.InternetResource
import org.jetbrains.demo.PointOfInterest

@Serializable
data class ResearchedPointOfInterest(
    val pointOfInterest: PointOfInterest,
    val research: String,
    val links: List<InternetResource>,
    @property:LLMDescription("Links to images. Links must be the images themselves, not just links to them.")
    val imageLinks: List<InternetResource>
) : SubgraphResult {
    override fun toStringDefault(): String =
        Json.encodeToString(serializer(), this)

    fun toDomain() =
        org.jetbrains.demo.ResearchedPointOfInterest(pointOfInterest, research, links, imageLinks)
}

val ResearchedPointOfInterestProvider = SubgraphResultProvider<ResearchedPointOfInterest>(
    name = "provide_research_results",
    description = """
            Finish tool to compile final conclusion result of the research done on the points of interest.
            Call to provide the final research conclusion result.
        """.trimIndent(),
)
