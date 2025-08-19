package org.jetbrains.demo.agent.chat

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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.*
import org.jetbrains.demo.AppConfig
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.PointOfInterestFindings
import org.jetbrains.demo.agent.koog.ktor.StreamingAIAgent
import org.jetbrains.demo.agent.koog.ktor.sseAgent
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
                    tools.registry(),
                    configureAgent = {
                        it.withSystemPrompt(prompt("travel-assistant-agent") {
                            system(markdown {
                                +"You're an expert travel assistant helping users reach their destination in a reliable way."
                                header(1, "Task description:")
                                +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
                            })
                        })
                    },
                    installFeatures = {
                        install(Tracing) {
                            addMessageProcessor(
                                TraceFeatureMessageFileWriter<Path>(
                                Path("agent-traces.log"),
                                sinkOpener = { path -> SystemFileSystem.sink(path).buffered() }
                            ))
                        }
                    }
                ).run(form)
                    .collect { event: StreamingAIAgent.Event<JourneyForm, PointOfInterestFindings> ->
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

private fun StreamingAIAgent.Event<JourneyForm, PointOfInterestFindings>.toDomainEventOrNull(): AgentEvent? {
    var inputTokens = 0
    var outputTokens = 0
    var totalTokens = 0

    return when (this) {
        is StreamingAIAgent.Event.Agent -> when (this) {
            is StreamingAIAgent.Event.OnAgentBeforeClose -> null
            is StreamingAIAgent.Event.OnAgentFinished<PointOfInterestFindings> -> AgentFinished(
                agentId = this.agentId,
                runId = this.runId,
                result = this.result.toString()
            )

            is StreamingAIAgent.Event.OnAgentRunError ->
                AgentFinished(
                    agentId = this.agentId,
                    runId = this.runId,
                    result = this.throwable.message ?: "Unknown error"
                )

            is StreamingAIAgent.Event.OnBeforeAgentStarted<JourneyForm, PointOfInterestFindings> -> AgentStarted(
                this.context.agentId,
                this.context.runId
            )
        }

        is StreamingAIAgent.Event.Tool -> when (this) {
            is StreamingAIAgent.Event.OnToolCall ->
                ToolStarted(this.toolCallId!!, this.tool.name)

            is StreamingAIAgent.Event.OnToolCallResult ->
                ToolFinished(this.toolCallId!!, this.tool.name)

            is StreamingAIAgent.Event.OnToolCallFailure ->
                ToolFinished(this.toolCallId!!, this.tool.name)

            is StreamingAIAgent.Event.OnToolValidationError ->
                ToolFinished(this.toolCallId!!, this.tool.name)
        }

        is StreamingAIAgent.Event.OnAfterLLMCall -> {
            inputTokens += this.responses.sumOf { it.metaInfo.inputTokensCount ?: 0 }
            outputTokens += this.responses.sumOf { it.metaInfo.outputTokensCount ?: 0 }
            totalTokens += this.responses.sumOf { it.metaInfo.totalTokensCount ?: 0 }
            println("Input tokens: $inputTokens, output tokens: $outputTokens, total tokens: $totalTokens")
            Message(this.responses.filterIsInstance<Message.Assistant>().map { it.content })
        }

        is StreamingAIAgent.Event.OnBeforeLLMCall,
        is StreamingAIAgent.Event.OnAfterNode,
        is StreamingAIAgent.Event.OnBeforeNode,
        is StreamingAIAgent.Event.OnNodeExecutionError,
        is StreamingAIAgent.Event.OnStrategyFinished<*, *>,
        is StreamingAIAgent.Event.OnStrategyStarted<*, *> -> null
    }
}
