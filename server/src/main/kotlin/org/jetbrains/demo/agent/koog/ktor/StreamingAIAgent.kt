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
        sealed interface Agent<Input, Output> : Event<Input, Output>

        data class OnBeforeAgentStarted<Input, Output>(
            val agent: AIAgent<Input, Output>,
            val runId: String,
            val strategy: AIAgentStrategy<Input, Output>,
            val feature: EventHandler,
            val context: AIAgentContextBase
        ) : Agent<Input, Output>

        data class OnAgentFinished<Output>(
            val agentId: String,
            val runId: String,
            val result: Output,
            val resultType: KType,
        ) : Agent<Nothing, Output>

        data class OnAgentRunError(
            val agentId: String,
            val runId: String,
            val throwable: Throwable
        ) : Agent<Nothing, Nothing>

        @JvmInline
        value class OnAgentBeforeClose(val agentId: String) : Agent<Nothing, Nothing>


        sealed interface Strategy<Input, Output> : Event<Input, Output>

        data class OnStrategyStarted<Input, Output>(
            val runId: String,
            val strategy: AIAgentStrategy<Input, Output>,
            val feature: EventHandler
        ) : Strategy<Input, Output>

        data class OnStrategyFinished<Input, Output>(
            val runId: String,
            val strategy: AIAgentStrategy<Input, Output>,
            val feature: EventHandler,
            val result: Output,
            val resultType: KType,
        ) : Strategy<Input, Output>

        sealed interface Node : Event<Nothing, Nothing>

        data class OnBeforeNode(
            val node: AIAgentNodeBase<*, *>,
            val context: AIAgentContextBase,
            val input: Any?,
            val inputType: KType,
        ) : Node

        data class OnAfterNode(
            val node: AIAgentNodeBase<*, *>,
            val context: AIAgentContextBase,
            val input: Any?,
            val output: Any?,
            val inputType: KType,
            val outputType: KType,
        ) : Node

        data class OnNodeExecutionError(
            val node: AIAgentNodeBase<*, *>,
            val context: AIAgentContextBase,
            val throwable: Throwable
        ) : Node

        sealed interface LLM : Event<Nothing, Nothing>

        data class OnBeforeLLMCall(
            val runId: String,
            val prompt: Prompt,
            val model: LLModel,
            val tools: List<ToolDescriptor>,
        ) : LLM

        data class OnAfterLLMCall(
            val runId: String,
            val prompt: Prompt,
            val model: LLModel,
            val tools: List<ToolDescriptor>,
            val responses: List<Message.Response>,
            val moderationResponse: ModerationResult?
        ) : LLM

        sealed interface Tool : Event<Nothing, Nothing>

        data class OnToolCall(
            val runId: String,
            val toolCallId: String?,
            val tool: ai.koog.agents.core.tools.Tool<*, *>,
            val toolArgs: ToolArgs
        ) : Tool

        data class OnToolValidationError(
            val runId: String,
            val toolCallId: String?,
            val tool: ai.koog.agents.core.tools.Tool<*, *>,
            val toolArgs: ToolArgs,
            val error: String
        ) : Tool

        data class OnToolCallFailure(
            val runId: String,
            val toolCallId: String?,
            val tool: ai.koog.agents.core.tools.Tool<*, *>,
            val toolArgs: ToolArgs,
            val throwable: Throwable
        ) : Tool

        data class OnToolCallResult(
            val runId: String,
            val toolCallId: String?,
            val tool: ai.koog.agents.core.tools.Tool<*, *>,
            val toolArgs: ToolArgs,
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
            onAgentBeforeClose { ctx -> send(Event.OnAgentBeforeClose(ctx.agentId)) }

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
