package org.jetbrains.demo.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import org.jetbrains.demo.agent.GroupedTimelineRow

@Composable
fun ToolRow(row: GroupedTimelineRow) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        row.items.forEach { task ->
            key(task.id) {
                TaskCard(task = task)
            }
        }
    }
}