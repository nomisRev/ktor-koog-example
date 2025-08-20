package org.jetbrains.demo.agent.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import demo.composeapp.generated.resources.Res
import demo.composeapp.generated.resources.browser
import demo.composeapp.generated.resources.database
import demo.composeapp.generated.resources.fx
import demo.composeapp.generated.resources.maps
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.demo.AgentEvent
import org.jetbrains.demo.AgentEvent.Tool
import kotlin.math.PI

enum class Task(val icon: DrawableResource) {
    Web(Res.drawable.browser),
    Database(Res.drawable.database),
    Maps(Res.drawable.maps),
    Other(Res.drawable.fx);
}

@Composable
fun TaskCard(task: AgentEvent.Tool, modifier: Modifier = Modifier) {
    val isFinished = task.state == Tool.State.Failed || task.state == Tool.State.Succeeded
    val taskName = task.name
    val taskKind = when {
        taskName.contains("maps") -> Task.Maps
        taskName.contains("database") -> Task.Database
        taskName.contains("web") -> Task.Web
        else -> Task.Other
    }
    val resource = painterResource(taskKind.icon)

    val infiniteTransition = rememberInfiniteTransition(label = "TaskCard-${task.id}")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ProgressAnimation-${task.id}"
    )

    val progressColor = MaterialTheme.colorScheme.primary
    val strokeWidth = 3.dp
    val borderModifier = if (!isFinished) {
        modifier
            .padding(strokeWidth / 2) // Add padding to ensure border is visible
            .drawWithContent {
                drawContent() // Draw the card content first
                drawProgressBorder(progress, progressColor, strokeWidth.toPx())
            }
    } else modifier

    Card(
        modifier = modifier.then(borderModifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isFinished)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                resource,
                contentDescription = taskName,
                tint = if (isFinished)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = taskName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFinished)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// Extension function to draw animated border progress
fun DrawScope.drawProgressBorder(
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    val cornerRadius = 12.dp.toPx()
    val halfStroke = strokeWidth / 2f

    // Calculate the total perimeter including rounded corners
    val straightEdges = 2 * (size.width + size.height) - 8 * cornerRadius

    val cornerCircumference = 2f * PI.toFloat() * cornerRadius
    val totalPerimeter = straightEdges + cornerCircumference

    // Indeterminate indicator: moving segment
    val segmentLength = totalPerimeter * 0.25f // Segment is 25% of total perimeter
    val startPosition = totalPerimeter * progress
    val endPosition = startPosition + segmentLength

    // Helper function to draw a segment of the border
    fun drawSegment(start: Float, end: Float) {
        var currentPosition = 0f
        var segmentStart = start
        var segmentEnd = end

        // Wrap around if segment goes beyond perimeter
        if (segmentStart >= totalPerimeter) segmentStart -= totalPerimeter
        if (segmentEnd > totalPerimeter) {
            // Draw wrapped segment in two parts
            drawSegment(segmentStart, totalPerimeter)
            drawSegment(0f, segmentEnd - totalPerimeter)
            return
        }

        // Top edge (left to right)
        val topEdgeLength = size.width - 2 * cornerRadius
        if (segmentStart < currentPosition + topEdgeLength && segmentEnd > currentPosition) {
            val edgeStart = maxOf(0f, segmentStart - currentPosition)
            val edgeEnd = minOf(topEdgeLength, segmentEnd - currentPosition)
            if (edgeEnd > edgeStart) {
                drawLine(
                    color = color,
                    start = Offset(cornerRadius + edgeStart, halfStroke),
                    end = Offset(cornerRadius + edgeEnd, halfStroke),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        currentPosition += topEdgeLength

        // Top-right corner
        val topRightCornerLength = (PI.toFloat() * cornerRadius) / 2f
        if (segmentStart < currentPosition + topRightCornerLength && segmentEnd > currentPosition) {
            val cornerStart = maxOf(0f, segmentStart - currentPosition)
            val cornerEnd = minOf(topRightCornerLength, segmentEnd - currentPosition)
            if (cornerEnd > cornerStart) {
                val startAngle = -90f + (cornerStart / topRightCornerLength) * 90f
                val sweepAngle = ((cornerEnd - cornerStart) / topRightCornerLength) * 90f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(size.width - 2 * cornerRadius - halfStroke, halfStroke),
                    size = Size(2 * cornerRadius, 2 * cornerRadius),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        currentPosition += topRightCornerLength

        // Right edge (top to bottom)
        val rightEdgeLength = size.height - 2 * cornerRadius
        if (segmentStart < currentPosition + rightEdgeLength && segmentEnd > currentPosition) {
            val edgeStart = maxOf(0f, segmentStart - currentPosition)
            val edgeEnd = minOf(rightEdgeLength, segmentEnd - currentPosition)
            if (edgeEnd > edgeStart) {
                drawLine(
                    color = color,
                    start = Offset(size.width - halfStroke, cornerRadius + edgeStart),
                    end = Offset(size.width - halfStroke, cornerRadius + edgeEnd),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        currentPosition += rightEdgeLength

        // Bottom-right corner
        val bottomRightCornerLength = (PI.toFloat() * cornerRadius) / 2f
        if (segmentStart < currentPosition + bottomRightCornerLength && segmentEnd > currentPosition) {
            val cornerStart = maxOf(0f, segmentStart - currentPosition)
            val cornerEnd = minOf(bottomRightCornerLength, segmentEnd - currentPosition)
            if (cornerEnd > cornerStart) {
                val startAngle = 0f + (cornerStart / bottomRightCornerLength) * 90f
                val sweepAngle = ((cornerEnd - cornerStart) / bottomRightCornerLength) * 90f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(
                        size.width - 2 * cornerRadius - halfStroke,
                        size.height - 2 * cornerRadius - halfStroke
                    ),
                    size = Size(2 * cornerRadius, 2 * cornerRadius),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        currentPosition += bottomRightCornerLength

        // Bottom edge (right to left)
        val bottomEdgeLength = size.width - 2 * cornerRadius
        if (segmentStart < currentPosition + bottomEdgeLength && segmentEnd > currentPosition) {
            val edgeStart = maxOf(0f, segmentStart - currentPosition)
            val edgeEnd = minOf(bottomEdgeLength, segmentEnd - currentPosition)
            if (edgeEnd > edgeStart) {
                drawLine(
                    color = color,
                    start = Offset(size.width - cornerRadius - edgeStart, size.height - halfStroke),
                    end = Offset(size.width - cornerRadius - edgeEnd, size.height - halfStroke),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        currentPosition += bottomEdgeLength

        // Bottom-left corner
        val bottomLeftCornerLength = (PI.toFloat() * cornerRadius) / 2f
        if (segmentStart < currentPosition + bottomLeftCornerLength && segmentEnd > currentPosition) {
            val cornerStart = maxOf(0f, segmentStart - currentPosition)
            val cornerEnd = minOf(bottomLeftCornerLength, segmentEnd - currentPosition)
            if (cornerEnd > cornerStart) {
                val startAngle = 90f + (cornerStart / bottomLeftCornerLength) * 90f
                val sweepAngle = ((cornerEnd - cornerStart) / bottomLeftCornerLength) * 90f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(halfStroke, size.height - 2 * cornerRadius - halfStroke),
                    size = Size(2 * cornerRadius, 2 * cornerRadius),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        currentPosition += bottomLeftCornerLength

        // Left edge (bottom to top)
        val leftEdgeLength = size.height - 2 * cornerRadius
        if (segmentStart < currentPosition + leftEdgeLength && segmentEnd > currentPosition) {
            val edgeStart = maxOf(0f, segmentStart - currentPosition)
            val edgeEnd = minOf(leftEdgeLength, segmentEnd - currentPosition)
            if (edgeEnd > edgeStart) {
                drawLine(
                    color = color,
                    start = Offset(halfStroke, size.height - cornerRadius - edgeStart),
                    end = Offset(halfStroke, size.height - cornerRadius - edgeEnd),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        currentPosition += leftEdgeLength

        // Top-left corner
        val topLeftCornerLength = (PI.toFloat() * cornerRadius) / 2f
        if (segmentStart < currentPosition + topLeftCornerLength && segmentEnd > currentPosition) {
            val cornerStart = maxOf(0f, segmentStart - currentPosition)
            val cornerEnd = minOf(topLeftCornerLength, segmentEnd - currentPosition)
            if (cornerEnd > cornerStart) {
                val startAngle = 180f + (cornerStart / topLeftCornerLength) * 90f
                val sweepAngle = ((cornerEnd - cornerStart) / topLeftCornerLength) * 90f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = Size(2 * cornerRadius, 2 * cornerRadius),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }

    drawSegment(startPosition, endPosition)
}
