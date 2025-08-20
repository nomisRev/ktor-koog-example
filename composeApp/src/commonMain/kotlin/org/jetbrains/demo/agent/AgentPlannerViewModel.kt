package org.jetbrains.demo.agent

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.Tool
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.ProposedTravelPlan
import org.jetbrains.demo.agent.TimelineItem.*
import kotlin.jvm.JvmInline

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
    value class Tasks(val tasks: PersistentList<AgentEvent.Tool>) : TimelineItem

    @Stable
    @JvmInline
    value class PointOfInterest(val ideas: PersistentList<org.jetbrains.demo.PointOfInterest>) : TimelineItem

    @Stable
    @JvmInline
    value class ResearchedPointOfInterest(val researchedPointOfInterest: org.jetbrains.demo.ResearchedPointOfInterest) : TimelineItem

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
        httpClient.sse(request = {
            method = HttpMethod.Post
            url("/plan").apply {
                contentType(ContentType.Application.Json)
                setBody(input)
            }
        }) {
            incoming
                .deserialize()
                .processAgentEvents()
                .collect(_state)
        }
    }

    private fun Flow<ServerSentEvent>.deserialize(): Flow<AgentEvent> =
        mapNotNull { it.data?.let { data -> Json.decodeFromString(AgentEvent.serializer(), data) } }

    private fun Flow<AgentEvent>.processAgentEvents(): Flow<PersistentList<TimelineItem>> =
        scan(persistentListOf()) { items, event ->
            when (event) {
                is AgentEvent.AgentFinished,
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
                    logger.d { "Tool finished: ${event.id}" }
                    items.mapIndexed { index, item ->
                        when (item) {
                            is Tasks if index == items.lastIndex && (event.state == Tool.State.Running) ->
                                Tasks(item.tasks.add(event))

                            is Tasks -> Tasks(item.tasks.map { task ->
                                if (task.id == event.id) event else task
                            })

                            is AgentFinished,
                            is TimelineItem.PointOfInterest,
                            is TimelineItem.ResearchedPointOfInterest,
                            is Messages -> item
                        }
                    }
                }

                is AgentEvent.Step1 -> items.add(PointOfInterest(event.ideas.toPersistentList()))
                is AgentEvent.Step2 -> items.add(ResearchedPointOfInterest(event.researchedPointOfInterest))
                is AgentEvent.AgentFinished -> items.add(AgentFinished(event.plan))
                is AgentEvent.AgentError -> TODO()
            }
        }

    private inline fun <A, B> PersistentList<A>.mapIndexed(transform: (Int, A) -> B): PersistentList<B> {
        var count = 0
        return map { transform(count++, it) }
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