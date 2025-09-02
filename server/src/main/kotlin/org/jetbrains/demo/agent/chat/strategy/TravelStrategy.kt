package org.jetbrains.demo.agent.chat.strategy

import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.markdown.markdown
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.PointOfInterest
import org.jetbrains.demo.PointOfInterestFindings
import org.jetbrains.demo.agent.koog.parallel
import org.jetbrains.demo.agent.tools.Tools

private val IMAGE_WIDTH = 400
private val WORD_COUNT = 200

fun planner(tools: Tools) = strategy<JourneyForm, ProposedTravelPlan>("travel-planner") {
    val pointsOfInterest by subgraphWithTask<JourneyForm, ItineraryIdeas>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        finishTool = ItineraryIdeasProvider
    ) { input ->
        markdown {
            header(1, "Task description")
            bulleted {
                item("Find points of interest that are relevant to the travel journey and travelers.")
                item("Use mapping tools to consider appropriate order and put a rough date range for each point of interest.")
            }
            header(2, "Details")
            bulleted {
                item("The travelers are ${input.travelers}.")
                item("Travelling from ${input.fromCity} to ${input.toCity}.")
                item("Leaving on ${input.startDate}, and returning on ${input.endDate}.")
                item("The preferred transportation method is ${input.transport}.")
            }
        }
    }

    val compress by nodeLLMCompressHistory<ItineraryIdeas>(strategy = HistoryCompressionStrategy.WholeHistory)

    val researchPointOfInterest by subgraphWithTask<PointOfInterest, ResearchedPointOfInterest>(
        toolSelectionStrategy = tools.mapsAndWeb(),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        finishTool = ResearchedPointOfInterestProvider
    ) { idea ->
        val form = agentInput<JourneyForm>()
        markdown {
            +"Research the following point of interest."
            +"Consider interesting stories about art and culture and famous people."
            +"Details from the traveler: ${form.travelers}."
            +"Dates to consider: departure from ${form.startDate} to ${form.endDate}."
            +"If any particularly important events are happening here during this time, mention them and list specific dates."
            header(1, "Point of interest to research")
            bulleted {
                item("Name: ${idea.name}")
                item("Location: ${idea.location}")
                item("From ${idea.fromDate} to ${idea.toDate}")
                item("Description: ${idea.description}")
            }
        }
    }

    val researchPoints by parallel(compress, researchPointOfInterest) { it.pointsOfInterest }
    val proposePlan by subgraphWithTask<PointOfInterestFindings, ProposedTravelPlan>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        finishTool = ProposedTravelPlanProvider
    ) { input ->
        val form = agentInput<JourneyForm>()
        // TODO turn this in proper structured data, and render something in the UI.
        """
                Given the following travel brief, create a detailed plan.
                Give it a brief, catchy title that doesn't include dates, but may consider season, mood or relate to travelers's interests.

                Plan the journey to minimize travel time.
                However, consider any important events or places of interest along the way that might inform routing.
                Include total distances.

                ${form.details?.let { "<details>${it}</details>" } ?: ""}
                Consider the weather in your recommendations. Use mapping tools to consider distance of driving or walking.

                Write up in $WORD_COUNT words or less.
                Include links in text where appropriate and in the links field.
                
                The Day field locationAndCountry field should be in the format <location,+Country> e.g. Ghent,+Belgium

                Put image links where appropriate in text and also in the links field.

                Recount at least one interesting story about a famous person associated with an area.
                
                Include natural headings and paragraphs in MARKDOWN format.
                Use unordered lists as appropriate.
                Start any headings at Header 4
                Embed images in text, with max width of ${IMAGE_WIDTH}px.
                Be sure to include informative caption and alt text for each image.

                Consider the following points of interest:
                ${
            input.pointsOfInterest.joinToString("\n") {
                """
                    ${it.pointOfInterest.name}
                    ${it.research}
                    ${it.links.joinToString { link -> "${link.url}: ${link.summary}" }}
                    Images: ${it.imageLinks.joinToString { link -> "${link.url}: ${link.summary}" }}

                """.trimIndent()
            }
        }
            """.trimIndent()
    }

    nodeStart then pointsOfInterest then compress then researchPoints
    edge(researchPoints forwardTo proposePlan transformed { PointOfInterestFindings(it.map(ResearchedPointOfInterest::toDomain)) })
    proposePlan then nodeFinish
}
