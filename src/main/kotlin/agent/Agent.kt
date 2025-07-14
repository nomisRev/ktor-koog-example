package com.example.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.*
import ai.koog.prompt.markdown.markdown
import com.example.agent.AgentEvent.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

suspend fun AgentConfig.agent(
    question: String,
    event: suspend (event: AgentEvent) -> Unit
) {
    AIAgent(
        promptExecutor = executor,
        strategy = singleRunStrategy(),
        toolRegistry = registry,
        agentConfig = AIAgentConfig(
            prompt = system(registry),
            model = OpenAIModels.CostOptimized.GPT4oMini,
            maxAgentIterations = 50
        ),
    ) {
        eventHandler(event)
        install(Tracing) {
            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
        }
    }.run(question)
}

private fun system(allTools: ToolRegistry): Prompt = prompt("travel-assistant-agent") {
    system(markdown {
        +"You're an expert travel assistant helping users reach their destination in a reliable way."
        newline()
        +"You have the following tools available to you:"
        bulleted {
            for (tool in allTools.tools) {
                item(tool.name)
            }
        }
        newline()
        header(1, "Task description:")
        +"You can only call tools. Figure out the accurate information from calling the google-maps tool, and the weather tool."
        +"DO NOT STOP UNTIL YOU'VE FOUND ALL THE ANSWERS!"
    })
}

fun AIAgent.FeatureContext.eventHandler(handler: suspend (AgentEvent) -> Unit) {
    install(EventHandler) {
        onToolCall { tool, args -> onToolCalled(tool, args, handler) }
        onToolCallResult { tool, args, result -> onToolCallResult(tool, args, result, handler) }
        onAgentFinished { name, result -> onAgentFinished(name, result, handler) }
    }
}

suspend fun onToolCalled(
    tool: Tool<*, *>,
    toolArgs: Tool.Args,
    handler: suspend (AgentEvent) -> Unit
): Unit = handler(ToolCall(tool.name))

suspend fun onToolCallResult(
    tool: Tool<*, *>,
    toolArgs: Tool.Args,
    result: ToolResult?,
    handler: suspend (AgentEvent) -> Unit
): Unit = handler(ToolCallResult(tool.name, result))

suspend fun onAgentFinished(name: String, result: String?, handler: suspend (AgentEvent) -> Unit): Unit =
    handler(AgentFinished(name, result))
