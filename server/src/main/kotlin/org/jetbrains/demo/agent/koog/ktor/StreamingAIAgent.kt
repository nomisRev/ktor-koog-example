package org.jetbrains.demo.agent.koog.ktor

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.ktor.Koog
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.ktor.server.application.pluginOrNull
import io.ktor.server.sse.ServerSSESession
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toDeprecatedClock
import java.lang.IllegalStateException
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun <Input, Output> ServerSSESession.sseAgent(
    inputType: KType,
    outputType: KType,
    strategy: AIAgentStrategy<Input, Output>,
    model: LLModel,
    tools: ToolRegistry = ToolRegistry.EMPTY,
    clock: Clock = Clock.System,
    // TODO We need to create a proper `AgentConfig` builder inside of Ktor that allows overriding global configuration.
    configureAgent: (AIAgentConfig) -> (AIAgentConfig) = { it },
    installFeatures: FeatureContext.() -> Unit = {}
): StreamingAIAgent<Input, Output> {
    val plugin = requireNotNull(call.application.pluginOrNull(Koog)) { "Plugin $Koog is not configured" }

    @Suppress("invisible_reference", "invisible_member")
    return StreamingAIAgent(
        inputType = inputType,
        outputType = outputType,
        promptExecutor = plugin.promptExecutor,
        strategy = strategy,
        agentConfig = plugin.agentConfig(model).let(configureAgent),
        toolRegistry = plugin.agentConfig.toolRegistry + tools,
        clock = clock,
        installFeatures = installFeatures
    )
}

fun AIAgentConfig.withSystemPrompt(prompt: Prompt): AIAgentConfig =
    AIAgentConfig(prompt, model, maxAgentIterations, missingToolsConversionStrategy)


fun AIAgentConfig.withMaxAgentIterations(maxAgentIterations: Int): AIAgentConfig =
    AIAgentConfig(prompt, model, maxAgentIterations, missingToolsConversionStrategy)

suspend inline fun <reified Input, reified Output> ServerSSESession.sseAgent(
    strategy: AIAgentStrategy<Input, Output>,
    model: LLModel,
    tools: ToolRegistry = ToolRegistry.EMPTY,
    clock: Clock = Clock.System,
    noinline configureAgent: (AIAgentConfig) -> (AIAgentConfig) = { it },
    noinline installFeatures: FeatureContext.() -> Unit = {}
): StreamingAIAgent<Input, Output> = sseAgent(
    typeOf<Input>(),
    typeOf<Output>(),
    strategy,
    model,
    tools,
    clock,
    configureAgent,
    installFeatures
)

@OptIn(ExperimentalUuidApi::class)
class StreamingAIAgent<Input, Output>(
    inputType: KType,
    outputType: KType,
    promptExecutor: PromptExecutor,
    strategy: AIAgentStrategy<Input, Output>,
    agentConfig: AIAgentConfigBase,
    override val id: String = Uuid.random().toString(),
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    clock: Clock = Clock.System,
    installFeatures: FeatureContext.() -> Unit = {},
) : AIAgentBase<Input, Flow<StreamingAIAgent.Event<Input, Output>>> {

    sealed interface Event<out Input, out Output> {
        val runId: String

        sealed interface Agent<Input, Output> : Event<Input, Output> {
            val agentId: String
        }

        data class OnBeforeAgentStarted<Input, Output>(
            val agent: AIAgent<Input, Output>,
            override val runId: String,
            val strategy: AIAgentStrategy<Input, Output>,
            val feature: EventHandler,
            val context: AIAgentContextBase
        ) : Agent<Input, Output> {
            override val agentId: String
                get() = agent.id
        }

        data class OnAgentFinished<Output>(
            override val agentId: String,
            override val runId: String,
            val result: Output,
            val resultType: KType,
        ) : Agent<Nothing, Output>

        data class OnAgentRunError(
            override val agentId: String,
            override val runId: String,
            val throwable: Throwable
        ) : Agent<Nothing, Nothing>

        sealed interface Strategy<Input, Output> : Event<Input, Output> {
            val strategy: AIAgentStrategy<Input, Output>
            val feature: EventHandler
        }

        data class OnStrategyStarted<Input, Output>(
            override val runId: String,
            override val strategy: AIAgentStrategy<Input, Output>,
            override val feature: EventHandler
        ) : Strategy<Input, Output>

        data class OnStrategyFinished<Input, Output>(
            override val runId: String,
            override val strategy: AIAgentStrategy<Input, Output>,
            override val feature: EventHandler,
            val result: Output,
            val resultType: KType,
        ) : Strategy<Input, Output>

        sealed interface Node : Event<Nothing, Nothing> {
            val node: AIAgentNodeBase<*, *>
            val context: AIAgentContextBase
        }

        data class OnBeforeNode(
            override val node: AIAgentNodeBase<*, *>,
            override val context: AIAgentContextBase,
            val input: Any?,
            val inputType: KType,
        ) : Node {
            override val runId: String
                get() = context.runId
        }

        data class OnAfterNode(
            override val node: AIAgentNodeBase<*, *>,
            override val context: AIAgentContextBase,
            val input: Any?,
            val output: Any?,
            val inputType: KType,
            val outputType: KType,
        ) : Node {
            override val runId: String
                get() = context.runId
        }

        data class OnNodeExecutionError(
            override val node: AIAgentNodeBase<*, *>,
            override val context: AIAgentContextBase,
            val throwable: Throwable
        ) : Node {
            override val runId: String
                get() = context.runId
        }

        sealed interface LLM : Event<Nothing, Nothing> {
            val prompt: Prompt
            val model: LLModel
            val tools: List<ToolDescriptor>
        }

        data class OnBeforeLLMCall(
            override val runId: String,
            override val prompt: Prompt,
            override val model: LLModel,
            override val tools: List<ToolDescriptor>,
        ) : LLM

        data class OnAfterLLMCall(
            override val runId: String,
            override val prompt: Prompt,
            override val model: LLModel,
            override val tools: List<ToolDescriptor>,
            val responses: List<Message.Response>,
            val moderationResponse: ModerationResult?
        ) : LLM

        sealed interface Tool : Event<Nothing, Nothing> {
            val toolCallId: String?
            val toolArgs: ToolArgs
            val tool: ai.koog.agents.core.tools.Tool<*, *>
        }

        data class OnToolCall(
            override val runId: String,
            override val toolCallId: String?,
            override val tool: ai.koog.agents.core.tools.Tool<*, *>,
            override val toolArgs: ToolArgs
        ) : Tool

        data class OnToolValidationError(
            override val runId: String,
            override val toolCallId: String?,
            override val tool: ai.koog.agents.core.tools.Tool<*, *>,
            override val toolArgs: ToolArgs,
            val error: String
        ) : Tool

        data class OnToolCallFailure(
            override val runId: String,
            override val toolCallId: String?,
            override val tool: ai.koog.agents.core.tools.Tool<*, *>,
            override val toolArgs: ToolArgs,
            val throwable: Throwable
        ) : Tool

        data class OnToolCallResult(
            override val runId: String,
            override val toolCallId: String?,
            override val tool: ai.koog.agents.core.tools.Tool<*, *>,
            override val toolArgs: ToolArgs,
            val result: ToolResult?
        ) : Tool
    }

    private var channel: ProducerScope<Event<Input, Output>>? = null
    private var isRunning = false
    private val runningMutex = Mutex()

    private suspend fun send(agent: Event<Input, Output>) =
        requireNotNull(channel) { "Race condition detected: SSEAgent2 is not running anymore" }
            .send(agent)

    private val agent = AIAgent(
        inputType = inputType,
        outputType = outputType,
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = agentConfig,
        id = id,
        toolRegistry = toolRegistry,
        clock = clock.toDeprecatedClock()
    ) {
        installFeatures()
        @Suppress("UNCHECKED_CAST")
        install(EventHandler) {
            onBeforeAgentStarted { ctx ->
                send(
                    Event.OnBeforeAgentStarted<Input, Output>(
                        ctx.agent as AIAgent<Input, Output>,
                        ctx.runId,
                        ctx.strategy as AIAgentStrategy<Input, Output>,
                        ctx.feature as EventHandler,
                        ctx.context
                    )
                )
            }
            onAgentFinished { ctx ->
                send(
                    Event.OnAgentFinished(
                        ctx.agentId,
                        ctx.runId,
                        ctx.result as Output,
                        ctx.resultType
                    )
                )
            }
            onAgentRunError { ctx -> send(Event.OnAgentRunError(ctx.agentId, ctx.runId, ctx.throwable)) }

            onStrategyStarted { ctx ->
                send(
                    Event.OnStrategyStarted(
                        ctx.runId,
                        ctx.strategy as AIAgentStrategy<Input, Output>,
                        ctx.feature
                    )
                )
            }
            onStrategyFinished { ctx ->
                send(
                    Event.OnStrategyFinished(
                        ctx.runId,
                        ctx.strategy as AIAgentStrategy<Input, Output>,
                        ctx.feature,
                        ctx.result as Output,
                        ctx.resultType
                    )
                )
            }

            onBeforeNode { ctx -> send(Event.OnBeforeNode(ctx.node, ctx.context, ctx.input, ctx.inputType)) }
            onAfterNode { ctx ->
                send(
                    Event.OnAfterNode(
                        ctx.node,
                        ctx.context,
                        ctx.input,
                        ctx.output,
                        ctx.inputType,
                        ctx.outputType
                    )
                )
            }
            onNodeExecutionError { ctx -> send(Event.OnNodeExecutionError(ctx.node, ctx.context, ctx.throwable)) }

            onBeforeLLMCall { ctx -> send(Event.OnBeforeLLMCall(ctx.runId, ctx.prompt, ctx.model, ctx.tools)) }
            onAfterLLMCall { ctx ->
                send(
                    Event.OnAfterLLMCall(
                        ctx.runId,
                        ctx.prompt,
                        ctx.model,
                        ctx.tools,
                        ctx.responses,
                        ctx.moderationResponse
                    )
                )
            }

            onToolCall { ctx -> send(Event.OnToolCall(ctx.runId, ctx.toolCallId, ctx.tool, ctx.toolArgs)) }
            onToolValidationError { ctx ->
                send(
                    Event.OnToolValidationError(
                        ctx.runId,
                        ctx.toolCallId,
                        ctx.tool,
                        ctx.toolArgs,
                        ctx.error
                    )
                )
            }
            onToolCallFailure { ctx ->
                send(
                    Event.OnToolCallFailure(
                        ctx.runId,
                        ctx.toolCallId,
                        ctx.tool,
                        ctx.toolArgs,
                        ctx.throwable
                    )
                )
            }
            onToolCallResult { ctx ->
                send(
                    Event.OnToolCallResult(
                        ctx.runId,
                        ctx.toolCallId,
                        ctx.tool,
                        ctx.toolArgs,
                        ctx.result
                    )
                )
            }
        }
    }

    override suspend fun run(agentInput: Input): Flow<Event<Input, Output>> =
        channelFlow<Event<Input, Output>> {
            runningMutex.withLock {
                if (isRunning) {
                    throw IllegalStateException("Agent is already running")
                }

                isRunning = true
            }
            this@StreamingAIAgent.channel = this
            agent.run(agentInput)
            this@StreamingAIAgent.channel = null
            runningMutex.withLock { isRunning = false }
        }
}
