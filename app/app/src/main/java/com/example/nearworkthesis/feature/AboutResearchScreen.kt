package com.example.nearworkthesis.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.BuildConfig
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.domain.export.ResultsPackSpec

@Composable
fun AboutResearchScreen(
    modifier: Modifier = Modifier,
    onOpenMethodsAssumptions: () -> Unit = {},
    onOpenDataFormats: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("About / Research", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Purpose, privacy, and study context for this thesis build.",
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
                Text("What this app does", style = MaterialTheme.typography.titleMedium)
                Text(
                    "HowFar collects nearwork samples (distance + illumination) and turns them into daily, weekly, and monthly summaries for awareness. It is designed for monitoring and reflection, not diagnosis.",
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
                Text("Data and privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "All data stays on this device. There are no network calls, analytics, or cloud sync. Exports are created only when you initiate them.",
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
                Text("Research links", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Open supporting materials and documentation used in the thesis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onOpenMethodsAssumptions) {
                        Text("Methods")
                    }
                    TextButton(onClick = onOpenDataFormats) {
                        Text("Data formats")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Versions", style = MaterialTheme.typography.titleMedium)
                Text("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
                Text("Database v${NearworkDatabase.DB_VERSION}", style = MaterialTheme.typography.bodyMedium)
                Text("Results Pack spec v${ResultsPackSpec.VERSION}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = true))
    }
}
