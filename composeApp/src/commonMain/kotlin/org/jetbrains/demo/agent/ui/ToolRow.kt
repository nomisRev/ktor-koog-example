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
        // Preserve type order by first appearance
        val typesInOrder = LinkedHashSet<AgentEvent.Tool.Type>()
        row.forEach { typesInOrder.add(it.type) }

        typesInOrder.forEach { type ->
            val group = row.filter { it.type == type }
            if (group.isNotEmpty()) {
                val runningCount = group.count { it.state == AgentEvent.Tool.State.Running }
                val representative = if (runningCount > 0) {
                    group.last { it.state == AgentEvent.Tool.State.Running }
                } else {
                    group.last()
                }
                key(type.name) {
                    ToolCard(
                        task = representative,
                        runningCount = runningCount,
                        titleOverride = type.name
                    )
                }
            }
        }
    }
}