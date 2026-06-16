package com.example.nearworkthesis.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class QuickExportFormat { Csv, Uf2 }

enum class QuickExportSeries { Raw, Processed }

sealed interface QuickExportState {
    data object Idle : QuickExportState
    data object Exporting : QuickExportState
    data class Success(val filename: String) : QuickExportState
    data class Error(val message: String) : QuickExportState
}

data class QuickExportCardColors(
    val accent: Color,
    val onAccent: Color,
    val buttonAccent: Color = accent,
    val onButtonAccent: Color = onAccent
)

@Composable
fun QuickExportCard(
    exportFormat: QuickExportFormat,
    exportSeries: QuickExportSeries,
    exportState: QuickExportState,
    onSelectFormat: (QuickExportFormat) -> Unit,
    onSelectSeries: (QuickExportSeries) -> Unit,
    onExport: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    colors: QuickExportCardColors? = null,
    title: String = "Export this day",
    subtitle: String = "Save raw/processed data as CSV or the original HowFar UF2 snapshot.",
    titleTextStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = titleTextStyle,
                fontWeight = FontWeight.Bold,
                color = colors?.accent ?: MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SingleChoiceSegmentedButtonRow {
                    QuickExportFormat.entries.forEachIndexed { index, format ->
                        SegmentedButton(
                            selected = exportFormat == format,
                            onClick = { onSelectFormat(format) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = QuickExportFormat.entries.size)
                        ) {
                            Text(if (format == QuickExportFormat.Csv) "CSV" else "UF2")
                        }
                    }
                }
            }
            if (exportFormat == QuickExportFormat.Csv) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SingleChoiceSegmentedButtonRow {
                        QuickExportSeries.entries.forEachIndexed { index, series ->
                            SegmentedButton(
                                selected = exportSeries == series,
                                onClick = { onSelectSeries(series) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = QuickExportSeries.entries.size)
                            ) {
                                Text(if (series == QuickExportSeries.Raw) "Raw" else "Processed")
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onExport,
                    enabled = exportState != QuickExportState.Exporting,
                    colors = colors?.let {
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = it.buttonAccent,
                            contentColor = it.onButtonAccent
                        )
                    } ?: androidx.compose.material3.ButtonDefaults.buttonColors()
                ) {
                    if (exportState == QuickExportState.Exporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Exporting")
                    } else {
                        Text("Export to Downloads", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (exportState is QuickExportState.Success) {
                Text(
                    text = "Saved as ${exportState.filename}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    when (exportState) {
        is QuickExportState.Success -> {
            AlertDialog(
                onDismissRequest = onDismissMessage,
                confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } },
                title = { Text("Export saved") },
                text = { Text("Saved ${exportState.filename} in Downloads.") }
            )
        }
        is QuickExportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismissMessage,
                confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK") } },
                title = { Text("Export failed") },
                text = { Text(exportState.message) }
            )
        }
        else -> Unit
    }
}
