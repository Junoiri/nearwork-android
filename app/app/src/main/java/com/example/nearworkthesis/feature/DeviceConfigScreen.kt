package com.example.nearworkthesis.feature

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalDeviceConfigRepository
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.domain.device.DeviceConnectionState
import com.example.nearworkthesis.domain.device.DeviceSettingsField
import com.example.nearworkthesis.domain.device.DeviceSettingsValidator
import com.example.nearworkthesis.domain.device.DeviceTimeMode

@Composable
fun DeviceConfigScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    backSignal: Int = 0,
    onConfirmExit: () -> Unit = {},
    onGoToImport: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val repository = LocalDeviceConfigRepository.current
    val viewModel: DeviceConfigViewModel = viewModel(factory = DeviceConfigViewModel.factory(repository))

    val connection by viewModel.connectionState.collectAsState()
    val state by viewModel.uiState.collectAsState()

    var pendingConfigBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingConfigFilename by remember { mutableStateOf<String?>(null) }
    var baselineForm by remember { mutableStateOf<DeviceConfigForm?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingExitAfterSave by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val createConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val bytes = pendingConfigBytes
        val filename = pendingConfigFilename
        pendingConfigBytes = null
        pendingConfigFilename = null

        if (uri == null) {
            scope.launch { snackbarHostState.showSnackbar("Save cancelled.") }
            return@rememberLauncherForActivityResult
        }
        if (bytes == null || filename == null) {
            scope.launch { snackbarHostState.showSnackbar("Config payload missing. Please retry.") }
            return@rememberLauncherForActivityResult
        }

        val result = writeBytesToUri(context, uri, bytes)
        result.fold(
            onSuccess = { scope.launch { snackbarHostState.showSnackbar("Saved $filename") } },
            onFailure = { t -> scope.launch { snackbarHostState.showSnackbar(t.message ?: "Unable to save UF2.") } }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeviceConfigEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is DeviceConfigEvent.LaunchSaveConfig -> {
                    pendingConfigBytes = event.bytes
                    pendingConfigFilename = event.filename
                    createConfigLauncher.launch(event.filename)
                }
            }
        }
    }

    val currentForm = when (val current = state) {
        is DeviceConfigUiState.Ready -> current.form
        is DeviceConfigUiState.Error -> current.form
        else -> null
    }
    val isDirty = baselineForm != null && currentForm != null && currentForm != baselineForm

    LaunchedEffect(currentForm, isDirty) {
        if (currentForm != null && !isDirty) {
            baselineForm = currentForm
        }
    }

    LaunchedEffect(state, pendingExitAfterSave) {
        val ready = state as? DeviceConfigUiState.Ready
        if (pendingExitAfterSave && ready != null && !ready.isBusy) {
            pendingExitAfterSave = false
            (pendingExitAction ?: onConfirmExit).also { pendingExitAction = null }.invoke()
        }
    }

    fun requestExit(action: (() -> Unit)? = null) {
        val target = action ?: onConfirmExit
        if (isDirty) {
            pendingExitAction = target
            showExitConfirm = true
        } else {
            target()
        }
    }

    BackHandler { requestExit() }

    LaunchedEffect(backSignal) {
        if (backSignal > 0) {
            requestExit()
        }
    }

    val isBusyNow = (state as? DeviceConfigUiState.Ready)?.isBusy == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Device config", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        viewModel.refreshConnection()
                        viewModel.refreshFromDevice()
                    },
                    enabled = !isBusyNow
                ) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh device status")
                }
                if (isDirty) {
                    TextButton(
                        onClick = { viewModel.applySettings() },
                        enabled = connection == DeviceConnectionState.Connected && !isBusyNow
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
        ConnectionBanner(connection = connection)
        Text(
            text = if (connection == DeviceConnectionState.Connected) {
                "Connected - Changes enabled"
            } else {
                "Disconnected - Changes disabled"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TextButton(onClick = { requestExit(onGoToImport) }) {
            Text("Select HowFar storage (opens Import)")
        }

        when (state) {
            is DeviceConfigUiState.Loading -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Reading device settings...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is DeviceConfigUiState.Error -> {
                val error = state as DeviceConfigUiState.Error
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text("Device config error", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            error.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.retryLastError() }) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Retry")
                            }
                            OutlinedButton(onClick = onGoToImport) {
                                Text("Go to Import")
                            }
                        }
                    }
                }

                error.form?.let { form ->
                    DeviceConfigFormContent(
                        state = DeviceConfigUiState.Ready(form, error.validationErrors, isBusy = false),
                        isEditable = connection == DeviceConnectionState.Connected,
                        onRead = { viewModel.refreshFromDevice() },
                        onApply = { viewModel.applySettings() },
                        onExport = { viewModel.exportConfigUf2() },
                        onClearDeviceData = { viewModel.clearDeviceData() },
                        onReset = { viewModel.resetToDefaults() },
                        onUpdateSampling = viewModel::updateSamplingIntervalSeconds,
                        onUpdateShutdown = viewModel::updateAutoShutdownMinutes,
                        onUpdateLux = viewModel::updateLowLightLuxThreshold,
                        onToggleLowPower = viewModel::updateLowPowerMode,
                        onSetTimeMode = viewModel::updateTimeMode
                    )
                }
            }

            is DeviceConfigUiState.Ready -> {
                DeviceConfigFormContent(
                    state = state as DeviceConfigUiState.Ready,
                    isEditable = connection == DeviceConnectionState.Connected,
                    onRead = { viewModel.refreshFromDevice() },
                    onApply = { viewModel.applySettings() },
                    onExport = { viewModel.exportConfigUf2() },
                    onClearDeviceData = { viewModel.clearDeviceData() },
                    onReset = { viewModel.resetToDefaults() },
                    onUpdateSampling = viewModel::updateSamplingIntervalSeconds,
                    onUpdateShutdown = viewModel::updateAutoShutdownMinutes,
                    onUpdateLux = viewModel::updateLowLightLuxThreshold,
                    onToggleLowPower = viewModel::updateLowPowerMode,
                    onSetTimeMode = viewModel::updateTimeMode
                )
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved device configuration changes.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        pendingExitAfterSave = true
                        viewModel.applySettings()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        (pendingExitAction ?: onConfirmExit).also { pendingExitAction = null }.invoke()
                    }
                ) { Text("Discard") }
            }
        )
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
private fun DeviceConfigScreenPreview() {
    NearworkTheme { DeviceConfigScreen(snackbarHostState = SnackbarHostState()) }
}

@Composable
private fun ConnectionBanner(connection: DeviceConnectionState) {
    val (label, colors, icon) = when (connection) {
        DeviceConnectionState.Disconnected -> Triple(
            "Disconnected",
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            Icons.Outlined.Cable
        )
        DeviceConnectionState.PermissionRequired -> Triple(
            "Permission required",
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            Icons.Outlined.Cable
        )
        DeviceConnectionState.Connecting -> Triple(
            "Connecting...",
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            Icons.Outlined.Cable
        )
        DeviceConnectionState.Connected -> Triple(
            "Connected",
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            Icons.Default.CheckCircle
        )
        is DeviceConnectionState.Error -> Triple(
            "Connection error",
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            Icons.Default.CloudOff
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Device connection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Direct read/write needs device storage;\nexport works without it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            AssistChip(
                onClick = {},
                label = { Text(label) },
                leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
                colors = colors
            )
        }
    }
}

@Composable
private fun DeviceConfigFormContent(
    state: DeviceConfigUiState.Ready,
    isEditable: Boolean,
    onRead: () -> Unit,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onClearDeviceData: () -> Unit,
    onReset: () -> Unit,
    onUpdateSampling: (Int) -> Unit,
    onUpdateShutdown: (Int) -> Unit,
    onUpdateLux: (Int) -> Unit,
    onToggleLowPower: (Boolean) -> Unit,
    onSetTimeMode: (DeviceTimeMode) -> Unit
) {
    val form = state.form
    val errors = state.validationErrors
    val isBusy = state.isBusy
    val isValid = errors.isEmpty() &&
        form.samplingIntervalSeconds in DeviceSettingsValidator.samplingIntervalMin..DeviceSettingsValidator.samplingIntervalMax &&
        form.autoShutdownMinutes in DeviceSettingsValidator.autoShutdownMin..DeviceSettingsValidator.autoShutdownMax &&
        form.lowLightLuxThreshold in DeviceSettingsValidator.lowLightLuxMin..DeviceSettingsValidator.lowLightLuxMax
    val controlsEnabled = isEditable && !isBusy

    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Sampling", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Controls how frequently the device records distance + illumination.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val samplingError = errors[DeviceSettingsField.SamplingIntervalSeconds]
            LabeledStepper(
                label = "Sampling interval (seconds)",
                value = form.samplingIntervalSeconds,
                min = DeviceSettingsValidator.samplingIntervalMin,
                max = DeviceSettingsValidator.samplingIntervalMax,
                enabled = controlsEnabled,
                errorText = samplingError,
                onChange = onUpdateSampling
            )
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Power", style = MaterialTheme.typography.titleMedium)
            }

            val shutdownError = errors[DeviceSettingsField.AutoShutdownMinutes]
            Text(
                "Auto shutdown is the device hibernate timer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LabeledStepper(
                label = "Auto shutdown (minutes)",
                value = form.autoShutdownMinutes,
                min = DeviceSettingsValidator.autoShutdownMin,
                max = DeviceSettingsValidator.autoShutdownMax,
                enabled = controlsEnabled,
                errorText = shutdownError,
                step = 5,
                onChange = onUpdateShutdown
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Low power mode", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Reduces device-side work between samples.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = form.enableLowPowerMode,
                    onCheckedChange = { onToggleLowPower(it) },
                    enabled = controlsEnabled
                )
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Light threshold", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Device-side threshold stored on device (separate from app analysis settings).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val luxError = errors[DeviceSettingsField.LowLightLuxThreshold]
            Text("Low-light lux threshold: ${form.lowLightLuxThreshold} lux", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = form.lowLightLuxThreshold.toFloat(),
                onValueChange = { onUpdateLux(it.toInt()) },
                valueRange = DeviceSettingsValidator.lowLightLuxMin.toFloat()..DeviceSettingsValidator.lowLightLuxMax.toFloat(),
                enabled = controlsEnabled
            )
            if (!luxError.isNullOrBlank()) {
                Text(luxError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Device time", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sets the timestamp in the settings UF2 (phone time) or leaves device time unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeModeChip(
                    selected = form.deviceTimeMode == DeviceTimeMode.USE_PHONE_TIME_UTC,
                    label = "Use phone (UTC)",
                    enabled = controlsEnabled,
                    onClick = { onSetTimeMode(DeviceTimeMode.USE_PHONE_TIME_UTC) }
                )
                TimeModeChip(
                    selected = form.deviceTimeMode == DeviceTimeMode.KEEP_DEVICE_TIME,
                    label = "Keep device time",
                    enabled = controlsEnabled,
                    onClick = { onSetTimeMode(DeviceTimeMode.KEEP_DEVICE_TIME) }
                )
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRead,
                    enabled = controlsEnabled
                ) {
                    Text("Read from device")
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onExport,
                enabled = controlsEnabled && isValid
            ) {
                Text("Export config UF2")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showClearDataConfirm = true },
                enabled = controlsEnabled && isValid
            ) {
                Text("Clear device data")
            }

            TextButton(
                onClick = { showResetConfirm = true },
                enabled = controlsEnabled
            ) {
                Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Reset to defaults")
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset device settings?") },
            text = { Text("This will overwrite the device configuration with default values.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onReset()
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear device data?") },
            text = {
                Text(
                    "This writes a one-time erase flag to optoconf.uf2 while keeping the current device settings. " +
                        "The device will clear its stored data and resume recording immediately after you disconnect."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataConfirm = false
                        onClearDeviceData()
                    }
                ) { Text("Write erase flag") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TimeModeChip(selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        colors = if (selected) {
            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    )
}

@Composable
private fun LabeledStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    errorText: String?,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { onChange((value - step).coerceAtLeast(min)) },
                enabled = enabled && value > min
            ) {
                Text("-", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { onChange((value + step).coerceAtMost(max)) },
                enabled = enabled && value < max
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (!errorText.isNullOrBlank()) {
            Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}










































