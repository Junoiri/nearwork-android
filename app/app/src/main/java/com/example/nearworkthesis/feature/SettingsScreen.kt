package com.example.nearworkthesis.feature

import android.Manifest
import android.app.TimePickerDialog
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalDemoRepository
import com.example.nearworkthesis.app.LocalNotificationScheduler
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.app.LocalSettingsStore
import com.example.nearworkthesis.core.ui.UiTestTags
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.settings.AnalysisSettingsExchange
import com.example.nearworkthesis.settings.PortableAnalysisSettings
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    backSignal: Int = 0,
    openNotificationsSection: Boolean = false,
    onConfirmExit: () -> Unit = {},
    onOpenDeviceConfig: () -> Unit = {},
    onOpenMethodsAssumptions: () -> Unit = {},
    onOpenAboutResearch: () -> Unit = {}
) {
    val settingsStore = LocalSettingsStore.current
    val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SettingsViewModel.factory(settingsStore)
    )
    val settingsState by settingsViewModel.uiState.collectAsState()
    val notificationScheduler = LocalNotificationScheduler.current
    val demoRepository = LocalDemoRepository.current
    val profileRepository = LocalProfileRepository.current
    val activeProfileStore = LocalActiveProfileStore.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val lowLightThresholdLux = settingsState.lowLightThresholdLux
    val nearworkThresholdCm = settingsState.nearworkDistanceThresholdCm
    val breakGapSeconds = settingsState.breakGapSeconds
    val minSessionDurationSeconds = settingsState.minSessionDurationSeconds
    val closeDistanceThresholdCm = settingsState.closeDistanceThresholdCm
    val extremeCloseThresholdCm = settingsState.extremeCloseThresholdCm
    val replaceAlsSingleSampleSpikes = settingsState.replaceAlsSingleSampleSpikes
    val alsSpikeThresholdLux = settingsState.alsSpikeThresholdLux
    val showDebugOverlay = settingsState.showDebugOverlay
    val lastDemoProfileId = settingsState.lastDemoProfileId
    val dailyReminderEnabled = settingsState.dailyReminderEnabled
    val dailyReminderTimeLocal = settingsState.dailyReminderTimeLocal
    val postImportNotificationEnabled = settingsState.postImportNotificationEnabled
    val duplicateResolutionPolicy = settingsState.duplicateResolutionPolicy

    val profiles by profileRepository.observeProfiles().collectAsState(initial = emptyList())
    val activeProfileId by activeProfileStore.observeActiveProfileId().collectAsState(initial = null)
    val activeProfileName = remember(profiles, activeProfileId) {
        profiles.firstOrNull { it.id == activeProfileId }?.name ?: profiles.firstOrNull()?.name ?: "Profile"
    }

    var baseLowLight by remember { mutableStateOf(lowLightThresholdLux) }
    var baseNearworkThreshold by remember { mutableStateOf(nearworkThresholdCm) }
    var baseBreakGapSeconds by remember { mutableStateOf(breakGapSeconds) }
    var baseMinSessionDurationSeconds by remember { mutableStateOf(minSessionDurationSeconds) }
    var baseCloseDistance by remember { mutableStateOf(closeDistanceThresholdCm) }
    var baseExtremeClose by remember { mutableStateOf(extremeCloseThresholdCm) }
    var baseReplaceAlsSpikes by remember { mutableStateOf(replaceAlsSingleSampleSpikes) }
    var baseAlsSpikeThresholdLux by remember { mutableStateOf(alsSpikeThresholdLux) }
    var baseShowDebugOverlay by remember { mutableStateOf(showDebugOverlay) }
    var baseDailyReminderEnabled by remember { mutableStateOf(dailyReminderEnabled) }
    var baseDailyReminderTime by remember { mutableStateOf(dailyReminderTimeLocal) }
    var basePostImportNotificationEnabled by remember { mutableStateOf(postImportNotificationEnabled) }
    var baseDuplicatePolicy by remember { mutableStateOf(duplicateResolutionPolicy) }

    var draftLowLight by remember { mutableStateOf(lowLightThresholdLux) }
    var draftNearworkThreshold by remember { mutableStateOf(nearworkThresholdCm) }
    var draftBreakGapSeconds by remember { mutableStateOf(breakGapSeconds) }
    var draftMinSessionDurationSeconds by remember { mutableStateOf(minSessionDurationSeconds) }
    var draftCloseDistance by remember { mutableStateOf(closeDistanceThresholdCm) }
    var draftExtremeClose by remember { mutableStateOf(extremeCloseThresholdCm) }
    var draftReplaceAlsSpikes by remember { mutableStateOf(replaceAlsSingleSampleSpikes) }
    var draftAlsSpikeThresholdLux by remember { mutableStateOf(alsSpikeThresholdLux) }
    var draftShowDebugOverlay by remember { mutableStateOf(showDebugOverlay) }
    var draftDailyReminderEnabled by remember { mutableStateOf(dailyReminderEnabled) }
    var draftDailyReminderTime by remember { mutableStateOf(dailyReminderTimeLocal) }
    var draftPostImportNotificationEnabled by remember { mutableStateOf(postImportNotificationEnabled) }
    var draftDuplicatePolicy by remember { mutableStateOf(duplicateResolutionPolicy) }

    var lowLightText by remember { mutableStateOf(lowLightThresholdLux.toString()) }
    var nearworkThresholdText by remember { mutableStateOf(nearworkThresholdCm.toString()) }
    var breakGapSecondsText by remember { mutableStateOf(breakGapSeconds.toString()) }
    var minSessionDurationText by remember { mutableStateOf(minSessionDurationSeconds.toString()) }
    var closeDistanceText by remember { mutableStateOf(closeDistanceThresholdCm.toString()) }
    var extremeCloseText by remember { mutableStateOf(extremeCloseThresholdCm.toString()) }
    var alsSpikeThresholdText by remember { mutableStateOf(format1(alsSpikeThresholdLux)) }
    var pendingApply by remember { mutableStateOf(false) }
    var pendingSnapshot by remember { mutableStateOf<SettingsSnapshot?>(null) }
    val thresholdOrderingError = remember(draftCloseDistance, draftExtremeClose) {
        SettingsViewModel.validateThresholdOrdering(
            closeDistanceThresholdCm = draftCloseDistance,
            extremeCloseThresholdCm = draftExtremeClose
        )
    }

    val isDirty = draftLowLight != baseLowLight ||
        draftNearworkThreshold != baseNearworkThreshold ||
        draftBreakGapSeconds != baseBreakGapSeconds ||
        draftMinSessionDurationSeconds != baseMinSessionDurationSeconds ||
        draftCloseDistance != baseCloseDistance ||
        draftExtremeClose != baseExtremeClose ||
        draftReplaceAlsSpikes != baseReplaceAlsSpikes ||
        draftAlsSpikeThresholdLux != baseAlsSpikeThresholdLux ||
        draftShowDebugOverlay != baseShowDebugOverlay ||
        draftDailyReminderEnabled != baseDailyReminderEnabled ||
        draftDailyReminderTime != baseDailyReminderTime ||
        draftPostImportNotificationEnabled != basePostImportNotificationEnabled ||
        draftDuplicatePolicy != baseDuplicatePolicy

    LaunchedEffect(
        lowLightThresholdLux,
        nearworkThresholdCm,
        breakGapSeconds,
        minSessionDurationSeconds,
        closeDistanceThresholdCm,
        extremeCloseThresholdCm,
        replaceAlsSingleSampleSpikes,
        alsSpikeThresholdLux,
        showDebugOverlay,
        dailyReminderEnabled,
        dailyReminderTimeLocal,
        postImportNotificationEnabled,
        duplicateResolutionPolicy,
        isDirty
    ) {
        val currentSnapshot = SettingsSnapshot(
            lowLight = lowLightThresholdLux,
            nearworkThreshold = nearworkThresholdCm,
            breakGapSeconds = breakGapSeconds,
            minSessionDurationSeconds = minSessionDurationSeconds,
            closeDistance = closeDistanceThresholdCm,
            extremeClose = extremeCloseThresholdCm,
            replaceAlsSpikes = replaceAlsSingleSampleSpikes,
            alsSpikeThresholdLux = alsSpikeThresholdLux,
            showDebugOverlay = showDebugOverlay,
            dailyReminderEnabled = dailyReminderEnabled,
            dailyReminderTime = dailyReminderTimeLocal,
            postImportNotificationEnabled = postImportNotificationEnabled,
            duplicatePolicy = duplicateResolutionPolicy
        )

        if (pendingApply && pendingSnapshot == currentSnapshot) {
            pendingApply = false
            pendingSnapshot = null
        }

        if (!isDirty && !pendingApply) {
            baseLowLight = lowLightThresholdLux
            baseNearworkThreshold = nearworkThresholdCm
            baseBreakGapSeconds = breakGapSeconds
            baseMinSessionDurationSeconds = minSessionDurationSeconds
            baseCloseDistance = closeDistanceThresholdCm
            baseExtremeClose = extremeCloseThresholdCm
            baseReplaceAlsSpikes = replaceAlsSingleSampleSpikes
            baseAlsSpikeThresholdLux = alsSpikeThresholdLux
            baseShowDebugOverlay = showDebugOverlay
            baseDailyReminderEnabled = dailyReminderEnabled
            baseDailyReminderTime = dailyReminderTimeLocal
            basePostImportNotificationEnabled = postImportNotificationEnabled
            baseDuplicatePolicy = duplicateResolutionPolicy

            draftLowLight = lowLightThresholdLux
            draftNearworkThreshold = nearworkThresholdCm
            draftBreakGapSeconds = breakGapSeconds
            draftMinSessionDurationSeconds = minSessionDurationSeconds
            draftCloseDistance = closeDistanceThresholdCm
            draftExtremeClose = extremeCloseThresholdCm
            draftReplaceAlsSpikes = replaceAlsSingleSampleSpikes
            draftAlsSpikeThresholdLux = alsSpikeThresholdLux
            draftShowDebugOverlay = showDebugOverlay
            draftDailyReminderEnabled = dailyReminderEnabled
            draftDailyReminderTime = dailyReminderTimeLocal
            draftPostImportNotificationEnabled = postImportNotificationEnabled
            draftDuplicatePolicy = duplicateResolutionPolicy
            lowLightText = lowLightThresholdLux.toString()
            nearworkThresholdText = nearworkThresholdCm.toString()
            breakGapSecondsText = breakGapSeconds.toString()
            minSessionDurationText = minSessionDurationSeconds.toString()
            closeDistanceText = closeDistanceThresholdCm.toString()
            extremeCloseText = extremeCloseThresholdCm.toString()
            alsSpikeThresholdText = format1(alsSpikeThresholdLux)
        }
    }

    var isDemoBusy by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var pendingNotificationToggle by remember { mutableStateOf<NotificationToggleTarget?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var pendingExitAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSettingsJson by remember { mutableStateOf<String?>(null) }
    var pendingSettingsFilename by remember { mutableStateOf<String?>(null) }
    var pendingImportedSettings by remember { mutableStateOf<PortableAnalysisSettings?>(null) }
    var pendingImportedSettingsLabel by remember { mutableStateOf<String?>(null) }
    var generalExpanded by remember(openNotificationsSection) { mutableStateOf(openNotificationsSection) }
    var researchExpanded by remember { mutableStateOf(false) }
    var infoExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(openNotificationsSection) {
        if (openNotificationsSection) {
            generalExpanded = true
            researchExpanded = false
            infoExpanded = false
        }
    }
    val accentColors = settingsAccentColors()

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingSettingsJson
        val filename = pendingSettingsFilename
        pendingSettingsJson = null
        pendingSettingsFilename = null

        if (uri == null) return@rememberLauncherForActivityResult
        if (json == null || filename == null) {
            scope.launch {
                snackbarHostState.showSnackbar("Settings export payload missing. Please retry.")
            }
            return@rememberLauncherForActivityResult
        }

        val result = writeSettingsTextToUri(context, uri, json)
        scope.launch {
            result.fold(
                onSuccess = { snackbarHostState.showSnackbar("Saved $filename") },
                onFailure = { t -> snackbarHostState.showSnackbar(t.message ?: "Unable to save settings file.") }
            )
        }
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val label = uri.lastPathSegment?.substringAfterLast('/') ?: "selected file"
        val result = runCatching {
            val json = readSettingsTextFromUri(context, uri)
            AnalysisSettingsExchange.parse(json)
        }
        result.onSuccess { imported ->
            pendingImportedSettings = imported
            pendingImportedSettingsLabel = label
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(throwable.message ?: "Unable to import settings file.")
            }
        }
    }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingNotificationToggle
        pendingNotificationToggle = null
        if (granted) {
            when (pending) {
                NotificationToggleTarget.DailyReminder -> {
                    settingsViewModel.setDailyReminderEnabled(true)
                    draftDailyReminderEnabled = true
                }
                NotificationToggleTarget.PostImport -> {
                    settingsViewModel.setPostImportNotificationEnabled(true)
                    draftPostImportNotificationEnabled = true
                }
                null -> Unit
            }
        } else {
            when (pending) {
                NotificationToggleTarget.DailyReminder -> draftDailyReminderEnabled = false
                NotificationToggleTarget.PostImport -> draftPostImportNotificationEnabled = false
                null -> Unit
            }
            scope.launch {
                snackbarHostState.showSnackbar("Notifications are disabled. Enable permissions to receive educational reminders.")
            }
        }
    }

    LaunchedEffect(Unit) {
        notificationScheduler.ensureChannels()
    }

    LaunchedEffect(dailyReminderEnabled, dailyReminderTimeLocal) {
        notificationScheduler.rescheduleDailyReminder()
    }

    val defaultDemoFile = "optodata_2026-06-07.csv"

    fun requestExit(action: (() -> Unit)? = null) {
        val target = action ?: onConfirmExit
        if (isDirty) {
            pendingExitAction = target
            showExitConfirm = true
        } else {
            target()
        }
    }

    fun applyDraftSettings(onComplete: (() -> Unit)? = null) {
        if (thresholdOrderingError != null) return
        pendingApply = true
        pendingSnapshot = SettingsSnapshot(
            lowLight = draftLowLight,
            nearworkThreshold = draftNearworkThreshold,
            breakGapSeconds = draftBreakGapSeconds,
            minSessionDurationSeconds = draftMinSessionDurationSeconds,
            closeDistance = draftCloseDistance,
            extremeClose = draftExtremeClose,
            replaceAlsSpikes = draftReplaceAlsSpikes,
            alsSpikeThresholdLux = draftAlsSpikeThresholdLux,
            showDebugOverlay = draftShowDebugOverlay,
            dailyReminderEnabled = draftDailyReminderEnabled,
            dailyReminderTime = draftDailyReminderTime,
            postImportNotificationEnabled = draftPostImportNotificationEnabled,
            duplicatePolicy = draftDuplicatePolicy
        )
        baseLowLight = draftLowLight
        baseNearworkThreshold = draftNearworkThreshold
        baseBreakGapSeconds = draftBreakGapSeconds
        baseMinSessionDurationSeconds = draftMinSessionDurationSeconds
        baseCloseDistance = draftCloseDistance
        baseExtremeClose = draftExtremeClose
        baseReplaceAlsSpikes = draftReplaceAlsSpikes
        baseAlsSpikeThresholdLux = draftAlsSpikeThresholdLux
        baseShowDebugOverlay = draftShowDebugOverlay
        baseDailyReminderEnabled = draftDailyReminderEnabled
        baseDailyReminderTime = draftDailyReminderTime
        basePostImportNotificationEnabled = draftPostImportNotificationEnabled
        baseDuplicatePolicy = draftDuplicatePolicy

        settingsViewModel.setLowLightThresholdLux(draftLowLight)
        settingsViewModel.setNearworkDistanceThresholdCm(draftNearworkThreshold)
        settingsViewModel.setBreakGapSeconds(draftBreakGapSeconds)
        settingsViewModel.setMinSessionDurationSeconds(draftMinSessionDurationSeconds)
        settingsViewModel.setCloseDistanceThresholdCm(draftCloseDistance)
        settingsViewModel.setExtremeCloseThresholdCm(draftExtremeClose)
        settingsViewModel.setReplaceAlsSingleSampleSpikes(draftReplaceAlsSpikes)
        settingsViewModel.setAlsSpikeThresholdLux(draftAlsSpikeThresholdLux)
        settingsViewModel.setShowDebugOverlay(draftShowDebugOverlay)
        settingsViewModel.setDuplicateResolutionPolicy(draftDuplicatePolicy)
        settingsViewModel.setDailyReminderTimeLocal(draftDailyReminderTime)

        if (draftDailyReminderEnabled) {
            if (needsNotificationPermission(context)) {
                pendingNotificationToggle = NotificationToggleTarget.DailyReminder
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settingsViewModel.setDailyReminderEnabled(true)
            }
        } else {
            settingsViewModel.setDailyReminderEnabled(false)
        }

        if (draftPostImportNotificationEnabled) {
            if (needsNotificationPermission(context)) {
                pendingNotificationToggle = NotificationToggleTarget.PostImport
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                settingsViewModel.setPostImportNotificationEnabled(true)
            }
        } else {
            settingsViewModel.setPostImportNotificationEnabled(false)
        }

        onComplete?.invoke()
    }

    fun applyImportedAnalysisSettings(settings: PortableAnalysisSettings) {
        pendingApply = false
        pendingSnapshot = null

        baseLowLight = settings.lowLightThresholdLux
        baseNearworkThreshold = settings.nearworkDistanceThresholdCm
        baseBreakGapSeconds = settings.breakGapSeconds
        baseMinSessionDurationSeconds = settings.minSessionDurationSeconds
        baseCloseDistance = settings.closeDistanceThresholdCm
        baseExtremeClose = settings.extremeCloseThresholdCm
        baseReplaceAlsSpikes = settings.replaceAlsSingleSampleSpikes
        baseAlsSpikeThresholdLux = settings.alsSpikeThresholdLux

        draftLowLight = settings.lowLightThresholdLux
        draftNearworkThreshold = settings.nearworkDistanceThresholdCm
        draftBreakGapSeconds = settings.breakGapSeconds
        draftMinSessionDurationSeconds = settings.minSessionDurationSeconds
        draftCloseDistance = settings.closeDistanceThresholdCm
        draftExtremeClose = settings.extremeCloseThresholdCm
        draftReplaceAlsSpikes = settings.replaceAlsSingleSampleSpikes
        draftAlsSpikeThresholdLux = settings.alsSpikeThresholdLux

        lowLightText = settings.lowLightThresholdLux.toString()
        nearworkThresholdText = settings.nearworkDistanceThresholdCm.toString()
        breakGapSecondsText = settings.breakGapSeconds.toString()
        minSessionDurationText = settings.minSessionDurationSeconds.toString()
        closeDistanceText = settings.closeDistanceThresholdCm.toString()
        extremeCloseText = settings.extremeCloseThresholdCm.toString()
        alsSpikeThresholdText = format1(settings.alsSpikeThresholdLux)

        settingsViewModel.setLowLightThresholdLux(settings.lowLightThresholdLux)
        settingsViewModel.setNearworkDistanceThresholdCm(settings.nearworkDistanceThresholdCm)
        settingsViewModel.setBreakGapSeconds(settings.breakGapSeconds)
        settingsViewModel.setMinSessionDurationSeconds(settings.minSessionDurationSeconds)
        settingsViewModel.setCloseDistanceThresholdCm(settings.closeDistanceThresholdCm)
        settingsViewModel.setExtremeCloseThresholdCm(settings.extremeCloseThresholdCm)
        settingsViewModel.setReplaceAlsSingleSampleSpikes(settings.replaceAlsSingleSampleSpikes)
        settingsViewModel.setAlsSpikeThresholdLux(settings.alsSpikeThresholdLux)
    }

    BackHandler { requestExit() }

    LaunchedEffect(backSignal) {
        if (backSignal > 0) {
            requestExit()
        }
    }
    Column(
        modifier = modifier
            .testTag(UiTestTags.SettingsScreen)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Thresholds apply instantly to your dashboards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isDirty) {
                TextButton(
                    modifier = Modifier.testTag(UiTestTags.SettingsApplyButton),
                    onClick = { applyDraftSettings() },
                    enabled = thresholdOrderingError == null
                ) {
                    Text("Apply")
                }
            }
        }

        SettingsCategory(
            modifier = Modifier.testTag(UiTestTags.SettingsGeneralCategory),
            title = "General",
            subtitle = "Everyday app and device settings.",
            expanded = generalExpanded,
            onToggle = { generalExpanded = !generalExpanded }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCardHeader("Notifications", accentColors)
                Text(
                    "Educational reminders delivered on-device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Prompt: check today's nearwork summary.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draftDailyReminderEnabled,
                        onCheckedChange = { enabled -> draftDailyReminderEnabled = enabled }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reminder time", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Uses the active profile's timezone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AccentTextButton(
                        fillWidth = false,
                        onClick = {
                            val current = parseReminderTime(draftDailyReminderTime)
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val formatted = LocalTime.of(hour, minute).format(TIME_FORMAT)
                                    draftDailyReminderTime = formatted
                                },
                                current.hour,
                                current.minute,
                                true
                            ).show()
                        }
                    ) {
                        Text(formatReminderLabel(draftDailyReminderTime))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Post-import summary", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "One-time summary after a successful import.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        modifier = Modifier.testTag(UiTestTags.SettingsPostImportNotificationSwitch),
                        checked = draftPostImportNotificationEnabled,
                        onCheckedChange = { enabled -> draftPostImportNotificationEnabled = enabled }
                    )
                }
            }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsCardHeader("Import behavior", accentColors)
                Text(
                    "Decide how to resolve duplicate timestamps during import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DuplicateResolutionPolicy.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = draftDuplicatePolicy == option,
                            onClick = { draftDuplicatePolicy = option },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = DuplicateResolutionPolicy.entries.size
                            )
                        ) {
                            Text(option.displayLabel)
                        }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsCardHeader("Demo / Testing", accentColors)
                Text(
                    "Load sample data for presentation purposes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Active: $activeProfileName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AccentTextButton(
                    enabled = !isDemoBusy && activeProfileId != null,
                    onClick = {
                        val profileId = activeProfileId ?: return@AccentTextButton
                        scope.launch {
                            isDemoBusy = true
                            val result = demoRepository.importDemoDataset(profileId, defaultDemoFile)
                            settingsViewModel.setLastDemoProfileId(profileId)
                            isDemoBusy = false
                            snackbarHostState.showSnackbar(result.toUserMessage(defaultDemoFile))
                        }
                    }
                ) {
                    Text("Load demo dataset into active profile")
                }

                AccentTextButton(
                    enabled = !isDemoBusy,
                    onClick = {
                        scope.launch {
                            isDemoBusy = true
                            val demoFiles = listOf(
                                "optodata_2026-06-05.csv",
                                "optodata_2026-06-06.csv",
                                "optodata_2026-06-07.csv",
                                "optodata_2026-06-08.csv"
                            )
                            val existingDemo = profiles.firstOrNull { it.name == "Demo Profile" }
                            if (existingDemo != null) {
                                profileRepository.deleteProfile(existingDemo.id)
                                val remaining = profileRepository.getProfiles()
                                remaining.firstOrNull()?.let { activeProfileStore.setActiveProfileId(it.id) }
                            }
                            val id = profileRepository.insertProfile("Demo Profile", System.currentTimeMillis())
                            activeProfileStore.setActiveProfileId(id)
                            var successCount = 0
                            for (file in demoFiles) {
                                val result = demoRepository.importDemoDataset(id, file)
                                if (result is com.example.nearworkthesis.domain.ImportResult.Success) successCount++
                            }
                            settingsViewModel.setLastDemoProfileId(id)
                            isDemoBusy = false
                            snackbarHostState.showSnackbar("Demo profile created with $successCount/4 days imported.")
                        }
                    }
                ) {
                    Text("Create fresh demo profile and load data there")
                }
                AccentTextButton(
                    enabled = !isDemoBusy && lastDemoProfileId != null,
                    onClick = { showResetConfirm = true }
                ) {
                    Text("Reset demo data")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show debug overlay", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Displays profile/date/thresholds for screenshot reproducibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        modifier = Modifier.testTag(UiTestTags.SettingsDebugOverlaySwitch),
                        checked = draftShowDebugOverlay,
                        onCheckedChange = { enabled -> draftShowDebugOverlay = enabled }
                    )
                }

                Text(
                    "Overlay: low-light ${draftLowLight} lux - nearwork ${draftNearworkThreshold} cm - close ${draftCloseDistance} cm - extreme ${draftExtremeClose} cm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCardHeader("General thresholds", accentColors)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Low-light threshold (lux)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Below this level, low-light conditions apply. Adopted from indoor lighting classifications (Niyazmand et al. 2025).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sliderMaxLux = 5_000f
                    val sliderValueLux = draftLowLight.coerceIn(0, sliderMaxLux.toInt()).toFloat()
                    Slider(
                        value = sliderValueLux,
                        onValueChange = { value ->
                            val rounded = (value / 10f).toInt() * 10
                            lowLightText = rounded.toString()
                            draftLowLight = rounded
                        },
                        valueRange = 0f..sliderMaxLux
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = lowLightText,
                            onValueChange = { new ->
                                lowLightText = new.filter { it.isDigit() }.take(6)
                                lowLightText.toIntOrNull()?.let { draftLowLight = it }
                            },
                            label = { Text("Lux") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Text(
                        "Current: $draftLowLight lux",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Close distance threshold (cm)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Flags a session when any sample falls below this NRS zone 2 boundary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sliderMinCm = 0f
                    val sliderMaxCm = 50f
                    val sliderValueCm = draftCloseDistance.coerceIn(sliderMinCm.toInt(), sliderMaxCm.toInt()).toFloat()
                    Slider(
                        value = sliderValueCm,
                        onValueChange = { value ->
                            val rounded = value.toInt().coerceIn(sliderMinCm.toInt(), sliderMaxCm.toInt())
                            closeDistanceText = rounded.toString()
                            draftCloseDistance = rounded
                        },
                        valueRange = sliderMinCm..sliderMaxCm
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.SettingsCloseDistanceField),
                            value = closeDistanceText,
                            onValueChange = { new ->
                                closeDistanceText = new.filter { it.isDigit() }.take(3)
                                closeDistanceText.toIntOrNull()?.let { draftCloseDistance = it }
                            },
                            label = { Text("cm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Text(
                        "Current: $draftCloseDistance cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extreme close threshold (cm)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Flags a session when any sample falls below this NRS zone 1 boundary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val sliderMinCm = 0f
                    val sliderMaxCm = 50f
                    val sliderValueCm = draftExtremeClose.coerceIn(sliderMinCm.toInt(), sliderMaxCm.toInt()).toFloat()
                    Slider(
                        value = sliderValueCm,
                        onValueChange = { value ->
                            val rounded = value.toInt().coerceIn(sliderMinCm.toInt(), sliderMaxCm.toInt())
                            extremeCloseText = rounded.toString()
                            draftExtremeClose = rounded
                        },
                        valueRange = sliderMinCm..sliderMaxCm
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.SettingsExtremeCloseField),
                            value = extremeCloseText,
                            onValueChange = { new ->
                                extremeCloseText = new.filter { it.isDigit() }.take(3)
                                extremeCloseText.toIntOrNull()?.let { draftExtremeClose = it }
                            },
                            isError = thresholdOrderingError != null,
                            label = { Text("cm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Text(
                        "Current: $draftExtremeClose cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (thresholdOrderingError != null) {
                        Text(
                            text = thresholdOrderingError,
                            modifier = Modifier.testTag(UiTestTags.SettingsThresholdOrderingError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Replace isolated ALS spikes", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Smooths one-sample lux spikes that stand sharply above both neighbors.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = draftReplaceAlsSpikes,
                            onCheckedChange = { enabled -> draftReplaceAlsSpikes = enabled }
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = alsSpikeThresholdText,
                        onValueChange = { new ->
                            alsSpikeThresholdText = new.replace(',', '.').filter { it.isDigit() || it == '.' }.take(8)
                            alsSpikeThresholdText.toDoubleOrNull()?.let { draftAlsSpikeThresholdLux = it }
                        },
                        label = { Text("Spike detection threshold (lux)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = draftReplaceAlsSpikes
                    )

                    Text(
                        "Current: ${format1(draftAlsSpikeThresholdLux)} lux",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    SettingsCardHeader("Device", accentColors)
                Text(
                    "Configure device-side settings (stub backend for now).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentTextButton(onClick = { requestExit(onOpenDeviceConfig) }) {
                    Text("Configure device")
                }
            }
            }
        }

        SettingsCategory(
            title = "Research",
            subtitle = "Advanced - for researchers.",
            expanded = researchExpanded,
            onToggle = { researchExpanded = !researchExpanded }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsCardHeader("Cross-device settings", accentColors)
                Text(
                    "Export the current global analysis settings to JSON, or import a settings file from another device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentTextButton(
                    onClick = {
                        val filename = AnalysisSettingsExchange.suggestedFilename(LocalDate.now())
                        val json = AnalysisSettingsExchange.toJson(
                            PortableAnalysisSettings(
                                lowLightThresholdLux = lowLightThresholdLux,
                                nearworkDistanceThresholdCm = nearworkThresholdCm,
                                breakGapSeconds = draftBreakGapSeconds,
                                minSessionDurationSeconds = draftMinSessionDurationSeconds,
                                closeDistanceThresholdCm = closeDistanceThresholdCm,
                                extremeCloseThresholdCm = extremeCloseThresholdCm,
                                replaceAlsSingleSampleSpikes = replaceAlsSingleSampleSpikes,
                                alsSpikeThresholdLux = alsSpikeThresholdLux
                            )
                        )
                        pendingSettingsFilename = filename
                        pendingSettingsJson = json
                        exportSettingsLauncher.launch(filename)
                    }
                ) {
                    Text("Export settings JSON")
                }
                AccentTextButton(
                    onClick = {
                        importSettingsLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                ) {
                    Text("Import settings JSON")
                }
            }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsCardHeader("Research thresholds", accentColors)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nearwork distance threshold (cm)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Defines the maximum distance included when building nearwork sessions and summaries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = nearworkThresholdText,
                        onValueChange = { new ->
                            nearworkThresholdText = new.filter { it.isDigit() }.take(3)
                            nearworkThresholdText.toIntOrNull()?.let { draftNearworkThreshold = it }
                        },
                        label = { Text("cm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Current: $draftNearworkThreshold cm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Session gap threshold (seconds)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Samples separated by at least this gap start a new nearwork session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = breakGapSecondsText,
                        onValueChange = { new ->
                            breakGapSecondsText = new.filter { it.isDigit() }.take(4)
                            breakGapSecondsText.toIntOrNull()?.let { draftBreakGapSeconds = it }
                        },
                        label = { Text("seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Current: $draftBreakGapSeconds s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Minimum session duration (seconds)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Shorter runs are ignored when summarising sessions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = minSessionDurationText,
                        onValueChange = { new ->
                            minSessionDurationText = new.filter { it.isDigit() }.take(4)
                            minSessionDurationText.toIntOrNull()?.let { draftMinSessionDurationSeconds = it }
                        },
                        label = { Text("seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        "Current: $draftMinSessionDurationSeconds s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Outdoor protective threshold: 3,000 lux", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Above this level, conditions are associated with protective outdoor illuminance. Literature-derived (Xiong et al. 2017).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
        }

        SettingsCategory(
            title = "Info",
            subtitle = "Reference material and study context.",
            expanded = infoExpanded,
            onToggle = { infoExpanded = !infoExpanded }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsCardHeader("About / Research", accentColors)
                Text(
                    "Purpose, privacy, and study materials in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentTextButton(onClick = { requestExit(onOpenAboutResearch) }) {
                    Text("Open About / Research")
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
                    SettingsCardHeader("Methods & Assumptions", accentColors)
                Text(
                    "Learn how preprocessing, thresholds, and time handling influence insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentTextButton(onClick = { requestExit(onOpenMethodsAssumptions) }) {
                    Text("Open methods")
                }
            }
            }
        }

    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved settings changes.") },
            confirmButton = {
                TextButton(
                    enabled = thresholdOrderingError == null,
                    onClick = {
                        showExitConfirm = false
                        applyDraftSettings {
                            (pendingExitAction ?: onConfirmExit).also { pendingExitAction = null }.invoke()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        (pendingExitAction ?: onConfirmExit).also { pendingExitAction = null }.invoke()
                    }
                ) {
                    Text("Discard")
                }
            }
        )
    }

    pendingImportedSettings?.let { importedSettings ->
        AlertDialog(
            onDismissRequest = {
                pendingImportedSettings = null
                pendingImportedSettingsLabel = null
            },
            title = { Text("Import settings?") },
            text = {
                Text(
                    "Apply analysis settings from ${pendingImportedSettingsLabel ?: "the selected file"}? " +
                        "This will overwrite the current global analysis settings on this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        applyImportedAnalysisSettings(importedSettings)
                        pendingImportedSettings = null
                        pendingImportedSettingsLabel = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Analysis settings imported.")
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImportedSettings = null
                        pendingImportedSettingsLabel = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset demo data?") },
            text = { Text("This will clear demo measurements/import sessions. If the last demo profile is \"Demo Profile\", it will be deleted.") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showResetConfirm = false
                        val targetProfileId = lastDemoProfileId ?: return@Button
                        scope.launch {
                            isDemoBusy = true
                            val profile = profileRepository.getProfile(targetProfileId)
                            if (profile?.name == "Demo Profile") {
                                profileRepository.deleteProfile(targetProfileId)
                                val remaining = profileRepository.getProfiles()
                                remaining.firstOrNull()?.let { activeProfileStore.setActiveProfileId(it.id) }
                            } else {
                                demoRepository.clearProfileData(targetProfileId)
                            }
                            settingsViewModel.setLastDemoProfileId(null)
                            isDemoBusy = false
                            snackbarHostState.showSnackbar("Demo data reset")
                        }
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun ImportResult.toUserMessage(filename: String): String {
    return when (this) {
        is ImportResult.Success -> "Demo imported: $filename"
        is ImportResult.NoNewData -> "No new data (already imported): $filename"
        is ImportResult.Error -> "Import failed: ${this.message}"
    }
}

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun parseReminderTime(value: String): LocalTime {
    return runCatching { LocalTime.parse(value, TIME_FORMAT) }
        .getOrElse { LocalTime.parse(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL, TIME_FORMAT) }
}

private fun formatReminderLabel(value: String): String {
    val parsed = runCatching { LocalTime.parse(value, TIME_FORMAT) }.getOrNull()
    return parsed?.format(TIME_FORMAT) ?: SettingsDefaults.DAILY_REMINDER_TIME_LOCAL
}

private fun needsNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) != PackageManager.PERMISSION_GRANTED
}

private fun readSettingsTextFromUri(context: android.content.Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader(Charsets.UTF_8).readText()
    } ?: throw IllegalStateException("Unable to open selected settings file.")
}

private fun writeSettingsTextToUri(context: android.content.Context, uri: Uri, text: String): Result<Unit> {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("Unable to open output stream.")
    }
}

@Composable
private fun SettingsCategory(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title"
                )
            }
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun SettingsCardHeader(
    title: String,
    accentColors: SettingsAccentColors
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = accentColors.accent
    )
}

@Composable
private fun AccentTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    content: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            contentColor = Periwinkle
        )
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            )
        ) {
            content()
        }
    }
}

@Composable
private fun settingsAccentColors(): SettingsAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        SettingsAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        SettingsAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class SettingsAccentColors(
    val accent: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color
)

private data class SettingsSnapshot(
    val lowLight: Int,
    val nearworkThreshold: Int,
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistance: Int,
    val extremeClose: Int,
    val replaceAlsSpikes: Boolean,
    val alsSpikeThresholdLux: Double,
    val showDebugOverlay: Boolean,
    val dailyReminderEnabled: Boolean,
    val dailyReminderTime: String,
    val postImportNotificationEnabled: Boolean,
    val duplicatePolicy: DuplicateResolutionPolicy
)

private enum class NotificationToggleTarget {
    DailyReminder,
    PostImport
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    val fake = remember { FakeSettingsStore() }
    NearworkTheme {
        CompositionLocalProvider(
            LocalSettingsStore provides fake,
            LocalNotificationScheduler provides FakeNotificationScheduler()
        ) {
            SettingsScreen(onOpenDeviceConfig = {})
        }
    }
}

private class FakeSettingsStore : SettingsStore {
    private val lowLux = MutableStateFlow(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX)
    private val nearworkDistanceCm = MutableStateFlow(SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM)
    private val breakGapSeconds = MutableStateFlow(SettingsDefaults.BREAK_GAP_SECONDS)
    private val minSessionDurationSeconds = MutableStateFlow(SettingsDefaults.MIN_SESSION_DURATION_SECONDS)
    private val closeDistanceThresholdCm = MutableStateFlow(SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM)
    private val extremeCloseThresholdCm = MutableStateFlow(SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM)
    private val replaceAlsSingleSampleSpikes = MutableStateFlow(SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES)
    private val alsSpikeThresholdLux = MutableStateFlow(SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX)
    private val showDebugOverlay = MutableStateFlow(SettingsDefaults.SHOW_DEBUG_OVERLAY)
    private val lastDemoProfileId = MutableStateFlow<Long?>(null)
    private val dailyReminderEnabled = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_ENABLED)
    private val dailyReminderTimeLocal = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL)
    private val postImportNotificationEnabled = MutableStateFlow(SettingsDefaults.POST_IMPORT_NOTIFICATION_ENABLED)
    private val duplicateResolutionPolicy = MutableStateFlow(SettingsDefaults.DUPLICATE_RESOLUTION_POLICY)

    override fun observeLowLightThresholdLux(): Flow<Int> = lowLux

    override suspend fun setLowLightThresholdLux(lux: Int) {
        lowLux.value = lux
    }

    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = nearworkDistanceCm

    override suspend fun setNearworkDistanceThresholdCm(value: Int) {
        nearworkDistanceCm.value = value
    }

    override fun observeBreakGapSeconds(): Flow<Int> = breakGapSeconds

    override suspend fun setBreakGapSeconds(value: Int) {
        breakGapSeconds.value = value
    }

    override fun observeMinSessionDurationSeconds(): Flow<Int> = minSessionDurationSeconds

    override suspend fun setMinSessionDurationSeconds(value: Int) {
        minSessionDurationSeconds.value = value
    }

    override fun observeCloseDistanceThresholdCm(): Flow<Int> = closeDistanceThresholdCm

    override suspend fun setCloseDistanceThresholdCm(value: Int) {
        closeDistanceThresholdCm.value = value
    }

    override fun observeExtremeCloseThresholdCm(): Flow<Int> = extremeCloseThresholdCm

    override suspend fun setExtremeCloseThresholdCm(value: Int) {
        extremeCloseThresholdCm.value = value
    }

    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = replaceAlsSingleSampleSpikes

    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) {
        replaceAlsSingleSampleSpikes.value = enabled
    }

    override fun observeAlsSpikeThresholdLux(): Flow<Double> = alsSpikeThresholdLux

    override suspend fun setAlsSpikeThresholdLux(value: Double) {
        alsSpikeThresholdLux.value = value
    }

    override fun observeShowDebugOverlay(): Flow<Boolean> = showDebugOverlay

    override suspend fun setShowDebugOverlay(enabled: Boolean) {
        showDebugOverlay.value = enabled
    }

    override fun observeLastDemoProfileId(): Flow<Long?> = lastDemoProfileId

    override suspend fun setLastDemoProfileId(profileId: Long?) {
        lastDemoProfileId.value = profileId
    }

    override fun observeDailyReminderEnabled(): Flow<Boolean> = dailyReminderEnabled

    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dailyReminderEnabled.value = enabled
    }

    override fun observeDailyReminderTimeLocal(): Flow<String> = dailyReminderTimeLocal

    override suspend fun setDailyReminderTimeLocal(value: String) {
        dailyReminderTimeLocal.value = value
    }

    override fun observePostImportNotificationEnabled(): Flow<Boolean> = postImportNotificationEnabled

    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) {
        postImportNotificationEnabled.value = enabled
    }

    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = duplicateResolutionPolicy

    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) {
        duplicateResolutionPolicy.value = policy
    }
}

private class FakeNotificationScheduler : NotificationScheduler {
    override fun ensureChannels() = Unit

    override suspend fun rescheduleDailyReminder() = Unit

    override suspend fun cancelDailyReminder() = Unit

    override suspend fun enqueuePostImportSummary(summary: ImportSummary) = Unit
}
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")























