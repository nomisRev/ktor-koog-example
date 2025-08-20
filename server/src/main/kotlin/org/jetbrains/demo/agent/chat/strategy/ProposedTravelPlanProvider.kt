package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.agents.ext.agent.SubgraphResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.demo.Day
import org.jetbrains.demo.InternetResource

@Serializable
data class ProposedTravelPlan(
    @LLMDescription("Catchy title appropriate to the travelers and input form")
    val title: String,
    @LLMDescription("Detailed travel plan in markdown format")
    val plan: String,
    @LLMDescription("List of days in the travel plan")
    val days: List<Day>,
    @LLMDescription("Links to images")
    val imageLinks: List<InternetResource>,
    @LLMDescription("Links to pages with more information about the travel plan")
    val pageLinks: List<InternetResource>,
    @LLMDescription("List of country names that the travelers will visit")
    val countriesVisited: List<String>,
) : SubgraphResult {
    override fun toStringDefault(): String =
        Json.encodeToString(serializer(), this)

    fun toDomain() =
        org.jetbrains.demo.ProposedTravelPlan(title, plan, days, imageLinks, pageLinks, countriesVisited)
}

object ProposedTravelPlanProvider : ProvideSubgraphResult<ProposedTravelPlan>() {
    override val argsSerializer: KSerializer<ProposedTravelPlan> = ProposedTravelPlan.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "proposed_travel_plan",
        description = """
            Finish tool to compile the proposal travel plan.
            Call to provide the proposal travel plan result.
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "title",
                description = "Catchy title appropriate to the travelers and travel brief",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "plan",
                description = "Detailed travel plan in markdown format",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "days",
                description = "List of days in the travel plan",
                type = ToolParameterType.List(
                    ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "date",
                                description = "Date in the format YYYY-MM-DD",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "locationAndCountry",
                                description = "Location where the traveler will stay on this day in Google Maps friendly format 'City,+Country'",
                                type = ToolParameterType.String
                            )
                        )
                    )
                )
            ),
            ToolParameterDescriptor(
                name = "imageLinks",
                description = "Links to image resources about the travel plan",
                type = ToolParameterType.List(InternetResource())
            ),
            ToolParameterDescriptor(
                name = "pageLinks",
                description = "Links to internet resources about the travel plan",
                type = ToolParameterType.List(InternetResource())
            ),
            ToolParameterDescriptor(
                name = "countriesVisited",
                description = "List of country names that the travelers will visit",
                type = ToolParameterType.List(ToolParameterType.String)
            ),
        ),
    )

    override suspend fun execute(args: ProposedTravelPlan): ProposedTravelPlan = args
}