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
import org.jetbrains.demo.agent.RowType

@Composable
fun Workflow(demo: AgentPlannerViewModel) {
    val groupedRows by demo.state.collectAsStateWithLifecycle(persistentListOf())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = groupedRows,
            key = { row ->
                when (row.type) {
                    RowType.TaskGroup -> "task_${row.items.joinToString("_") { it.id }}"
                    RowType.Message -> "message_${row.message.hashCode()}"
                }
            }
        ) { row ->
            when (row.type) {
                RowType.TaskGroup -> ToolRow(row)
                RowType.Message -> MessageRow(row.message)
            }
        }
    }
}