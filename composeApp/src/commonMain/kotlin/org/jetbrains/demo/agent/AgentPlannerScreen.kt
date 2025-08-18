package org.jetbrains.demo.agent

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.demo.AgentColumn
import org.jetbrains.demo.AgentGraph
import org.jetbrains.demo.SerializableImmutableList
import org.jetbrains.demo.ToolNode
import org.jetbrains.demo.ToolStatus

@Composable
fun AgentPlannerRoute(viewModel: AgentPlannerViewModel) {
    val scrollState = rememberScrollState()
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    Logger.d("PlannerScreen: uiState=$uiState")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI Planner", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when (val s = uiState) {
            is PlannerUiState.Loading -> Text("Preparing...")
            is PlannerUiState.Error -> Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
            is PlannerUiState.Success -> AgentGraphView(graph = s.graph)
        }
    }
}

@Composable
private fun AgentGraphView(graph: AgentGraph) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val headerText by remember(graph.started, graph.finished) {
            derivedStateOf {
                when {
                    !graph.started -> "Idle"
                    graph.started && !graph.finished -> "Running..."
                    else -> "Finished"
                }
            }
        }

        Text(headerText, style = MaterialTheme.typography.titleMedium)

        val result = graph.result
        if (result != null) {
            ResultCardView(result)
        }

        if (graph.columns.isEmpty()) {
            Text("Awaiting events...")
        } else {
            AgentGraph(graph.columns)
        }
    }
}

@Composable
private fun AgentGraph(columns: SerializableImmutableList<AgentColumn>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(
            items = columns,
            key = { col ->
                when (col) {
                    is AgentColumn.Single -> col.node.tool.id
                    is AgentColumn.Parallel -> col.nodes.joinToString("|") { it.tool.id }
                }
            }
        ) { col ->
            when (col) {
                is AgentColumn.Single -> ToolNodeView(node = { col.node })
                is AgentColumn.Parallel -> ParallelColumnView(tools = { col.nodes })
            }
        }
    }
}

@Composable
private fun ResultCardView(result: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            text = result,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ParallelColumnView(tools: () -> ImmutableList<ToolNode>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.width(48.dp)) {
            VerticalConnector(heightPerItem = 72.dp, count = tools().size)
        }
        tools().forEach { node -> ToolNodeView(node = { node }) }
    }
}

@Composable
private fun ToolNodeView(node: () -> ToolNode) {
    val n = node()
    val color by animateColorAsState(
        when (n.status) {
            ToolStatus.Pending -> MaterialTheme.colorScheme.surfaceVariant
            ToolStatus.Running -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ToolStatus.Finished -> MaterialTheme.colorScheme.primaryContainer
        }, label = "nodeColor"
    )

    val pulse = if (n.status == ToolStatus.Running) {
        val infinite = rememberInfiniteTransition(label = "pulse")
        infinite.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAnim"
        )
    } else null

    Card(
        modifier = Modifier.sizeIn(minWidth = 140.dp).width(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = n.tool.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = if (pulse != null) Modifier.alpha(0.7f + 0.3f * pulse.value) else Modifier
            )
            val statusText = remember(n.status) { derivedStateOf { n.status.name } }
            Text(
                statusText.value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VerticalConnector(heightPerItem: Dp, count: Int) {
    val totalHeight = (heightPerItem * count) + 8.dp * (count - 1)
    val outlineColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.height(totalHeight).width(24.dp)) {
        val pathHeight = size.height
        drawLine(
            color = outlineColor,
            start = androidx.compose.ui.geometry.Offset(x = size.width / 2, y = 0f),
            end = androidx.compose.ui.geometry.Offset(x = size.width / 2, y = pathHeight),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
}
