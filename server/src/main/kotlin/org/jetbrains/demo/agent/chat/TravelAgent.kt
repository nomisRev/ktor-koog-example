package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.ProvideSubgraphResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.lettuce.core.KeyScanArgs.Builder.type
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.AgentError
import org.jetbrains.demo.AgentEvent.AgentStarted
import org.jetbrains.demo.AgentEvent.Step1
import org.jetbrains.demo.AgentEvent.Step2
import org.jetbrains.demo.AgentEvent.Tool
import org.jetbrains.demo.AppConfig
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.agent.chat.strategy.ItineraryIdeas
import org.jetbrains.demo.agent.chat.strategy.ItineraryIdeasProvider
import org.jetbrains.demo.agent.chat.strategy.ProposedTravelPlan
import org.jetbrains.demo.agent.chat.strategy.ProposedTravelPlanProvider
import org.jetbrains.demo.agent.chat.strategy.ResearchedPointOfInterest
import org.jetbrains.demo.agent.chat.strategy.ResearchedPointOfInterestProvider
import org.jetbrains.demo.agent.chat.strategy.planner
import org.jetbrains.demo.agent.koog.ktor.StreamingAIAgent
import org.jetbrains.demo.agent.koog.ktor.sseAgent
import org.jetbrains.demo.agent.koog.ktor.withMaxAgentIterations
import org.jetbrains.demo.agent.koog.ktor.withSystemPrompt
import org.jetbrains.demo.agent.tools.tools
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.concurrent.atomics.updateAndFetch

public fun Route.sse(
    path: String,
    method: HttpMethod = HttpMethod.Get,
    handler: suspend ServerSSESession.() -> Unit
): Route = route(path, method) { sse(handler) }

fun Application.agent(config: AppConfig) {
    val deferredTools = async { tools(config) }

    routing {
        // TODO allow disabling authentication with a project-wide flag both on backend -and frontend.
        authenticate("google", optional = developmentMode) {
            sse("/plan", HttpMethod.Post) {
                val form = call.receive<JourneyForm>()
                val tools = deferredTools.await()
                sseAgent(
                    planner(tools),
                    OpenAIModels.CostOptimized.GPT4oMini,
                    tools.registry() + ToolRegistry {
                        tool(ItineraryIdeasProvider)
                        tool(ResearchedPointOfInterestProvider)
                        tool(ProposedTravelPlanProvider)
                    },
                    configureAgent = {
                        it.withSystemPrompt(prompt("travel-assistant-agent") {
                            system(markdown {
                                +"You're an expert travel assistant helping users reach their destination in a reliable way."
                                header(1, "Task description:")
                                +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
                            })
                        }).withMaxAgentIterations(100)
                    },
                ).run(form)
                    .collect { event: StreamingAIAgent.Event<JourneyForm, ProposedTravelPlan> ->
                        val result = event.toDomainEventOrNull()

                        if (result != null) {
                            application.log.debug("Sending AgentEvent: $result")
                            send(data = Json.encodeToString(AgentEvent.serializer(), result))
                        } else {
                            application.log.debug("Ignoring $event")
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private fun StreamingAIAgent.Event<JourneyForm, ProposedTravelPlan>.toDomainEventOrNull(): AgentEvent? {
    val inputTokens = AtomicInt(0)
    val outputTokens = AtomicInt(0)
    val totalTokens = AtomicInt(0)

    return when (this) {
        is Agent -> when (this) {
            is OnAgentFinished -> AgentEvent.AgentFinished(
                agentId = agentId,
                runId = runId,
                result.toDomain()
            )

            is OnAgentRunError ->
                AgentError(agentId = agentId, runId = runId, throwable.message)

            is OnBeforeAgentStarted -> AgentStarted(context.agentId, context.runId)
        }


        is StreamingAIAgent.Event.Tool if tool is ProvideSubgraphResult -> when (this) {
            is OnToolCallResult if toolArgs is ItineraryIdeas -> Step1(toolArgs.pointsOfInterest)
            is OnToolCallResult if toolArgs is ResearchedPointOfInterest -> Step2(toolArgs.toDomain())
            else -> null
        }

        is StreamingAIAgent.Event.Tool -> Tool(
            id = toolCallId!!,
            name = tool.name,
            type = Tool.Type.fromToolName(tool.name),
            state = when (this) {
                is StreamingAIAgent.Event.OnToolCall -> Tool.State.Running
                is StreamingAIAgent.Event.OnToolCallResult -> Tool.State.Succeeded
                is StreamingAIAgent.Event.OnToolCallFailure,
                is StreamingAIAgent.Event.OnToolValidationError -> Tool.State.Failed
            }
        )

        is StreamingAIAgent.Event.OnAfterLLMCall -> {
            val input = responses.sumOf { it.metaInfo.inputTokensCount ?: 0 }
            val output = responses.sumOf { it.metaInfo.outputTokensCount ?: 0 }
            val total = responses.sumOf { it.metaInfo.totalTokensCount ?: 0 }

            inputTokens.update { it + input }
            outputTokens.update { it + output }
            totalTokens.updateAndFetch { it + total }
            println("Input tokens: ${inputTokens.load()}, output tokens: ${outputTokens.load()}, total tokens: ${totalTokens.load()}")
            AgentEvent.Message(responses.filterIsInstance<Message.Assistant>().map { it.content })
        }

        is OnNodeExecutionError,
        is OnBeforeLLMCall,
        is OnAfterNode,
        is OnBeforeNode,
        is OnStrategyFinished<*, *>,
        is OnStrategyStarted<*, *> -> null
    }
}
