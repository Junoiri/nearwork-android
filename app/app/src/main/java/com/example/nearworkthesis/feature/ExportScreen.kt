package com.example.nearworkthesis.feature

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalHowfarUf2Archive
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.domain.export.ResultsPackCsvBuilder
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ExportScreen(
    modifier: Modifier = Modifier,
    measurementRepository: MeasurementRepository,
    snackbarHostState: SnackbarHostState,
    onGoToImport: () -> Unit
) {
    val activeProfileStore = LocalActiveProfileStore.current
    val howfarUf2Archive = LocalHowfarUf2Archive.current
    val viewModel: ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ExportViewModel.factory(measurementRepository, activeProfileStore, howfarUf2Archive)
    )
    val state by viewModel.uiState.collectAsState()

    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var pendingFilename by remember { mutableStateOf<String?>(null) }
    var pendingZipBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingZipFilename by remember { mutableStateOf<String?>(null) }
    var pendingBinaryBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingBinaryFilename by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val csv = pendingCsv
        val filename = pendingFilename
        pendingCsv = null
        pendingFilename = null

        if (uri == null) {
            viewModel.onSaveCancelled()
            return@rememberLauncherForActivityResult
        }
        if (csv == null || filename == null) {
            viewModel.onSaveFailed("Export payload missing. Please retry.")
            return@rememberLauncherForActivityResult
        }

        val result = writeTextToUri(context, uri, csv)
        result.fold(
            onSuccess = {
                viewModel.onSaved(filename)
            },
            onFailure = { t ->
                viewModel.onSaveFailed(t.message ?: "Unable to save CSV.")
            }
        )
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val bytes = pendingZipBytes
        val filename = pendingZipFilename
        pendingZipBytes = null
        pendingZipFilename = null

        if (uri == null) {
            viewModel.onSaveCancelled()
            return@rememberLauncherForActivityResult
        }
        if (bytes == null || filename == null) {
            viewModel.onSaveFailed("Export payload missing. Please retry.")
            return@rememberLauncherForActivityResult
        }

        val result = writeBytesToUri(context, uri, bytes)
        result.fold(
            onSuccess = { viewModel.onSaved(filename) },
            onFailure = { t -> viewModel.onSaveFailed(t.message ?: "Unable to save ZIP.") }
        )
    }

    val createBinaryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val bytes = pendingBinaryBytes
        val filename = pendingBinaryFilename
        pendingBinaryBytes = null
        pendingBinaryFilename = null

        if (uri == null) {
            viewModel.onSaveCancelled()
            return@rememberLauncherForActivityResult
        }
        if (bytes == null || filename == null) {
            viewModel.onSaveFailed("Export payload missing. Please retry.")
            return@rememberLauncherForActivityResult
        }

        val result = writeBytesToUri(context, uri, bytes)
        result.fold(
            onSuccess = { viewModel.onSaved(filename) },
            onFailure = { t -> viewModel.onSaveFailed(t.message ?: "Unable to save UF2.") }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExportEvent.LaunchCreateDocument -> {
                    pendingCsv = event.csv
                    pendingFilename = event.filename
                    createDocumentLauncher.launch(event.filename)
                }
                is ExportEvent.LaunchCreateZip -> {
                    pendingZipBytes = event.bytes
                    pendingZipFilename = event.filename
                    createZipLauncher.launch(event.filename)
                }
                is ExportEvent.LaunchCreateBinary -> {
                    pendingBinaryBytes = event.bytes
                    pendingBinaryFilename = event.filename
                    createBinaryLauncher.launch(event.filename)
                }
            }
        }
    }

    LaunchedEffect(state) {
        val success = state as? ExportUiState.Success ?: return@LaunchedEffect
        val message = if (success.filename.endsWith(".zip", ignoreCase = true)) {
            "Saved ${success.filename} (contains 3 CSVs)"
        } else {
            "Saved ${success.filename}"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.dismissSuccess()
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (state) {
            is ExportUiState.Loading -> ExportLoading()
            is ExportUiState.Empty -> ExportEmpty(onGoToImport = onGoToImport)
            is ExportUiState.Ready -> ExportContent(
                model = (state as ExportUiState.Ready).model,
                isExporting = false,
                onGoToImport = onGoToImport,
                onSetType = viewModel::setExportType,
                onSetPreset = viewModel::setPreset,
                onSetCustomStart = viewModel::setCustomStartDay,
                onSetCustomEnd = viewModel::setCustomEndDay,
                onExport = viewModel::export
            )
            is ExportUiState.Exporting -> ExportContent(
                model = (state as ExportUiState.Exporting).model,
                isExporting = true,
                onGoToImport = onGoToImport,
                onSetType = viewModel::setExportType,
                onSetPreset = viewModel::setPreset,
                onSetCustomStart = viewModel::setCustomStartDay,
                onSetCustomEnd = viewModel::setCustomEndDay,
                onExport = viewModel::export
            )
            is ExportUiState.Error -> ExportError(
                message = (state as ExportUiState.Error).message,
                onRetry = viewModel::retry
            )
            is ExportUiState.Success -> {
                // Snackbar handles it; render current Ready model if present.
                val model = (state as ExportUiState.Success).model
                ExportContent(
                    model = model,
                    isExporting = false,
                    onGoToImport = onGoToImport,
                    onSetType = viewModel::setExportType,
                    onSetPreset = viewModel::setPreset,
                    onSetCustomStart = viewModel::setCustomStartDay,
                    onSetCustomEnd = viewModel::setCustomEndDay,
                    onExport = viewModel::export
                )
            }
        }
    }
}

@Composable
private fun ExportLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(16.dp))
        Text("Preparing export...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportEmpty(onGoToImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.UploadFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text("Nothing to export", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            "Import data to unlock exports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(24.dp))
        Button(onClick = onGoToImport) {
            Text("Go to Import")
        }
    }
}

@Composable
private fun ExportError(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            // I keep the error icon local-looking here because export failures are file issues, not cloud issues.
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text("Unable to export", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.size(16.dp))
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun ExportContent(
    model: ExportUiModel,
    isExporting: Boolean,
    onGoToImport: () -> Unit,
    onSetType: (ExportType) -> Unit,
    onSetPreset: (ExportRangePreset) -> Unit,
    onSetCustomStart: (String) -> Unit,
    onSetCustomEnd: (String) -> Unit,
    onExport: () -> Unit
) {
    val (startDay, endDay) = model.resolvedRangeDays()
    val rangeLabel = remember(startDay, endDay) {
        when {
            startDay.isNullOrBlank() || endDay.isNullOrBlank() -> "\u2014"
            startDay == endDay -> formatDayShort(startDay)
            else -> "${formatDayShort(startDay)}\u2013${formatDayShort(endDay)}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text("Export", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Generate an export file from your measurements",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("What to export", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        onClick = { onSetType(ExportType.RawMeasurements) },
                        selected = model.exportType == ExportType.RawMeasurements,
                        label = { Text("Raw") }
                    )
                    FilterChip(
                        onClick = { onSetType(ExportType.DailySummaries) },
                        selected = model.exportType == ExportType.DailySummaries,
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        onClick = { onSetType(ExportType.AnalysisReport) },
                        selected = model.exportType == ExportType.AnalysisReport,
                        label = { Text("Analysis") }
                    )
                    FilterChip(
                        onClick = { onSetType(ExportType.ResultsPack) },
                        selected = model.exportType == ExportType.ResultsPack,
                        label = { Text("Results Pack") }
                    )
                    FilterChip(
                        onClick = { onSetType(ExportType.HowfarUf2) },
                        selected = model.exportType == ExportType.HowfarUf2,
                        label = { Text("HowFar UF2") }
                    )
                }
                Text(
                    when (model.exportType) {
                        ExportType.RawMeasurements -> "Timestamped rows (distance, lux)"
                        ExportType.DailySummaries -> "Per-day aggregates (avg/min/max + first/last)"
                        ExportType.AnalysisReport -> "Per-day analysis (D\u00B7h, low-light minutes, preprocessing stats)"
                        ExportType.ResultsPack -> "Exports 3 CSVs for thesis tables."
                        ExportType.HowfarUf2 -> "Exports the latest raw HowFar UF2 import."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (model.exportType == ExportType.ResultsPack) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Results pack notes", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Manifest: ${ResultsPackCsvBuilder.manifestFilename} inside the ZIP. It records the date range, app version, and settings used.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (model.exportType != ExportType.HowfarUf2) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Date range", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            onClick = { onSetPreset(ExportRangePreset.Last1Day) },
                            selected = model.preset == ExportRangePreset.Last1Day,
                            label = { Text("Last 1") }
                        )
                        FilterChip(
                            onClick = { onSetPreset(ExportRangePreset.Last7Days) },
                            selected = model.preset == ExportRangePreset.Last7Days,
                            label = { Text("Last 7") }
                        )
                        FilterChip(
                            onClick = { onSetPreset(ExportRangePreset.AllAvailable) },
                            selected = model.preset == ExportRangePreset.AllAvailable,
                            label = { Text("All") }
                        )
                        FilterChip(
                            onClick = { onSetPreset(ExportRangePreset.Custom) },
                            selected = model.preset == ExportRangePreset.Custom,
                            label = { Text("Custom") }
                        )
                    }

                    Text(
                        "Selected: $rangeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (model.preset == ExportRangePreset.Custom) {
                        HorizontalDivider()
                        CustomRangePicker(
                            days = model.availableDaysAsc,
                            startDay = model.customStartDay ?: model.availableDaysAsc.first(),
                            endDay = model.customEndDay ?: model.availableDaysAsc.last(),
                            onSetStart = onSetCustomStart,
                            onSetEnd = onSetCustomEnd
                        )
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Export", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Saves a CSV (or raw UF2) via the system file picker.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onExport,
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Building CSV\u2026")
                    } else {
                        Text(if (model.exportType == ExportType.HowfarUf2) "Save UF2" else "Save CSV")
                    }
                }
                Button(
                    onClick = onGoToImport,
                    enabled = !isExporting
                ) {
                    Text("Go to Import")
                }
            }
        }
    }
}

@Composable
private fun CustomRangePicker(
    days: List<String>,
    startDay: String,
    endDay: String,
    onSetStart: (String) -> Unit,
    onSetEnd: (String) -> Unit
) {
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.size(6.dp))
            AssistChip(
                onClick = { startExpanded = true },
                label = { Text(formatDayShort(startDay)) }
            )
            DropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                days.forEach { day ->
                    DropdownMenuItem(
                        text = { Text(formatDayLong(day)) },
                        onClick = {
                            onSetStart(day)
                            startExpanded = false
                        }
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("End", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.size(6.dp))
            AssistChip(
                onClick = { endExpanded = true },
                label = { Text(formatDayShort(endDay)) }
            )
            DropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                days.forEach { day ->
                    DropdownMenuItem(
                        text = { Text(formatDayLong(day)) },
                        onClick = {
                            onSetEnd(day)
                            endExpanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun formatDayLong(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }.getOrDefault(day)

private fun formatDayShort(day: String): String =
    runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(day)

private fun writeTextToUri(context: Context, uri: Uri, text: String): Result<Unit> {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream.")
    }
}

private fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray): Result<Unit> {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IllegalStateException("Unable to open output stream.")
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    val model = ExportUiModel(
        availableDaysAsc = listOf("2025-12-16", "2025-12-17", "2025-12-18"),
        exportType = ExportType.RawMeasurements,
        preset = ExportRangePreset.Last7Days,
        customStartDay = "2025-12-16",
        customEndDay = "2025-12-18"
    )
    NearworkTheme {
        ExportContent(
            model = model,
            isExporting = false,
            onGoToImport = {},
            onSetType = {},
            onSetPreset = {},
            onSetCustomStart = {},
            onSetCustomEnd = {},
            onExport = {}
        )
    }
}





















