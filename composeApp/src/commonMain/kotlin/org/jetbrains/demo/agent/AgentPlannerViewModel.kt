package org.jetbrains.demo.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.sse.ServerSentEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.demo.AgentColumn
import org.jetbrains.demo.AgentColumn.*
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentGraph
import org.jetbrains.demo.JourneyForm
import org.jetbrains.demo.Tool
import org.jetbrains.demo.ToolNode
import org.jetbrains.demo.ToolStatus
import org.jetbrains.demo.agent.PlannerUiState.*

sealed interface PlannerUiState {
    data object Loading : PlannerUiState
    data class Success(val graph: AgentGraph) : PlannerUiState
    data class Error(val message: String) : PlannerUiState
}

class AgentPlannerViewModel(
    base: Logger,
    private val httpClient: HttpClient,
) : ViewModel() {
    private val logger = base.withTag("AgentPlannerViewModel")

    private val _state = MutableStateFlow<PlannerUiState>(PlannerUiState.Success(AgentGraph.empty()))
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    fun start(input: JourneyForm) = viewModelScope.launch {
        httpClient.sse(request = {
            method = HttpMethod.Post
            url("/plan").apply {
                contentType(ContentType.Application.Json)
                setBody(input)
            }
        }) {
            incoming
                .mapNotNull { it.toAgentEvent() }
                .catch { t ->
                    logger.e("Agent run failed: ${t.message}", t)
                    _state.update { PlannerUiState.Error(t.message ?: "Unknown error") }
                }
                .collect { event ->
                    logger.d("Event: $event")
                    handleEvent(event)
                }
        }
    }

    private fun ServerSentEvent.toAgentEvent(): AgentEvent? =
        data?.let { Json.decodeFromString(AgentEvent.serializer(), it) }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.AgentStarted -> _state.update { s ->
                Success(currentGraph(s).copy(started = true))
            }

            is AgentEvent.ToolStarted -> _state.update { s ->
                val g = currentGraph(s)
                val newCol: AgentColumn = if (event.ids.size == 1) {
                    Single(ToolNode(event.ids[0], ToolStatus.Running))
                } else {
                    Parallel(event.ids.map { ToolNode(it, ToolStatus.Running) }.toImmutableList())
                }
                Success(g.copy(columns = (g.columns + newCol).toImmutableList()))
            }

            is AgentEvent.ToolFinished -> _state.update { s ->
                val g = currentGraph(s)
                val updatedCols = g.columns.map { col ->
                    when (col) {
                        is AgentColumn.Single -> if (event.ids.any { it.id == col.node.tool.id })
                            col.copy(node = col.node.copy(status = ToolStatus.Finished)) else col

                        is AgentColumn.Parallel -> col.copy(
                            nodes = col.nodes.map { n ->
                                if (event.ids.any { it.id == n.tool.id }) n.copy(status = ToolStatus.Finished) else n
                            }.toImmutableList()
                        )
                    }
                }.toImmutableList()
                Success(g.copy(columns = updatedCols))
            }

            is AgentEvent.AgentFinished -> _state.update { s ->
                val g = currentGraph(s)
                val finishedCols = g.columns.map { col ->
                    when (col) {
                        is AgentColumn.Single -> col.copy(node = col.node.copy(status = ToolStatus.Finished))
                        is AgentColumn.Parallel -> col.copy(
                            nodes = col.nodes.map { it.copy(status = ToolStatus.Finished) }.toImmutableList()
                        )
                    }
                }.toImmutableList()
                Success(g.copy(finished = true, result = event.result, columns = finishedCols))
            }

            is AgentEvent.Message -> logger.d("Dropped message: ${event.message}")
        }
    }

    private fun currentGraph(state: PlannerUiState): AgentGraph =
        (state as? Success)?.graph ?: AgentGraph.empty()
}
