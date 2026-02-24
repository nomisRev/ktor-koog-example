package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.agents.snapshot.feature.persistence
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.xml.xml
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.PointOfInterest
import org.jetbrains.demo.PointOfInterestFindings
import org.jetbrains.demo.ProposedTravelPlan
import org.jetbrains.demo.ResearchedPointOfInterest
import org.jetbrains.demo.agent.PostgresPersistenceStorageProvider
import org.jetbrains.demo.agent.tools.Tools

private val IMAGE_WIDTH = 400
private val WORD_COUNT = 200


fun test(tools: Tools) = strategy<JourneyForm, ProposedTravelPlan>("travel-planner") {
    val pointsOfInterest by subgraphWithTask<JourneyForm, List<PointOfInterest>>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = AnthropicModels.Opus_4_5,
    ) { "points of interest prompt" }

    val researchPointOfInterest by subgraphWithTask<List<PointOfInterest>, ResearchedPointOfInterest>(
        toolSelectionStrategy = tools.mapsAndWeb(),
        llmModel = AnthropicModels.Sonnet_4_5,
    ) { "research point of interest prompt" }

    val proposePlan by subgraphWithTask<ResearchedPointOfInterest, ProposedTravelPlan>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = OpenAIModels.Chat.GPT5_2,
    ) { "Our proposal plan" }

    val review by subgraphWithVerification<ProposedTravelPlan>(
        toolSelectionStrategy = ToolSelectionStrategy.NONE,
        llmModel = OpenAIModels.Chat.GPT5_2,
        llmParams = OpenAIChatParams(
            temperature = 0.8,
            reasoningEffort = ReasoningEffort.HIGH
        )
    ) { "Review the proposed travel plan" }

    val fixPlan by subgraphWithTask<String, ProposedTravelPlan>(
        llmModel = OpenAIModels.Chat.GPT5Codex
    ) { feedback ->
        "You must fix the following problems: $feedback"
    }

    nodeStart then pointsOfInterest then researchPointOfInterest then proposePlan then review
    edge(review forwardTo nodeFinish onCondition { it.successful } transformed { it.input })
    edge(review forwardTo fixPlan onCondition { !it.successful } transformed { it.feedback })
}

fun planner(tools: Tools) = strategy<JourneyForm, ProposedTravelPlan>("travel-planner") {
    val pointsOfInterest by subgraphWithTask<JourneyForm, List<PointOfInterest>>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = AnthropicModels.Sonnet_4,
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

    val researchPointOfInterest by subgraphWithTask<PointOfInterest, ResearchedPointOfInterest>(
        toolSelectionStrategy = tools.mapsAndWeb(),
        llmModel = AnthropicModels.Sonnet_4_5,
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

    val compres by nodeLLMCompressHistory<List<PointOfInterest>>(
        strategy = object : HistoryCompressionStrategy() {
            override suspend fun compress(
                llmSession: AIAgentLLMWriteSession,
                memoryMessages: List<Message>
            ) {
                TODO()
            }
        }
    )

    val customCompress by node<List<PointOfInterest>, List<PointOfInterest>> { pointsOfInterest ->
        val form = agentInput<JourneyForm>()
        llm.writeSession {
            val existingHistory = prompt
            prompt = prompt("replace") {
                TODO("Construct entire new history from scratch")
            }
        }
        pointsOfInterest
    }

    val researchPoints by node<List<PointOfInterest>, List<ResearchedPointOfInterest>> { pois ->
        supervisorScope {
            pois.map {
                async { (researchPointOfInterest.execute(this@node, it) as ResearchedPointOfInterest) }
            }.awaitAll()
        }
    }

    val proposePlan by subgraphWithTask<PointOfInterestFindings, ProposedTravelPlan>(
        toolSelectionStrategy = tools.mapsAndWeather(),
        llmModel = OpenAIModels.Chat.GPT5_2,
    ) { input ->
        val form = agentInput<JourneyForm>()
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

    nodeStart then pointsOfInterest then researchPoints
    edge(researchPoints forwardTo proposePlan transformed { PointOfInterestFindings(it) })
    proposePlan then nodeFinish
}
