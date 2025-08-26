package org.jetbrains.demo.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.demo.AgentEvent

@Composable
fun ToolRow(row: ImmutableList<AgentEvent.Tool>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        row.forEach { task ->
            key(task.id) {
                ToolCard(task = task)
            }
        }
    }
}