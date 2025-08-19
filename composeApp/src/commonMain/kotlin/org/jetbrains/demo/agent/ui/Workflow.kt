package org.jetbrains.demo.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.demo.agent.AgentPlannerViewModel
import org.jetbrains.demo.agent.TimelineItem
import org.jetbrains.demo.ui.Logger

@Composable
fun Workflow(demo: AgentPlannerViewModel) {
    val groupedRows by demo.state.collectAsStateWithLifecycle(persistentListOf())
    Logger.app.d { "Workflow: $groupedRows" }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = groupedRows) { row ->
            when(row) {
                is TimelineItem.Messages -> MessageRow(row.message)
                is TimelineItem.Tasks -> ToolRow(row.tasks)
            }
        }
    }
}