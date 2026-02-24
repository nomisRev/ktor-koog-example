package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.*
import org.jetbrains.demo.AgentEvent.Tool
import org.jetbrains.demo.AppConfig
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.PointOfInterest
import org.jetbrains.demo.ProposedTravelPlan
import org.jetbrains.demo.ResearchedPointOfInterest
import org.jetbrains.demo.agent.tools.Tools
import org.jetbrains.demo.user.UserRepository
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.concurrent.atomics.updateAndFetch
import kotlin.time.Clock

interface TravelAgent {
    fun playJourney(form: JourneyForm): Flow<AgentEvent>
    fun resume(agentId: String): Flow<AgentEvent>
}

class KoogTravelAgent(
    private val config: AppConfig,
    private val executor: PromptExecutor,
    private val tools: Deferred<Tools>,
    private val users: UserRepository,
    private val storage: PersistenceStorageProvider<*>
) : TravelAgent {
    override fun resume(agentId: String): Flow<AgentEvent> = channelFlow {
        val checkpoint = storage.getLatestCheckpoint(agentId)
        // restart playJourney where it left off / crashed
        // or with a user-initiated manual feedback message
    }

    override fun playJourney(form: JourneyForm): Flow<AgentEvent> = channelFlow {
        val tools = tools.await()
        AIAgent(
            executor,
            AIAgentConfig(
                prompt("travel-assistant-agent") {
                    system(markdown {
                        "Today's date is ${
                            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                        }."
                        +"You're an expert travel assistant helping users reach their destination in a reliable way."
                        header(1, "Task description:")
                        +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
                    })
                },
                AnthropicModels.Sonnet_4_5,
                300,
            ),
            planner(tools),
            tools.registry() + ToolRegistry { tools(users.asTools()) },
        ) {
            install(OpenTelemetry) {
                addLangfuseExporter(
                    langfuseUrl = config.langfuseUrl,
                    langfusePublicKey = config.langfusePublicKey,
                    langfuseSecretKey = config.langfuseSecretKey
                )
            }
            install(Persistence) {
                storage = this@KoogTravelAgent.storage
                enableAutomaticPersistence = true
            }
        }.run(form)
    }

    context(producer: ProducerScope<AgentEvent>)
    fun GraphAIAgent.FeatureContext.events() {
        @OptIn(ExperimentalAtomicApi::class)
        install(EventHandler) {
            val inputTokens = AtomicInt(0)
            val outputTokens = AtomicInt(0)
            val totalTokens = AtomicInt(0)

            onAgentCompleted {
                producer.send(AgentFinished(it.agentId, it.runId, it.result as ProposedTravelPlan))
            }
            onAgentExecutionFailed { producer.send(AgentError(it.agentId, it.runId, it.throwable.message)) }
            onAgentStarting { producer.send(AgentStarted(it.agent.id, it.runId)) }
            onSubgraphExecutionCompleted {
                when (it.subgraph.name) {
                    "pointsOfInterest" -> Step1(it.output as List<PointOfInterest>)
                    "researchPointOfInterest" -> Step2(it.output as ResearchedPointOfInterest)
                }
            }
            onToolCallStarting {
                producer.send(
                    Tool(
                        it.toolCallId!!,
                        it.toolName,
                        Tool.Type.fromToolName(it.toolName),
                        Tool.State.Running
                    )
                )
            }
            onToolCallFailed {
                producer.send(
                    Tool(
                        it.toolCallId!!,
                        it.toolName,
                        Tool.Type.fromToolName(it.toolName),
                        Tool.State.Failed
                    )
                )
            }
            onToolCallCompleted {
                producer.send(
                    Tool(
                        it.toolCallId!!,
                        it.toolName,
                        Tool.Type.fromToolName(it.toolName),
                        Tool.State.Succeeded
                    )
                )
            }
            onToolValidationFailed {
                producer.send(
                    Tool(
                        it.toolCallId!!,
                        it.toolName,
                        Tool.Type.fromToolName(it.toolName),
                        Tool.State.Failed
                    )
                )
            }
            onLLMCallCompleted {
                val input = it.responses.sumOf { it.metaInfo.inputTokensCount ?: 0 }
                val output = it.responses.sumOf { it.metaInfo.outputTokensCount ?: 0 }
                val total = it.responses.sumOf { it.metaInfo.totalTokensCount ?: 0 }
                println("Input tokens: $input, output tokens: $output, total tokens: $total")

                inputTokens.update { it + input }
                outputTokens.update { it + output }
                totalTokens.updateAndFetch { it + total }

                val responses = it.responses.filterIsInstance<Message.Assistant>().map { it.content }
                if (responses.isNotEmpty()) producer.send(Message(responses))
            }
        }
    }
}
