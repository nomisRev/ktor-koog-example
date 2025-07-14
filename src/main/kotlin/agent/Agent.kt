package com.example.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.*
import ai.koog.prompt.markdown.markdown
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
