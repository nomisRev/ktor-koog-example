package org.jetbrains.demo.agent

import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastLastOrNull
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentGraph
import org.jetbrains.demo.JourneyForm
import kotlin.jvm.JvmInline
import kotlin.random.Random

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
    value class Tasks(val tasks: PersistentList<Task>) : TimelineItem

    @Stable
    data class Task(val id: String, val name: String, val status: TaskStatus)
}

enum class TaskStatus { Executing, Finished }


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
                    if(event.message.isEmpty()) items
                    else items.mutate { mutable ->
                        val lastMessage = mutable.lastOrNull()
                        if (lastMessage is TimelineItem.Messages) {
                            mutable.removeAt(mutable.lastIndex)
                            mutable.add(TimelineItem.Messages(lastMessage.messages.add(event)))
                        } else {
                            mutable.add(TimelineItem.Messages(persistentListOf(event)))
                        }
                    }

                is AgentEvent.ToolFinished -> {
                    logger.d { "Tool finished: ${event.id}" }
                    items.map { item ->
                        when (item) {
                            is TimelineItem.Messages -> item
                            is TimelineItem.Tasks -> TimelineItem.Tasks(item.tasks.map { task ->
                                logger.d { "Task: ${task.id} == ${event.id} : ${task.status} -> TaskStatus.Finished" }
                                if (task.id == event.id) task.copy(status = TaskStatus.Finished) else task
                            })
                        }
                    }
                }

                is AgentEvent.ToolStarted ->
                    items.mutate { mutable ->
                        val lastMessage = mutable.lastOrNull()
                        val item = TimelineItem.Task(event.id, event.name, TaskStatus.Executing)
                        if (lastMessage is TimelineItem.Tasks) {
                            mutable.removeAt(mutable.lastIndex)
                            mutable.add(TimelineItem.Tasks(lastMessage.tasks.add(item)))
                        } else {
                            mutable.add(TimelineItem.Tasks(persistentListOf(item)))
                        }
                    }
            }
        }

    // Stubbed events.
    private fun events(): Flow<AgentEvent> = flow {
        emit(AgentEvent.AgentStarted("1", "1"))
        emit(AgentEvent.Message(listOf("Hello, I am going to start researching your request. Please wait a moment.")))
        emit(AgentEvent.ToolStarted("1", "web"))
        delay(Random.nextLong(500, 1500L))
        emit(AgentEvent.ToolStarted("2", "database"))
        emit(AgentEvent.ToolStarted("3", "web"))
        delay(Random.nextLong(500, 1500L))
        emit(AgentEvent.ToolStarted("4", "database"))
        delay(Random.nextLong(200, 1000L))
        emit(AgentEvent.ToolStarted("5", "web"))
        delay(Random.nextLong(200, 1000L))
        emit(AgentEvent.ToolStarted("6", "database"))

        emit(AgentEvent.ToolFinished("1", "web"))
        emit(AgentEvent.Message(listOf("I'm going to look up some additional personalised information.")))
        emit(AgentEvent.ToolStarted("7", "other"))
        emit(AgentEvent.Message(listOf("Message about OTHER.")))

        emit(AgentEvent.ToolFinished("2", "database"))
        emit(AgentEvent.ToolFinished("4", "database"))
        emit(AgentEvent.Message(listOf("I'm going to check if there are any special requests that need to be considered.")))
        emit(AgentEvent.ToolFinished("3", "other"))
        emit(AgentEvent.ToolFinished("5", "web"))
        emit(AgentEvent.Message(listOf("Finalising your request. Please wait a moment.")))
        emit(AgentEvent.ToolFinished("6", "database"))
        emit(AgentEvent.ToolFinished("7", "web"))

        emit(AgentEvent.Message(listOf("Here is the final result of your request.")))
        emit(AgentEvent.AgentFinished("1", "1", "result"))
    }.onEach { delay(Random.nextLong(1000, 2500L)) }

    inline fun <A, B> PersistentList<A>.map(transform: (A) -> B): PersistentList<B> {
        val size = this.size
        if (size == 0) return persistentListOf()
        val builder = persistentListOf<B>().builder()
        for (element in this) {
            builder.add(transform(element))
        }
        return builder.build()
    }
}