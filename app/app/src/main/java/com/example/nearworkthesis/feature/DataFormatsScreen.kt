package com.example.nearworkthesis.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataFormatsScreen(
    modifier: Modifier = Modifier,
    onOpenExport: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Data formats", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Summary of exports and the localDay/timezone contract.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Results Pack (ZIP)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Exports create a ZIP with daily.csv, sessions.csv, import_quality.csv, and manifest.json. The manifest records preprocessing settings, thresholds, and timezone details for reproducibility.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onOpenExport) {
                    Text("Open export")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("localDay and timezone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "localDay is stored as YYYY-MM-DD and derived from UTC timestamps using the profile timezoneId. Day grouping and summaries always use localDay (not SQL date math on epoch).",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Export reminders", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Exports are user-initiated and stored locally. Share only what is needed for the thesis report or analysis.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
