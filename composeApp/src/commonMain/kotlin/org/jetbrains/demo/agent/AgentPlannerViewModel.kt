package org.jetbrains.demo.agent

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.sse.sseSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.invoke
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.sse.ServerSentEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.Tool
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.ProposedTravelPlan
import org.jetbrains.demo.agent.TimelineItem.*
import org.jetbrains.demo.deserialize
import org.jetbrains.demo.sseFlow
import kotlin.jvm.JvmInline
import kotlin.time.Duration

@Stable
sealed interface TimelineItem {
    @Stable
    @JvmInline
    value class Messages(val messages: PersistentList<AgentEvent.Message>) : TimelineItem {
        val message: String
            get() = messages.flatMap { it.message }.joinToString(" ")
    }

    @Stable
    @JvmInline
    value class Tasks(val tasks: PersistentList<Tool>) : TimelineItem

    @Stable
    @JvmInline
    value class PointOfInterest(val ideas: PersistentList<org.jetbrains.demo.PointOfInterest>) : TimelineItem

    @Stable
    @JvmInline
    value class ResearchedPointOfInterest(val researchedPointOfInterest: org.jetbrains.demo.ResearchedPointOfInterest) :
        TimelineItem

    @JvmInline
    @Stable
    value class AgentFinished(val proposal: ProposedTravelPlan) : TimelineItem
}

class AgentPlannerViewModel(
    base: Logger,
    private val httpClient: HttpClient,
) : ViewModel() {
    private val logger = base.withTag("AgentPlannerViewModel")

    private val _state = MutableStateFlow<ImmutableList<TimelineItem>>(persistentListOf())
    val state: StateFlow<ImmutableList<TimelineItem>> = _state.asStateFlow()

    fun start(input: JourneyForm) = viewModelScope.launch {
        httpClient.sseFlow {
            method = HttpMethod.Post
            url("/plan")
            contentType(ContentType.Application.Json)
            setBody(input)
        }
            .deserialize<AgentEvent>()
            .onEach { logger.d { "Received event $it" } }
            .aggregateEventsIntoTimeline()
            .collect(_state)
    }

    private fun Flow<AgentEvent>.aggregateEventsIntoTimeline(): Flow<PersistentList<TimelineItem>> =
        scan(persistentListOf()) { items, event ->
            when (event) {
                is AgentEvent.AgentStarted -> items

                is AgentEvent.Message ->
                    if (event.message.isEmpty()) items
                    else items.mutate { mutable ->
                        val lastMessage = mutable.lastOrNull()
                        if (lastMessage is Messages) {
                            mutable.removeAt(mutable.lastIndex)
                            mutable.add(Messages(lastMessage.messages.add(event)))
                        } else {
                            mutable.add(Messages(persistentListOf(event)))
                        }
                    }

                is Tool -> {
                    val lastOrNull = items.lastOrNull()
                    when (event.state) {
                        Tool.State.Running if lastOrNull is Tasks -> items.mutate { mutable ->
                            mutable.removeAt(mutable.lastIndex)
                            mutable.add(Tasks(lastOrNull.tasks.add(event)))
                        }

                        Tool.State.Failed, Tool.State.Succeeded -> items.map { item ->
                            when (item) {
                                is Tasks -> Tasks(item.tasks.map { task ->
                                    if (task.id == event.id) event else task
                                })

                                else -> item
                            }
                        }

                        else -> items.add(Tasks(persistentListOf(event)))
                    }
                }

                is AgentEvent.Step1 -> items.add(PointOfInterest(event.ideas.toPersistentList()))
                is AgentEvent.Step2 -> items.add(ResearchedPointOfInterest(event.researchedPointOfInterest))
                is AgentEvent.AgentFinished -> items.add(AgentFinished(event.plan))
                is AgentEvent.AgentError -> items.add(
                    Messages(
                        persistentListOf(
                            AgentEvent.Message(
                                listOf(
                                    event.result ?: "UNKOWN ERROR"
                                )
                            )
                        )
                    )
                )
            }
        }

    private inline fun <A, B> PersistentList<A>.map(transform: (A) -> B): PersistentList<B> {
        val size = this.size
        if (size == 0) return persistentListOf()
        val builder = persistentListOf<B>().builder()
        for (element in this) {
            builder.add(transform(element))
        }
        return builder.build()
    }
}