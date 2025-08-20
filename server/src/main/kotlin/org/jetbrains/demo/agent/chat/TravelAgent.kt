package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
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
import io.ktor.util.reflect.instanceOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.*
import org.jetbrains.demo.AppConfig
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.agent.chat.PointOfInterestFindings
import org.jetbrains.demo.agent.koog.ktor.StreamingAIAgent
import org.jetbrains.demo.agent.koog.ktor.sseAgent
import org.jetbrains.demo.agent.koog.ktor.withMaxAgentIterations
import org.jetbrains.demo.agent.koog.ktor.withSystemPrompt
import org.jetbrains.demo.agent.tools.tools

public fun Route.sse(
    path: String,
    method: HttpMethod = HttpMethod.Get,
    handler: suspend ServerSSESession.() -> Unit
): Route = route(path, method) { sse(handler) }

fun Application.agent(config: AppConfig) {
    val deferredTools = async { tools(config) }

    routing {
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
                    installFeatures = {
                        install(Tracing) {
                            addMessageProcessor(
                                TraceFeatureMessageFileWriter(
                                    Path("agent-traces2.log"),
                                    sinkOpener = { path -> SystemFileSystem.sink(path).buffered() }
                                ))
                        }
                    }
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

private fun StreamingAIAgent.Event<JourneyForm, ProposedTravelPlan>.toDomainEventOrNull(): AgentEvent? {
    var inputTokens = 0
    var outputTokens = 0
    var totalTokens = 0

    return when (val value = this) {
        is StreamingAIAgent.Event.Agent -> when (value) {
            is StreamingAIAgent.Event.OnAgentBeforeClose -> null
            is StreamingAIAgent.Event.OnAgentFinished<ProposedTravelPlan> -> AgentFinished(
                agentId = value.agentId,
                runId = value.runId,
                result = value.result.toString()
            )

            is StreamingAIAgent.Event.OnAgentRunError ->
                AgentFinished(
                    agentId = value.agentId,
                    runId = value.runId,
                    result = value.throwable.message ?: "Unknown error"
                )

            is StreamingAIAgent.Event.OnBeforeAgentStarted<JourneyForm, ProposedTravelPlan> -> AgentStarted(
                value.context.agentId,
                value.context.runId
            )
        }

        is StreamingAIAgent.Event.Tool -> when (value) {
            is StreamingAIAgent.Event.OnToolCall ->
                ToolStarted(value.toolCallId!!, value.tool.name)

            is StreamingAIAgent.Event.OnToolCallResult ->
                when {
                    value.toolArgs is ItineraryIdeas -> Message(listOf(this.result.toString()))
                    value.toolArgs is ResearchedPointOfInterest -> Message(listOf(this.result.toString()))
                    value.toolArgs is ProposedTravelPlan -> Message(listOf(this.result.toString()))
                    else -> ToolFinished(value.toolCallId!!, value.tool.name)
                }

            is StreamingAIAgent.Event.OnToolCallFailure ->
                ToolFinished(value.toolCallId!!, value.tool.name)

            is StreamingAIAgent.Event.OnToolValidationError ->
                ToolFinished(value.toolCallId!!, value.tool.name)
        }

        is StreamingAIAgent.Event.OnAfterLLMCall -> {
            inputTokens += value.responses.sumOf { it.metaInfo.inputTokensCount ?: 0 }
            outputTokens += value.responses.sumOf { it.metaInfo.outputTokensCount ?: 0 }
            totalTokens += value.responses.sumOf { it.metaInfo.totalTokensCount ?: 0 }
            println("Input tokens: $inputTokens, output tokens: $outputTokens, total tokens: $totalTokens")
            Message(value.responses.filterIsInstance<Message.Assistant>().map { it.content })
        }

        is StreamingAIAgent.Event.OnBeforeLLMCall,
        is StreamingAIAgent.Event.OnAfterNode,
        is StreamingAIAgent.Event.OnBeforeNode,
        is StreamingAIAgent.Event.OnNodeExecutionError,
        is StreamingAIAgent.Event.OnStrategyFinished<*, *>,
        is StreamingAIAgent.Event.OnStrategyStarted<*, *> -> null
    }
}
