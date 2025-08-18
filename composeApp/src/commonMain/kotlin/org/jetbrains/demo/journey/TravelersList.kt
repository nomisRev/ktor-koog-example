package org.jetbrains.demo.journey

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.demo.Traveler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelersList(
    travellers: ImmutableList<Traveler>,
    onAdd: () -> Unit,
    onRemove: (id: String) -> Unit,
    onNameChange: (id: String, name: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Travelers")

        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = travellers, key = { it.id }) { traveler ->
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = traveler.name,
                            onValueChange = { onNameChange(traveler.id, it) },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onRemove(traveler.id) }) { Text("Remove") }
                    }
                }
            }
        }

        Button(onClick = onAdd) { Text("Add traveler") }
    }
}
