package com.example.nearworkthesis.feature

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.core.ui.components.AppScaffold
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.importing.queryDisplayName
import com.example.nearworkthesis.importing.readAllBytesWithLimit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onImported: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeviceConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val model by viewModel.uiModel.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val accentColors = importAccentColors()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val onImportedLatest by rememberUpdatedState(onImported)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showHowfarUf2Help by remember { mutableStateOf(false) }
    var dismissedTimeSyncWarning by remember { mutableStateOf(false) }
    var dismissedRtcDriftWarning by remember { mutableStateOf(false) }
    var showDeleteImportedDataDialog by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    val customAnchorDateTime = remember(model.customAnchorMillis) {
        Instant.ofEpochMilli(model.customAnchorMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    val cropStartDateTime = remember(model.cropStartMillis) {
        Instant.ofEpochMilli(model.cropStartMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    val cropEndDateTime = remember(model.cropEndMillis) {
        Instant.ofEpochMilli(model.cropEndMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    LaunchedEffect(model.lastResult) {
        dismissedTimeSyncWarning = false
        dismissedRtcDriftWarning = false
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            scope.launch { snackbarHostState.showSnackbar("File import cancelled.") }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val filename = queryDisplayName(context, uri) ?: "import.csv"
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    readAllBytesWithLimit(context, uri, maxBytes = 25 * 1024 * 1024)
                }
            }.getOrNull()

            if (bytes == null) {
                snackbarHostState.showSnackbar("Unable to read selected file.")
                return@launch
            }

            viewModel.importCsvBytes(filename = filename, bytes = bytes, sourceType = ImportSourceType.FILE)
        }
    }



    // Manual UF2 import matches howfar-read: user picks the UF2 file explicitly.
    val openHowfarUf2Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            scope.launch { snackbarHostState.showSnackbar("HowFar file selection cancelled.") }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val filename = queryDisplayName(context, uri) ?: "optodata.uf2"
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    readAllBytesWithLimit(context, uri, maxBytes = 25 * 1024 * 1024)
                }
            }.getOrNull()

            if (bytes == null) {
                snackbarHostState.showSnackbar("Unable to read selected UF2 file.")
                return@launch
            }

            viewModel.importHowfarUf2File(filename = filename, bytes = bytes)
        }
    }

    // Optional shortcut: select the device storage folder so we can auto-pick optodata.uf2 later.
    val openTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            scope.launch { snackbarHostState.showSnackbar("HowFar device selection cancelled.") }
            return@rememberLauncherForActivityResult
        }
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModel.setDeviceTreeUri(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshHowfar()
        viewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    AppScaffold(
        title = "Import",
        modifier = modifier,
        showBack = true,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Open settings")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Import data",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Import measurements from the HowFar device or from files.",
                style = MaterialTheme.typography.bodyMedium
            )

            HowfarStatusCard(
                howfar = model.howfar,
                isImporting = model.isImporting,
                onRefresh = { viewModel.refreshHowfar() },
                onPrimaryAction = { action ->
                    when (action) {
                        ImportHowfarPrimaryAction.SelectDevice -> openTreeLauncher.launch(null)
                        ImportHowfarPrimaryAction.ImportFromDevice -> viewModel.importFromHowfar()
                        ImportHowfarPrimaryAction.RetryDetection -> viewModel.refreshHowfar()
                    }
                },
                showPrimaryAction = false
            )
            if (model.howfar.primaryAction == ImportHowfarPrimaryAction.ImportFromDevice) {
                AdvancedSettingsHeader(
                    expanded = showAdvancedSettings,
                    onToggle = { showAdvancedSettings = !showAdvancedSettings }
                )
                if (showAdvancedSettings) {
                    ImportAnchorCard(
                        anchorMode = model.anchorMode,
                        customAnchorMillis = model.customAnchorMillis,
                        onSelectAnchorMode = viewModel::setAnchorMode,
                        onPickManualAnchor = {
                            showAnchorDateTimePicker(
                                context = context,
                                initialDateTime = customAnchorDateTime,
                                onDateTimeSelected = viewModel::setCustomAnchorMillis
                            )
                        }
                    )
                    ImportCropStartCard(
                        cropStartMode = model.cropStartMode,
                        cropStartMillis = model.cropStartMillis,
                        onSelectCropStartMode = viewModel::setCropStartMode,
                        onPickCropStart = {
                            showAnchorDateTimePicker(
                                context = context,
                                initialDateTime = cropStartDateTime,
                                onDateTimeSelected = viewModel::setCropStartMillis
                            )
                        }
                    )
                    ImportCropEndCard(
                        cropEndMode = model.cropEndMode,
                        cropEndMillis = model.cropEndMillis,
                        onSelectCropEndMode = viewModel::setCropEndMode,
                        onPickCropEnd = {
                            showAnchorDateTimePicker(
                                context = context,
                                initialDateTime = cropEndDateTime,
                                onDateTimeSelected = viewModel::setCropEndMillis
                            )
                        }
                    )
                    if (model.cropEndMode == ImportCropEndMode.Manual && model.anchorMode == ImportAnchorMode.Auto) {
                        InlineWarningCard(
                            title = "Check crop end date",
                            message = "Manual crop end is set but recording end time is on automatic - verify the crop end date matches the actual recording date."
                        )
                    }
                }
            }

            if (model.isImporting) {
                val label = model.importingLabel?.lowercase(Locale.getDefault()).orEmpty()
                StatusRow(
                    text = "Importing $label...",
                    showProgress = true
                )
            }

            model.howfar.primaryAction?.let { action ->
                if (action == ImportHowfarPrimaryAction.SelectDevice) {
                    Button(
                        onClick = { openTreeLauncher.launch(null) },
                        enabled = model.howfar.primaryActionEnabled && !model.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Periwinkle,
                            contentColor = Graphite
                        )
                    ) {
                        Text("Select HowFar storage", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        openDocumentLauncher.launch(arrayOf("text/csv", "text/*"))
                    },
                    enabled = !model.isImporting,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColors.accent,
                        contentColor = accentColors.onAccent
                    )
                ) {
                    Text(text = "Import from CSV", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        openHowfarUf2Launcher.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    enabled = !model.isImporting,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = 56.dp),
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColors.accent,
                        contentColor = accentColors.onAccent
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Import from UF2", fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { showHowfarUf2Help = !showHowfarUf2Help },
                            enabled = !model.isImporting,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "HowFar UF2 help",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    when (model.howfar.primaryAction) {
                        ImportHowfarPrimaryAction.ImportFromDevice -> viewModel.importFromHowfar()
                        ImportHowfarPrimaryAction.RetryDetection -> viewModel.refreshHowfar()
                        else -> Unit
                    }
                },
                enabled = model.howfar.primaryAction == ImportHowfarPrimaryAction.ImportFromDevice &&
                    model.howfar.primaryActionEnabled &&
                    !model.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import from HowFar device")
            }

            if (showHowfarUf2Help) {
                Text(
                    text = "Pick the UF2 data file from the device (e.g., optodata.uf2 or PATIENT0.UF2).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            model.howfar.primaryAction?.let { action ->
                if (action == ImportHowfarPrimaryAction.RetryDetection) {
                    Button(
                        onClick = { viewModel.refreshHowfar() },
                        enabled = model.howfar.primaryActionEnabled && !model.isImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry HowFar detection")
                    }
                }
            }

            model.lastResult?.let { last ->
                val deviceTimeUnset = isDeviceTimeUnset(last.summary)
                if (deviceTimeUnset && !dismissedTimeSyncWarning) {
                    TimeSyncWarningCard(
                        phoneTimestamp = summaryFormatter.format(Instant.now()),
                        deviceTimestamp = last.summary.lastTimestampEpochMillis?.let { summaryFormatter.format(Instant.ofEpochMilli(it)) }
                            ?: "Unknown",
                        onSyncNow = onOpenDeviceConfig,
                        onProceed = {
                            dismissedTimeSyncWarning = true
                        }
                    )
                }
                val showRtcDriftWarning = last.outcome == ImportOutcome.Success &&
                    shouldShowRtcDriftWarning(last.summary) &&
                    !dismissedRtcDriftWarning
                ImportReportCard(
                    last = last,
                    showRtcDriftWarning = showRtcDriftWarning,
                    onCopy = {
                        val report = formatSummary(last.summary)
                        scope.launch {
                            clipboardManager.setText(AnnotatedString(report))
                            snackbarHostState.showSnackbar("Report copied to clipboard.")
                        }
                    },
                    onDismiss = { viewModel.clearLastResult() },
                    onGoToDaily = {
                        viewModel.clearLastResult()
                        onImportedLatest()
                    },
                    onDeleteImportedData = { showDeleteImportedDataDialog = true },
                    onDismissRtcDriftWarning = { dismissedRtcDriftWarning = true }
                )
            }
        }
    }

    if (dialogState is ImportDialogState.Error) {
        val errorState = dialogState as ImportDialogState.Error
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text(text = "Import failed") },
            text = { Text(text = errorState.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text(text = "Try again")
                }
            }
        )
    }

    if (showDeleteImportedDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteImportedDataDialog = false },
            title = { Text("Delete imported data?") },
            text = { Text("This removes the imported measurements for the affected day range from the active profile.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteImportedDataDialog = false
                        viewModel.deleteImportedDays()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImportedDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun HowfarStatusCard(
    howfar: ImportHowfarUiModel,
    isImporting: Boolean,
    onRefresh: () -> Unit,
    onPrimaryAction: (ImportHowfarPrimaryAction) -> Unit,
    showPrimaryAction: Boolean = true
) {
    val accentColors = importAccentColors()
    Card(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            tint = accentColors.accent
                        )
                        Text(
                            "HowFar storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        howfar.statusTitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        howfar.statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            val statusLabel = when {
                                isImporting -> "Busy"
                                howfar.statusTitle.contains("Ready") -> "Ready"
                                howfar.statusTitle.contains("Not connected") -> "Unavailable"
                                else -> "Disconnected"
                            }
                            Text(statusLabel)
                        }
                    )
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isImporting
                    ) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh HowFar status")
                    }
                }
            }

            if (showPrimaryAction) {
                howfar.primaryAction?.let { action ->
                val label = when (action) {
                    ImportHowfarPrimaryAction.SelectDevice -> "Select HowFar storage"
                    ImportHowfarPrimaryAction.ImportFromDevice -> "Import from device"
                    ImportHowfarPrimaryAction.RetryDetection -> "Retry detection"
                }
                Button(
                    onClick = { onPrimaryAction(action) },
                    enabled = howfar.primaryActionEnabled && !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(label)
                }
            }
            }
        }
    }
}

@Composable
internal fun StatusRow(
    text: String,
    showProgress: Boolean = false
) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportReportCard(
    last: ImportLastResult,
    showRtcDriftWarning: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    onGoToDaily: () -> Unit,
    onDeleteImportedData: () -> Unit,
    onDismissRtcDriftWarning: () -> Unit
) {
    val title = when (last.outcome) {
        ImportOutcome.Success -> "Import complete"
        ImportOutcome.NoNewData -> "No new data"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Validation + preprocessing report",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(onClick = onDismiss, label = { Text("Dismiss") })
            }

            Text(
                text = formatSummary(last.summary),
                style = MaterialTheme.typography.bodyMedium
            )

            if (showRtcDriftWarning) {
                InlineWarningCard(
                    title = "RTC drift warning",
                    message = "The last recorded measurement is more than 48 hours old. Consider resyncing the HowFar clock using howfar-conf.",
                    dismissLabel = "Dismiss",
                    onDismiss = onDismissRtcDriftWarning
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onCopy
                ) {
                    Text("Copy report")
                }
                if (last.outcome == ImportOutcome.Success) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onGoToDaily
                    ) {
                        Text("Go to Daily")
                    }
                }
            }
            if (last.outcome == ImportOutcome.Success) {
                OutlinedButton(
                    onClick = onDeleteImportedData,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete imported data")
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettingsHeader(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Advanced settings",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse advanced settings" else "Expand advanced settings"
                )
            }
        }
    }
}

@Composable
private fun ImportAnchorCard(
    anchorMode: ImportAnchorMode,
    customAnchorMillis: Long,
    onSelectAnchorMode: (ImportAnchorMode) -> Unit,
    onPickManualAnchor: () -> Unit
) {
    val nowText = timestampPickerFormatter.format(LocalDateTime.now().atZone(ZoneId.systemDefault()))
    val manualText = remember(customAnchorMillis) {
        timestampPickerFormatter.format(Instant.ofEpochMilli(customAnchorMillis).atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Recording end time", style = MaterialTheme.typography.titleMedium)
            Text(
                "The most recent measurement will be anchored to this time. Use the current time if you're importing immediately after recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = anchorMode == ImportAnchorMode.Auto,
                    onClick = { onSelectAnchorMode(ImportAnchorMode.Auto) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Current time (auto)")
                }
                SegmentedButton(
                    selected = anchorMode == ImportAnchorMode.Manual,
                    onClick = { onSelectAnchorMode(ImportAnchorMode.Manual) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Set manually")
                }
            }
            if (anchorMode == ImportAnchorMode.Auto) {
                Text(
                    text = "Will use: $nowText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Selected: $manualText",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onPickManualAnchor) {
                    Text("Choose date and time")
                }
            }
        }
    }
}

@Composable
private fun ImportCropStartCard(
    cropStartMode: ImportCropStartMode,
    cropStartMillis: Long,
    onSelectCropStartMode: (ImportCropStartMode) -> Unit,
    onPickCropStart: () -> Unit
) {
    val manualText = remember(cropStartMillis) {
        timestampPickerFormatter.format(Instant.ofEpochMilli(cropStartMillis).atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Recording start time", style = MaterialTheme.typography.titleMedium)
            Text(
                "Discard measurements before this time. Enable this when the device flash still contains older sessions you do not want to import.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = cropStartMode == ImportCropStartMode.Disabled,
                    onClick = { onSelectCropStartMode(ImportCropStartMode.Disabled) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Disabled")
                }
                SegmentedButton(
                    selected = cropStartMode == ImportCropStartMode.Manual,
                    onClick = { onSelectCropStartMode(ImportCropStartMode.Manual) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Set manually")
                }
            }
            if (cropStartMode == ImportCropStartMode.Disabled) {
                Text(
                    text = "All earlier measurements will be kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Selected: $manualText",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onPickCropStart) {
                    Text("Choose date and time")
                }
            }
        }
    }
}

@Composable
private fun ImportCropEndCard(
    cropEndMode: ImportCropEndMode,
    cropEndMillis: Long,
    onSelectCropEndMode: (ImportCropEndMode) -> Unit,
    onPickCropEnd: () -> Unit
) {
    val manualText = remember(cropEndMillis) {
        timestampPickerFormatter.format(Instant.ofEpochMilli(cropEndMillis).atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Crop end time", style = MaterialTheme.typography.titleMedium)
            Text(
                "Discard measurements after this time. Enable this when the file extends beyond the session you want to keep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = cropEndMode == ImportCropEndMode.Auto,
                    onClick = { onSelectCropEndMode(ImportCropEndMode.Auto) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Disabled")
                }
                SegmentedButton(
                    selected = cropEndMode == ImportCropEndMode.Manual,
                    onClick = { onSelectCropEndMode(ImportCropEndMode.Manual) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Set manually")
                }
            }
            if (cropEndMode == ImportCropEndMode.Auto) {
                Text(
                    text = "All later measurements will be kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Selected: $manualText",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onPickCropEnd) {
                    Text("Choose date and time")
                }
            }
        }
    }
}

@Composable
private fun InlineWarningCard(
    title: String,
    message: String,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            if (dismissLabel != null && onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            }
        }
    }
}

@Composable
private fun TimeSyncWarningCard(
    phoneTimestamp: String,
    deviceTimestamp: String,
    onSyncNow: () -> Unit,
    onProceed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Time sync with HowFar is advised",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "The timestamps look far in the past. Syncing time prevents incorrect dates in the calendar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Phone time: $phoneTimestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Device last record: $deviceTimestamp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSyncNow
                ) {
                    Text("Sync time now")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onProceed
                ) {
                    Text("Proceed without sync")
                }
            }
        }
    }
}

private fun isDeviceTimeUnset(summary: ImportSummary): Boolean {
    return summary.firstTimestampEpochMillis?.let { it < OLDEST_REASONABLE_EPOCH_MILLIS } == true
}

@Preview(showBackground = true)
@Composable
private fun ImportScreenPreview() {
    NearworkTheme {
        HowfarStatusCard(
            howfar = ImportHowfarUiModel(
                statusTitle = "HowFar: Ready",
                statusSubtitle = "Storage: HOWFAR",
                primaryAction = ImportHowfarPrimaryAction.ImportFromDevice,
                primaryActionEnabled = true
            ),
            isImporting = false,
            onRefresh = {},
            onPrimaryAction = { _ -> }
        )
    }
}

private val summaryFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
private val timestampPickerFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private const val OLDEST_REASONABLE_EPOCH_MILLIS = 1262304000000L

@Composable
private fun importAccentColors(): ImportAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        ImportAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        ImportAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class ImportAccentColors(
    val accent: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color
)

private fun showAnchorDateTimePicker(
    context: Context,
    initialDateTime: LocalDateTime,
    onDateTimeSelected: (Long) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selectedDateTime = LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                    onDateTimeSelected(selectedDateTime.atZone(zoneId).toInstant().toEpochMilli())
                },
                initialDateTime.hour,
                initialDateTime.minute,
                true
            ).show()
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth
    ).show()
}

private fun formatSummary(summary: ImportSummary): String {
    val first = summary.firstTimestampEpochMillis?.let { summaryFormatter.format(Instant.ofEpochMilli(it)) }
    val last = summary.lastTimestampEpochMillis?.let { summaryFormatter.format(Instant.ofEpochMilli(it)) }
    return buildString {
        appendLine("Source: ${summary.sourceType}")
        appendLine("File: ${summary.filename}")
        appendLine("Rows read: ${summary.totalRows}")
        appendLine("Inserted: ${summary.insertedRows}")
        append("Rejected: ${summary.rejectedRows}")
        appendLine()
        append("Duplicate policy: ${summary.duplicateResolutionPolicy.displayLabel}")
        if (summary.duplicatesEncounteredCount > 0) {
            appendLine()
            append("Duplicates encountered: ${summary.duplicatesEncounteredCount}")
            when (summary.duplicateResolutionPolicy) {
                DuplicateResolutionPolicy.KEEP_EXISTING -> {
                    appendLine()
                    append("Duplicates kept: ${summary.duplicatesKeptCount}")
                }
                DuplicateResolutionPolicy.REPLACE_WITH_NEW -> {
                    appendLine()
                    append("Duplicates replaced: ${summary.duplicatesReplacedCount}")
                }
            }
        }
        if (
            summary.invalidTimestampCount +
            summary.invalidDistanceCount +
            summary.invalidLuxCount +
            summary.croppedByTimeWindowCount +
            summary.croppedByEndWindowCount > 0
        ) {
            appendLine()
            appendLine("Rejection reasons:")
            appendLine("- invalid timestamp: ${summary.invalidTimestampCount}")
            appendLine("- invalid distance: ${summary.invalidDistanceCount}")
            appendLine("- invalid lux: ${summary.invalidLuxCount}")
            appendLine("- cropped before start window: ${summary.croppedByTimeWindowCount}")
            append("- cropped after end window: ${summary.croppedByEndWindowCount}")
        }
        if (summary.gapCount > 0) {
            appendLine()
            append("Gaps detected: ${summary.gapCount}")
            summary.largestGapDurationMillis?.let { largest ->
                appendLine()
                append("Largest gap: ${formatDurationMillis(largest)}")
            }
        }
        val deviceTimeUnset = isDeviceTimeUnset(summary)
        if (deviceTimeUnset) {
            appendLine()
            append("Note: device time looks unset. Sync time in Device Config for accurate dates.")
        }
        first?.let { appendLine().append("First timestamp: $it") }
        last?.let { appendLine().append("Last timestamp: $it") }
    }
}

private fun shouldShowRtcDriftWarning(summary: ImportSummary): Boolean {
    if (summary.sourceType != ImportSourceType.HOWFAR_USB) return false
    if (isDeviceTimeUnset(summary)) return false
    val lastTimestamp = summary.lastTimestampEpochMillis ?: return false
    val driftMillis = kotlin.math.abs(System.currentTimeMillis() - lastTimestamp)
    return driftMillis > RTC_DRIFT_WARNING_THRESHOLD_MILLIS
}

private fun formatDurationMillis(durationMillis: Long): String {
    if (durationMillis <= 0L) return "0s"
    val seconds = durationMillis / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private const val RTC_DRIFT_WARNING_THRESHOLD_MILLIS = 48L * 60L * 60L * 1000L






























