package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.ProvideSubgraphResult
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
    @LLMDescription("Links to images. Links must be the images themselves, not just links to them.")
    val imageLinks: List<InternetResource>
) : SubgraphResult {
    override fun toStringDefault(): String =
        Json.encodeToString(serializer(), this)

    fun toDomain() =
        org.jetbrains.demo.ResearchedPointOfInterest(pointOfInterest, research, links, imageLinks)
}

object ResearchedPointOfInterestProvider : ProvideSubgraphResult<ResearchedPointOfInterest>() {
    override val argsSerializer = ResearchedPointOfInterest.serializer()

    override val descriptor = ToolDescriptor(
        name = "research_point_of_interest",
        description = """
            Finish tool to compile final conclusion result of the research done on the points of interest.
            Call to provide the final research conclusion result.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "pointOfInterest",
                description = "The points of interest in the suggested itinerary",
                type = PointOfInterestType()
            ),
            ToolParameterDescriptor(
                name = "research",
                description = "Research information about the point of interest",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "links",
                description = "Links to internet resources about the point of interest",
                type = ToolParameterType.List(InternetResource())
            ),
            ToolParameterDescriptor(
                name = "imageLinks",
                description = "Links to image resources about the point of interest",
                type = ToolParameterType.List(InternetResource())
            )
        ),
    )

    override suspend fun execute(args: ResearchedPointOfInterest): ResearchedPointOfInterest = args
}

fun InternetResource(): ToolParameterType.Object = ToolParameterType.Object(
    properties = listOf(
        ToolParameterDescriptor(
            name = "url",
            description = "URL of the internet resource",
            type = ToolParameterType.String
        ),
        ToolParameterDescriptor(
            name = "summary",
            description = "Summary of the internet resource",
            type = ToolParameterType.String
        )
    )
)

