package org.jetbrains.demo.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.demo.ProposedTravelPlan
import org.jetbrains.demo.PointOfInterest
import org.jetbrains.demo.ResearchedPointOfInterest
import org.jetbrains.demo.agent.AgentPlannerViewModel
import org.jetbrains.demo.agent.TimelineItem

@Composable
fun Workflow(demo: AgentPlannerViewModel) {
    val groupedRows by demo.state.collectAsStateWithLifecycle(persistentListOf())
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = groupedRows,
        ) { row ->
            when (row) {
                is TimelineItem.Messages -> MessageRow(row.message)
                is TimelineItem.Tasks -> ToolRow(row.tasks)
                is TimelineItem.AgentFinished -> AgentFinishedRow(row.proposal)
                is TimelineItem.PointOfInterest -> PointOfInterestRow(row.ideas)
                is TimelineItem.ResearchedPointOfInterest -> ResearchedPointOfInterestRow(row.researchedPointOfInterest)
            }
        }
    }
}

@Composable
fun AgentFinishedRow(proposal: ProposedTravelPlan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Proposed travel plan",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = proposal.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 2.dp)
        )
        val countries = proposal.countriesVisited
        if (countries.isNotEmpty()) {
            Text(
                text = "Countries: ${countries.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "Days: ${proposal.days.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun PointOfInterestRow(ideas: List<PointOfInterest>) {
    if (ideas.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Points of interest",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        ideas.forEach { poi ->
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = poi.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${poi.location} • ${poi.fromDate} - ${poi.toDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ResearchedPointOfInterestRow(poi: ResearchedPointOfInterest) {
    val base = poi.pointOfInterest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Research: ${base.name}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "${base.location} • ${base.fromDate} - ${base.toDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (poi.research.isNotBlank()) {
            Text(
                text = poi.research,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (poi.links.isNotEmpty()) {
            Text(
                text = "Links:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            poi.links.forEach { link ->
                Text(
                    text = "${link.summary} - ${link.url}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}