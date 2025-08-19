package org.jetbrains.demo.agent.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.feature.message.FeatureMessageProcessor
import ai.koog.agents.core.feature.writer.FeatureMessageFileWriter
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.examples.tripplanning.tools.*
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.xml.xml
import io.ktor.server.sse.ServerSSESession
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.agent.koog.ktor.StreamingAIAgent
import org.jetbrains.demo.agent.koog.ktor.sseAgent
import org.jetbrains.demo.agent.tools.OpenMeteoClient
import org.jetbrains.demo.agent.tools.Tools
import org.jetbrains.demo.user.User
import kotlin.time.Clock

suspend fun ServerSSESession.createPlannerAgent(tools: Tools): StreamingAIAgent<JourneyForm, TripPlan> {
    val toolRegistry = ToolRegistry {
        tool(::addDate)
        tools(tools.weatherTool)
//        tools(userTools)

        // Technical tools:
        tool(ProvidePlanSuggestion)
        tool(ProvideUserPlan)

    } + tools.googleMaps

    val plannerStrategy = plannerStrategy(
        tools = tools,
        addDateTool = ::addDate.asTool(),
        providePlanSuggestionTool = ProvidePlanSuggestion,
        provideUserPlanTool = ProvideUserPlan,
    )

    return sseAgent<JourneyForm, TripPlan>(
        strategy = plannerStrategy,
        model = OpenAIModels.Reasoning.GPT4oMini,
        tools = toolRegistry,
        configureAgent = {
            AIAgentConfig(
                prompt = prompt(
                    "planner-agent-prompt",
                    params = LLMParams(temperature = 0.3)
                ) {
                    system(
                        """
                You are a trip planning agent that helps the user to plan their trip.
                Use the information provided by the user to suggest the best possible trip plan.
                """.trimIndent()
                    )
                },
                model = OpenAIModels.Reasoning.GPT4oMini,
                maxAgentIterations = 200
            )
        },
        installFeatures = {
            install(Tracing) {
                addMessageProcessor(
                    TraceFeatureMessageFileWriter(
                    Path("agent-traces.log"),
                    { SystemFileSystem.sink(it).buffered() }
                ))
            }
        })
}


private fun plannerStrategy(
    tools: Tools,
    addDateTool: Tool<*, *>,
    providePlanSuggestionTool: ProvidePlanSuggestion,
    provideUserPlanTool: ProvideUserPlan,
) = strategy<JourneyForm, TripPlan>("planner-strategy") {
    val userPlanKey = createStorageKey<TripPlan>("user_plan")
    val prevSuggestedPlanKey = createStorageKey<TripPlan>("prev_suggested_plan")

    val setup by node<JourneyForm, JourneyForm> { form ->
        llm.writeSession {
            updatePrompt {
                system {
                    +"Today's date is ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}. ."
                    +"Timezone is ${TimeZone.currentSystemDefault()}."
                }
            }
        }
        form
    }

    val clarifyUserPlan by subgraphWithTask<JourneyForm, TripPlan>(
        tools = listOf(addDateTool),
        finishTool = provideUserPlanTool
    ) { form ->
        xml {
            tag("instructions") {
                +"""
                Clarify a user plan until the locations, dates and additional information, such as user preferences, are provided.    
                """.trimIndent()
            }
            tag("user_input_form") {
                tag("from_city") { +form.fromCity }
                tag("to_city") { +form.toCity }
                tag("preferred_transport") { +"${form.transport}" }
                tag("start_date") { +"${form.startDate}" }
                tag("end_date") { +"${form.endDate}" }
                tag("travelers") { +form.travelers.joinToString(", ") { it.name } }
                if (form.details != null) tag("details") { +"${form.details}" }
            }
        }
    }

    val suggestPlan by subgraphWithTask<SuggestPlanRequest, TripPlan>(
        tools = tools.googleMaps.tools + tools.weatherTool.asTools(),
        finishTool = providePlanSuggestionTool,
    ) { input ->
        xml {
            tag("instructions") {
                markdown {
                    h2("Requirements")
                    bulleted {
                        item("Suggest the plan for ALL days and ALL locations in the user plan, preserving the order.")
                        item("Follow the user plan and provide a detailed step-by-step plan suggestion with multiple options for each date.")
                        item("Consider weather conditions when suggesting places for each date and time to assess how suitable the activity is for the weather.")
                        item("Check detailed information about each place, such as opening hours and reviews, before adding it to the final plan suggestion.")
                    }

                    h2("Tool usage guidelines")
                    +"""
                    ALWAYS use "maps_search_places" tool to search for places, AVOID making your own suggestions.
                    While searching for places, keep search query short and specific:
                    Example DO: "museum", "historical museum", "italian restaurant", "coffee shop", "art gallery"
                    Example DON'T: "interesting cultural sites", "local cuisine restaurants", "restaurant in the city center"
                    """.trimIndent()
                    br()

                    """
                    Use other "maps_*" tools to get more details about the place: reviews, opening hours, distances, etc.
                    """.trimIndent()
                    br()

                    """
                    Use ${
                        tools.weatherTool.asTools().joinToString(", ") { it.name }
                    } tool for each date, requesting hourly granularity when you need to
                    make a detailed itinerary.
                    """.trimIndent()
                }
            }

            when (input) {
                is SuggestPlanRequest.InitialRequest -> {
                    tag("user_plan") {
                        +input.userPlan.toStringDefault()
                    }
                }

                is SuggestPlanRequest.CorrectionRequest -> {
                    tag("additional_instructions") {
                        +"User asked for corrections to the previously suggested plan. Provide updated plan according to these corrections."
                    }

                    tag("user_plan") {
                        +input.userPlan.toStringDefault()
                    }

                    tag("previously_suggested_plan") {
                        +input.prevSuggestedPlan.toStringDefault()
                    }

                    tag("user_feedback") {
                        +input.userFeedback
                    }
                }
            }
        }
    }

    val saveUserPlan by node<TripPlan, Unit> { plan ->
        storage.set(userPlanKey, plan)

        llm.writeSession {
            replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)
        }
    }

    val savePrevSuggestedPlan by node<TripPlan, TripPlan> { plan ->
        storage.set(prevSuggestedPlanKey, plan)

        llm.writeSession {
            replaceHistoryWithTLDR(strategy = HistoryCompressionStrategy.WholeHistory)
        }

        plan
    }

    val createInitialPlanRequest by node<Unit, SuggestPlanRequest> {
        SuggestPlanRequest.InitialRequest(
            userPlan = storage.getValue(userPlanKey),
        )
    }

    val createPlanCorrectionRequest by node<String, SuggestPlanRequest> { userFeedback ->
        SuggestPlanRequest.CorrectionRequest(
            userFeedback = userFeedback,
            userPlan = storage.getValue(userPlanKey),
            prevSuggestedPlan = storage.getValue(prevSuggestedPlanKey)
        )
    }

    // Show plan suggestion to the user and get a response
//    val showPlanSuggestion by node<String, String> { message ->
//        userTools.showMessage(message)
//    }

//    val processUserFeedback by nodeLLMRequestStructured<PlanSuggestionFeedback>()

    // Edges

    nodeStart then setup then clarifyUserPlan then saveUserPlan then createInitialPlanRequest then suggestPlan then savePrevSuggestedPlan then nodeFinish

//    edge(
//        savePrevSuggestedPlan forwardTo showPlanSuggestion
//                transformed { it.toStringDefault() }
//    )

//    edge(showPlanSuggestion forwardTo processUserFeedback)

//    edge(
//        processUserFeedback forwardTo createPlanCorrectionRequest
//                transformed { it.getOrThrow().structure }
//                onCondition { !it.isAccepted }
//                transformed { it.message }
//    )
//    edge(
//        processUserFeedback forwardTo nodeFinish
//                transformed { it.getOrThrow().structure }
//                onCondition { it.isAccepted }
//                transformed { storage.getValue(prevSuggestedPlanKey) }
//    )

//    edge(createPlanCorrectionRequest forwardTo suggestPlan)
}