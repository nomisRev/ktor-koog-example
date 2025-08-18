package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import org.checkerframework.checker.units.qual.t
import org.jetbrains.demo.ItineraryIdeas
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.PointOfInterest
import org.jetbrains.demo.PointOfInterestFindings
import org.jetbrains.demo.ResearchedPointOfInterest
import org.jetbrains.demo.agent.koog.descriptors
import org.jetbrains.demo.agent.koog.parallel
import org.jetbrains.demo.agent.tools.Tools

fun planner(tools: Tools) = strategy("travel-planner") {
    val pointsOfInterest by pointsOfInterest(tools)
    val researchPointOfInterest by researchPointOfInterest(tools)
    val researchPoints by parallel(pointsOfInterest, researchPointOfInterest) { it.pointsOfInterest }

    edge(nodeStart forwardTo researchPoints)
    edge(researchPoints forwardTo nodeFinish transformed { PointOfInterestFindings(it) })
}

fun pointsOfInterest(tools: Tools) = simpleStructuredInputOutput<JourneyForm, ItineraryIdeas>(
    toolSelectionStrategy = tools.mathWebAndMaps(),
    llmModel = OpenAIModels.Reasoning.GPT4oMini
) { input ->
    llm.requestLmmWithUpdatedPrompt {
        user(markdown {
            header(1, "Task description")
            +"Find points of interest that are relevant to the travel journey and travelers."
            +"Use mapping tools to consider appropriate order and put a rough date range for each point of interest."
            header(2, "Details")
            bulleted {
                item("The travelers are ${input.travelers}.")
                item("Travelling from ${input.fromCity} to ${input.toCity}.")
                item("Leaving on ${input.startDate}, and returning on ${input.endDate}.")
                item("The preferred transportation method is ${input.transport}.")
            }
        })
    }
}

fun researchPointOfInterest(tools: Tools) = simpleStructuredInputOutput<PointOfInterest, ResearchedPointOfInterest>(
    toolSelectionStrategy = tools.web(),
    llmModel = OpenAIModels.Reasoning.GPT4oMini
) { idea ->
    val form = agentInput<JourneyForm>()
    llm.requestLmmWithUpdatedPrompt {
        user(markdown {
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
        })
    }
}

private suspend fun AIAgentLLMContext.requestLmmWithUpdatedPrompt(body: PromptBuilder.() -> Unit): Message.Response =
    writeSession {
        updatePrompt { body() }
        requestLLM()
    }
