package com.example.nearworkthesis.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalHowfarUf2Archive
import com.example.nearworkthesis.app.LocalNearworkRiskScoreCalculator
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.core.ui.components.QuickExportCard
import com.example.nearworkthesis.core.ui.components.QuickExportCardColors
import com.example.nearworkthesis.core.ui.components.QuickExportFormat
import com.example.nearworkthesis.core.ui.components.QuickExportSeries
import com.example.nearworkthesis.core.ui.components.QuickExportState
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.core.text.DailyInsightsThresholds
import com.example.nearworkthesis.core.text.NearworkInsightsBuilder
import com.example.nearworkthesis.core.util.AppConstants
import com.example.nearworkthesis.core.util.writeBytesToDownloads
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkRiskReason
import com.example.nearworkthesis.domain.analysis.NearworkSession
import com.example.nearworkthesis.domain.analysis.NearworkSessionRisk
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun DailyScreen(
    modifier: Modifier = Modifier,
    measurementRepository: MeasurementRepository,
    selectedDate: String? = null,
    onGoToImport: () -> Unit = {},
    onOpenAnalysis: (String) -> Unit = {},
    onDeletedEmpty: () -> Unit = {}
) {
    val activeProfileStore = LocalActiveProfileStore.current
    val nearworkRiskScoreCalculator = LocalNearworkRiskScoreCalculator.current
    val profileRepository = LocalProfileRepository.current
    val viewModel: DailyViewModel = viewModel(
        factory = DailyViewModel.factory(
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeProfileStore,
            nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
            selectedDate = selectedDate
        )
    )
    val state by viewModel.uiState.collectAsState()
    val selectedLocalDate by viewModel.selectedDate.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val activeProfileName = remember(state) {
        val dataState = state as? DailyUiState.Data
        val profiles = dataState?.profiles.orEmpty()
        val activeProfileId = dataState?.activeProfileId
        profiles.firstOrNull { it.id == activeProfileId }?.name ?: profiles.firstOrNull()?.name ?: "Profile"
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DailyUiEvent.DayDeleted -> {
                    snackbarHostState.showSnackbar("Deleted data for ${event.localDay}")
                    if (event.shouldNavigateBack) {
                        onDeletedEmpty()
                    }
                }
                is DailyUiEvent.DeleteFailed -> {
                    snackbarHostState.showSnackbar("Unable to delete data for ${event.localDay}")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DailyDateHeader(
                selectedDate = selectedLocalDate,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onGoBack = viewModel::goToPreviousDay,
                onGoForward = viewModel::goToNextDay,
                onGoToToday = viewModel::goToToday
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is DailyUiState.Loading -> DailyLoading()
                    is DailyUiState.Empty -> DailyEmpty(
                        selectedDate = selectedLocalDate,
                        onGoToImport = onGoToImport
                    )
                    is DailyUiState.Error -> DailyError(
                        message = (state as DailyUiState.Error).message,
                        onRetry = { viewModel.refresh() }
                    )
                    is DailyUiState.Data -> {
                        val dataState = state as DailyUiState.Data
                        DailyContent(
                            summary = dataState.summary,
                            sampleCount = dataState.sampleCount,
                            avgDistanceCm = dataState.avgDistanceCm,
                            processedSamples = dataState.processedSamples,
                            sessionInsights = dataState.sessionInsights,
                            sessions = dataState.sessions,
                            nrsResult = dataState.nrsResult,
                            analysisConfig = dataState.analysisConfig,
                            measurementRepository = measurementRepository,
                            activeProfileId = dataState.activeProfileId,
                            howfarUf2Archive = LocalHowfarUf2Archive.current,
                            isCurrentDay = dataState.isCurrentDay,
                            onGoToImport = onGoToImport,
                            onOpenAnalysis = onOpenAnalysis,
                            onRequestDelete = { day -> deleteTarget = day }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete day data?") },
            text = { Text("Delete measurements for $day from $activeProfileName. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.deleteCurrentDay()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DailyLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(16.dp))
        Text(text = "Loading your day...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DailyDateHeader(
    selectedDate: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoToToday: () -> Unit
) {
    val accentColors = dailyAccentColors()
    val isToday = selectedDate == LocalDate.now()
    val dateLabel = remember(selectedDate, isToday) {
        if (isToday) {
            "Today"
        } else {
            selectedDate.format(dayNavigationFormatter)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoBack, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous day"
                )
            }
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onGoForward, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next day"
                )
            }
        }

        if (!isToday) {
            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = onGoToToday,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColors.accent,
                    contentColor = accentColors.onAccent
                )
            ) {
                Text("Today", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyEmpty(
    selectedDate: LocalDate,
    onGoToImport: () -> Unit
) {
    val isToday = selectedDate == LocalDate.now()
    val headline = remember(selectedDate, isToday) {
        if (isToday) {
            "No measurements for today"
        } else {
            "No measurements for ${selectedDate.format(dayNavigationFormatter)}"
        }
    }
    val subtitle = if (isToday) {
        "Connect HowFar or import a file to see today’s data."
    } else {
        "Try another day or import more data to view this date."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(24.dp))
        Button(onClick = onGoToImport) {
            Text(text = "Go to Import")
        }
    }
}

@Composable
private fun DailyError(
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
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = "Unable to load daily data",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun DailyContent(
    summary: DailySummary,
    sampleCount: Int,
    avgDistanceCm: Double?,
    processedSamples: List<NearworkSample>,
    sessionInsights: DailySessionInsights,
    sessions: List<DailySessionUiModel>,
    nrsResult: com.example.nearworkthesis.domain.analysis.NrsResult,
    analysisConfig: AnalysisConfig,
    measurementRepository: MeasurementRepository?,
    activeProfileId: Long?,
    howfarUf2Archive: com.example.nearworkthesis.importing.howfar.HowfarUf2Archive?,
    isCurrentDay: Boolean,
    onGoToImport: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    val accentColors = dailyAccentColors()
    val firstTime = remember(summary.firstTimestampIso) { summary.firstTimestampIso?.let { formatTime(it) } }
    val lastTime = remember(summary.lastTimestampIso) { summary.lastTimestampIso?.let { formatTime(it) } }
    val thresholds = analysisConfig.thresholds
    val lowLightThresholdLux = thresholds.lowLightThresholdLux
    val nearworkThresholdCm = thresholds.nearworkDistanceThresholdCm

    val insightsBullets = remember(
        summary,
        sessionInsights,
        lowLightThresholdLux,
        nearworkThresholdCm
    ) {
        NearworkInsightsBuilder.build(
            summary = summary,
            sessionInsights = sessionInsights,
            thresholds = DailyInsightsThresholds(
                lowLightThresholdLux = lowLightThresholdLux,
                nearworkDistanceThresholdCm = nearworkThresholdCm
            )
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNrsInfo by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(QuickExportFormat.Csv) }
    var exportSeries by remember { mutableStateOf(QuickExportSeries.Raw) }
    var exportState by remember { mutableStateOf<QuickExportState>(QuickExportState.Idle) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daily Summary",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accentColors.accent
                )
                Text(
                    text = "Your latest imported data at a glance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrentDay) {
                    Text(
                        text = "Showing today’s measurements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } 

        QuickExportCard(
            exportFormat = exportFormat,
            exportSeries = exportSeries,
            exportState = exportState,
            onSelectFormat = { exportFormat = it },
            onSelectSeries = { exportSeries = it },
            onDismissMessage = { exportState = QuickExportState.Idle },
            colors = QuickExportCardColors(
                accent = accentColors.accent,
                onAccent = accentColors.onAccent,
                buttonAccent = Periwinkle,
                onButtonAccent = Graphite
            ),
            onExport = {
                val profileId = activeProfileId
                val repository = measurementRepository
                val archive = howfarUf2Archive
                if (profileId == null || repository == null || archive == null) {
                    exportState = QuickExportState.Error("No active profile selected.")
                    return@QuickExportCard
                }
                exportState = QuickExportState.Exporting
                scope.launch {
                    exportState = runCatching {
                        when (exportFormat) {
                            QuickExportFormat.Csv -> {
                                val csv = when (exportSeries) {
                                    QuickExportSeries.Raw -> repository.exportRawCsv(profileId, summary.day, summary.day)
                                    QuickExportSeries.Processed -> repository.exportProcessedCsv(
                                        profileId = profileId,
                                        startDay = summary.day,
                                        endDay = summary.day,
                                        config = analysisConfig
                                    )
                                }
                                val filename = if (exportSeries == QuickExportSeries.Raw) {
                                    "nearwork_raw_${summary.day}.csv"
                                } else {
                                    "nearwork_processed_${summary.day}.csv"
                                }
                                writeBytesToDownloads(context, filename, "text/csv", csv.toByteArray())
                                QuickExportState.Success(filename)
                            }
                            QuickExportFormat.Uf2 -> {
                                val snapshot = archive.loadLatest(profileId)
                                    ?: return@runCatching QuickExportState.Error("No UF2 snapshot available yet.")
                                val filename = "howfar_${summary.day}.uf2"
                                writeBytesToDownloads(context, filename, "application/octet-stream", snapshot.bytes)
                                QuickExportState.Success(filename)
                            }
                        }
                    }.getOrElse { throwable ->
                        QuickExportState.Error(throwable.message ?: "Export failed.")
                    }
                }
            }
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenAnalysis(summary.day) },
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColors.accent,
                contentColor = accentColors.onAccent
            )
        ) {
            Icon(imageVector = Icons.Default.Timeline, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("View Raw vs Processed", fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = { onRequestDelete(summary.day) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Delete day data")
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Overview") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Details") }
            )
        }

        if (selectedTabIndex == 0) {
            DiopterHoursCard(
                diopterHoursTotal = summary.diopterHoursTotal,
                nrs = nrsResult.nrs,
                meanLuxDuringNearwork = nrsResult.meanLuxDuringNearwork,
                lowLightMinutes = summary.lowLightMinutes,
                onOpenNrsInfo = { showNrsInfo = true }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Sessions",
                    headline = sessionInsights.sessions.size.toString(),
                    subtitle = "Detected today",
                    icon = Icons.Default.Timeline,
                    accentColors = accentColors
                )
                RiskFlagSummaryCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    flaggedSessions = sessionInsights.flaggedSessions,
                    accentColors = accentColors
                )
            }

            DailySessionOverviewCard(
                insights = sessionInsights,
                onInspect = { onOpenAnalysis(summary.day) },
                accentColors = accentColors
            )

            DailyInsightsCard(bullets = insightsBullets)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Measurements",
                    headline = "$sampleCount",
                    subtitle = listOfNotNull(
                        firstTime?.let { "First: $it" },
                        lastTime?.let { "Last: $it" }
                    ).joinToString(" • "),
                    icon = Icons.Default.CheckCircle,
                    accentColors = accentColors
                )
                MetricCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Distance (cm)",
                    headline = formatRange(avgDistanceCm),
                    subtitle = "Min ${formatValue(summary.minDistanceCm)} • Max ${formatValue(summary.maxDistanceCm)}",
                    icon = Icons.Default.Visibility,
                    accentColors = accentColors
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = "Illumination (lux)",
                    headline = formatRange(summary.avgLux),
                    subtitle = "Min ${formatValue(summary.minLux)} • Max ${formatValue(summary.maxLux)}",
                    icon = Icons.Default.Lightbulb,
                    accentColors = accentColors
                )
                QualityBadges(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onGoToImport = onGoToImport,
                    accentColors = accentColors
                )
            }

            NearworkSessionDetailsSection(
                insights = sessionInsights,
                sessions = sessions,
                processedSamples = processedSamples,
                onInspect = { onOpenAnalysis(summary.day) },
                accentColors = accentColors
            )
        }
    } 

    if (showNrsInfo) {
        AlertDialog(
            onDismissRequest = { showNrsInfo = false },
            title = { Text("About NRS") },
            text = {
                Text("NRS is projected to a 24-hour equivalent. A child wearing the device for 4 hours and one wearing it for 8 hours in identical conditions will show the same NRS.")
            },
            confirmButton = {
                TextButton(onClick = { showNrsInfo = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun DailySessionOverviewCard(
    insights: DailySessionInsights,
    onInspect: () -> Unit,
    accentColors: DailyAccentColors
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentSectionTitle(text = "Session timeline", accentColors = accentColors)

            val longest = insights.longestSession
            if (longest == null) {
                Text(
                    "No nearwork sessions detected for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Longest continuous nearwork", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${formatDuration(longest.durationSeconds)}  •  ${formatRangeHm(longest.startTimestampMillis, longest.endTimestampMillis)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    AssistChip(onClick = onInspect, label = { Text("Inspect") })
                }
            }

            NearworkTimelineBar(
                sessions = insights.sessions,
                onTapSession = { onInspect() }
            )
        }
    }
}

@Composable
private fun NearworkSessionDetailsSection(
    insights: DailySessionInsights,
    sessions: List<DailySessionUiModel>,
    processedSamples: List<NearworkSample>,
    onInspect: () -> Unit,
    accentColors: DailyAccentColors
) {
    var sessionsExpanded by rememberSaveable(sessions) { mutableStateOf(true) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentSectionTitle(text = "Session details", accentColors = accentColors)

            if (insights.sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AssistChip(onClick = onInspect, label = { Text("Inspect") })
                }
            }

            DailyDistributionSection(
                title = "Distance zones",
                rows = distanceZoneRows(processedSamples),
                emptyMessage = "No data",
                accentColors = accentColors
            )

            DailyDistributionSection(
                title = "Lighting conditions",
                rows = lightingConditionRows(processedSamples),
                emptyMessage = "No data",
                accentColors = accentColors
            )
            if (sessions.isEmpty()) {
                Text(
                    "No nearwork sessions for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { sessionsExpanded = !sessionsExpanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColors.accent)
                ) {
                    Icon(
                        imageVector = if (sessionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (sessionsExpanded) "Hide identified sessions" else "Show identified sessions (${sessions.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (sessionsExpanded) {
                    sessions.forEach { session ->
                        SessionDetailCard(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskFlagSummaryCard(
    modifier: Modifier = Modifier,
    flaggedSessions: List<NearworkSessionRisk>,
    accentColors: DailyAccentColors
) {
    val reasons = remember(flaggedSessions) {
        flaggedSessions.flatMap { it.reasons }.distinct()
    }

    ElevatedCard(
        modifier = modifier
            .heightIn(min = 0.dp)
            .wrapContentHeight(),
        shape = CardDefaults.elevatedShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentSectionTitle(text = "Risk flags", accentColors = accentColors)
            if (reasons.isEmpty()) {
                Text(
                    "No flagged sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                DailyBadgeFlowRow {
                    if (NearworkRiskReason.CloseDistance in reasons) {
                        DailyBadge(text = "Close distance", accentColors = accentColors)
                    }
                    if (NearworkRiskReason.LowLight in reasons) {
                        DailyBadge(text = "Low light", accentColors = accentColors)
                    }
                    if (NearworkRiskReason.ExtremeClose in reasons) {
                        DailyBadge(text = "Extreme close", accentColors = accentColors)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearworkTimelineBar(
    sessions: List<NearworkSession>,
    onTapSession: (NearworkSession) -> Unit
) {
    val barHeight = 14.dp
    val corner = 8.dp
    val dayMillis = 24L * 60L * 60L * 1000L

    val firstStart = sessions.minOfOrNull { it.startTimestampMillis }
    val dayStart = firstStart?.let { ts ->
        Instant.ofEpochMilli(ts).atOffset(ZoneOffset.UTC).toLocalDate().atTime(LocalTime.MIDNIGHT)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    val background = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.primary

    val sessionRects = remember(sessions, dayStart) {
        val start = dayStart ?: 0L
        sessions.map { s ->
            val x0 = ((s.startTimestampMillis - start).toDouble() / dayMillis.toDouble()).coerceIn(0.0, 1.0)
            val x1 = ((s.endTimestampMillis - start).toDouble() / dayMillis.toDouble()).coerceIn(0.0, 1.0)
            s to (x0 to x1)
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(sessionRects) {
                detectTapGestures { offset ->
                    val x = (offset.x / size.width).coerceIn(0f, 1f)
                    sessionRects.firstOrNull { (_, range) ->
                        val (x0, x1) = range
                        x.toDouble() in x0..x1
                    }?.first?.let(onTapSession)
                }
            }
    ) {
        val hPx = barHeight.toPx()
        val y = (size.height - hPx) / 2f
        drawRoundRect(
            color = background,
            topLeft = Offset(0f, y),
            size = Size(size.width, hPx),
            cornerRadius = CornerRadius(corner.toPx(), corner.toPx())
        )

        for ((session, range) in sessionRects) {
            val (x0, x1) = range
            val left = (x0 * size.width).toFloat()
            val right = (x1 * size.width).toFloat().coerceAtLeast(left + 2f)
            drawRoundRect(
                color = highlight,
                topLeft = Offset(left, y),
                size = Size(right - left, hPx),
                cornerRadius = CornerRadius(corner.toPx(), corner.toPx())
            )
        }
    }
}

@Composable
private fun DiopterHoursCard(
    diopterHoursTotal: Double,
    nrs: Double,
    meanLuxDuringNearwork: Double?,
    lowLightMinutes: Int,
    onOpenNrsInfo: () -> Unit
) {
    val accentColors = dailyAccentColors()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.elevatedShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // I keep the British spelling here so this card matches the thesis language.
                Text(
                    text = "Dioptre-hours",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColors.accent
                )
            }

            Text(
                text = "${format2(diopterHoursTotal)} D·h",
                style = MaterialTheme.typography.headlineMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Nearwork Risk Score: ${format2(nrs)}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(
                    onClick = onOpenNrsInfo,
                    modifier = Modifier.size(20.dp)
                ) {
                    // I keep the note one tap away because people will otherwise read NRS like a raw accumulation.
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = "Explain NRS normalisation")
                }
            }

            val lowLightLabel = if (lowLightMinutes > 0) "$lowLightMinutes min in low light" else "No low-light exposure detected"
            Text(
                text = listOfNotNull(
                    lowLightLabel,
                    meanLuxDuringNearwork?.let { "Nearwork lux avg ${formatValue(it)}" }
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // I keep the two user-facing lux anchors visible here so the average has immediate context.
                text = "< 55 lux: low-light  |  ≥ 3,000 lux: outdoor protective.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class DistributionRow(
    val label: String,
    val fraction: Double,
    val colorTone: DistributionColorTone
)

private enum class DistributionColorTone {
    Error,
    Tertiary,
    Secondary,
    Primary
}

@Composable
private fun DailyDistributionSection(
    title: String,
    rows: List<DistributionRow>,
    emptyMessage: String,
    accentColors: DailyAccentColors
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccentSectionTitle(text = title, accentColors = accentColors)
            if (rows.isEmpty()) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                rows.forEach { row ->
                    DistributionRowItem(row = row)
                }
            }
        }
    }
}

@Composable
private fun DistributionRowItem(row: DistributionRow) {
    val barColor = when (row.colorTone) {
        DistributionColorTone.Error -> MaterialTheme.colorScheme.error
        DistributionColorTone.Tertiary -> MaterialTheme.colorScheme.tertiary
        DistributionColorTone.Secondary -> MaterialTheme.colorScheme.secondary
        DistributionColorTone.Primary -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium)
            Text("${(row.fraction * 100.0).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {}
            Surface(
                modifier = Modifier
                    .fillMaxWidth(row.fraction.toFloat().coerceIn(0f, 1f))
                    .fillMaxSize(),
                color = barColor,
                shape = MaterialTheme.shapes.small
            ) {}
        }
    }
}

@Composable
private fun SessionDetailCard(session: DailySessionUiModel) {
    val accentColors = dailyAccentColors()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatRangeHm(session.session.startTimestampMillis, session.session.endTimestampMillis),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    formatDuration(session.session.durationSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Mean distance: ${formatValue(session.session.avgDistanceCm)} cm", style = MaterialTheme.typography.bodyMedium)
            Text("D·h: ${format2(session.session.diopterHoursInSession)}", style = MaterialTheme.typography.bodyMedium)
            Text("NRS: ${if (session.nrs > 0.0) format1(session.nrs) else "—"}", style = MaterialTheme.typography.bodyMedium)
            Text("Mean lux: ${session.meanLux?.let { formatValue(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            if (session.reasons.isNotEmpty()) {
                DailyBadgeFlowRow {
                    if (NearworkRiskReason.CloseDistance in session.reasons) {
                        DailyBadge(text = "Close distance", accentColors = accentColors)
                    }
                    if (NearworkRiskReason.LowLight in session.reasons) {
                        DailyBadge(text = "Low light", accentColors = accentColors)
                    }
                    if (NearworkRiskReason.ExtremeClose in session.reasons) {
                        DailyBadge(text = "Extreme close", accentColors = accentColors)
                    }
                }
            }
        }
    }
}

private fun distanceZoneRows(samples: List<NearworkSample>): List<DistributionRow> {
    val valid = samples.filter { sample ->
        sample.distanceCm.isFinite()
    }
    if (valid.isEmpty()) return emptyList()
    val total = valid.size.toDouble()
    val extremeCloseCm = AppConstants.ZONE_EXTREME_CLOSE_CM
    val closeCm = AppConstants.ZONE_CLOSE_CM
    val moderateCm = AppConstants.ZONE_MODERATE_CM
    return listOf(
        DistributionRow(
            "< ${extremeCloseCm.toInt()} cm",
            valid.count { it.distanceCm < extremeCloseCm } / total,
            DistributionColorTone.Error
        ),
        DistributionRow(
            "${extremeCloseCm.toInt()}–${closeCm.toInt()} cm",
            valid.count { it.distanceCm >= extremeCloseCm && it.distanceCm < closeCm } / total,
            DistributionColorTone.Tertiary
        ),
        DistributionRow(
            "${closeCm.toInt()}–${moderateCm.toInt()} cm",
            valid.count { it.distanceCm >= closeCm && it.distanceCm < moderateCm } / total,
            DistributionColorTone.Secondary
        ),
        DistributionRow(
            "≥ ${moderateCm.toInt()} cm",
            valid.count { it.distanceCm >= moderateCm } / total,
            DistributionColorTone.Primary
        )
    )
}

private fun lightingConditionRows(samples: List<NearworkSample>): List<DistributionRow> {
    val valid = samples.filter { sample ->
        sample.lux.isFinite()
    }
    if (valid.isEmpty()) return emptyList()
    val total = valid.size.toDouble()
    val lowLightLux = AppConstants.LUX_TIER_1
    val outdoorLux = AppConstants.LUX_TIER_4
    return listOf(
        DistributionRow(
            "Low-light (≤ ${lowLightLux.toInt()} lux)",
            valid.count { it.lux <= lowLightLux } / total,
            DistributionColorTone.Error
        ),
        DistributionRow(
            "Indoor (${lowLightLux.toInt() + 1} – ${outdoorLux.toInt() - 1} lux)",
            valid.count { it.lux > lowLightLux && it.lux < outdoorLux } / total,
            DistributionColorTone.Secondary
        ),
        DistributionRow(
            "Outdoor (≥ ${outdoorLux.toInt()} lux)",
            valid.count { it.lux >= outdoorLux } / total,
            DistributionColorTone.Primary
        )
    )
}

@Composable 
private fun MetricCard( 
    modifier: Modifier = Modifier, 
    title: String, 
    headline: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColors: DailyAccentColors
) {
    ElevatedCard(
        modifier = modifier,
        shape = CardDefaults.elevatedShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColors.accent)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColors.accent,
                    maxLines = 1
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun DailyInsightsCard(bullets: List<String>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardDefaults.elevatedShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            bullets.forEach { bullet ->
                InsightsBulletRow(text = bullet)
            }

            Text(
                text = "Educational only - not medical advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun InsightsBulletRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun QualityBadges(
    modifier: Modifier = Modifier,
    onGoToImport: () -> Unit,
    accentColors: DailyAccentColors
) {
    ElevatedCard(
        modifier = modifier.wrapContentHeight(),
        shape = CardDefaults.elevatedShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = accentColors.accent
                )
                Text(
                    text = "Quality",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColors.accent
                )
            }
            DailyBadgeFlowRow {
                DailyBadge(text = "Imported", accentColors = accentColors)
                DailyBadge(text = "Profile 1", accentColors = accentColors)
                AssistChip(
                    onClick = onGoToImport,
                    label = { Text("Local only", maxLines = 2, textAlign = TextAlign.Center) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = accentColors.accent.copy(alpha = 0.14f),
                        labelColor = accentColors.accent
                    )
                )
            }
        }
    }
}

@Composable
private fun AccentSectionTitle(
    text: String,
    accentColors: DailyAccentColors
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accentColors.accent
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyBadgeFlowRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun DailyBadge(
    text: String,
    accentColors: DailyAccentColors
) {
    Surface(
        color = accentColors.accent.copy(alpha = 0.14f),
        contentColor = accentColors.accent,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun dailyAccentColors(): DailyAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        DailyAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        DailyAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class DailyAccentColors(
    val accent: Color,
    val onAccent: Color
)

private fun formatRange(value: Double?): String {
    return formatNumber(value, "%.1f")
}

private fun formatValue(value: Double?): String {
    return formatNumber(value, "%.0f")
}

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatNumber(value: Double?, format: String): String {
    val safe = value?.takeUnless { it.isNaN() || it.isInfinite() }
    return safe?.let { String.format(Locale.US, format, it) } ?: "—"
}

private fun formatTime(iso: String): String {
    return runCatching {
        val parsed = LocalDateTime.parse(iso.replace(" ", "T"))
        parsed.format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("—")
}

private fun formatDate(day: String): String {
    return runCatching {
        LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(day)
}

private fun formatDuration(durationSeconds: Long): String {
    val totalMinutes = durationSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatRangeHm(startMillis: Long, endMillis: Long): String {
    val start = Instant.ofEpochMilli(startMillis).atOffset(ZoneOffset.UTC).toLocalTime()
    val end = Instant.ofEpochMilli(endMillis).atOffset(ZoneOffset.UTC).toLocalTime()
    return "${start.format(DateTimeFormatter.ofPattern("HH:mm"))}–${end.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private val dayNavigationFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun DailyScreenPreview() {
    val summary = DailySummary(
        day = "2025-12-16",
        sampleCount = 1200,
        avgDistanceCm = 75.3,
        minDistanceCm = 11.0,
        maxDistanceCm = 185.2,
        avgLux = 320.0,
        minLux = 12.0,
        maxLux = 1000.0,
        diopterHoursTotal = 6.85,
        lowLightMinutes = 42,
        firstTimestampIso = "2025-12-16T07:00:00",
        lastTimestampIso = "2025-12-16T19:30:00"
    )
    val insights = DailySessionInsights(
        sessions = emptyList(),
        longestSession = null,
        flaggedSessions = emptyList()
    )
    NearworkTheme {
        DailyContent(
            summary = summary,
            sampleCount = summary.sampleCount,
            avgDistanceCm = summary.avgDistanceCm,
            processedSamples = emptyList(),
            sessionInsights = insights,
            sessions = emptyList(),
            nrsResult = com.example.nearworkthesis.domain.analysis.NrsResult(
                nrs = 12.48,
                sampleCount = summary.sampleCount,
                meanLuxDuringNearwork = 280.0
            ),
            analysisConfig = AnalysisConfig(
                thresholds = AnalysisThresholds(
                    // I preview the thesis-facing default here so the sample screen mirrors the real first-launch state.
                    lowLightThresholdLux = 55,
                    nearworkDistanceThresholdCm = 60,
                    breakGapSeconds = 60,
                    minSessionDurationSeconds = 60,
                    closeDistanceThresholdCm = 30,
                    extremeCloseThresholdCm = 20
                ),
                pipeline = AnalysisPipelineConfig(
                    smoothingWindowSize = 5,
                    dedupeRule = "same timestamp keep last",
                    distanceRangeMinCm = 10.0,
                    distanceRangeMaxCm = 200.0,
                    luxRangeMin = 0.0,
                    luxRangeMax = 50_000.0,
                    gapThresholdSeconds = 60
                ),
                timeHandling = AnalysisTimeHandling(
                    timezoneId = "UTC",
                    statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
                )
            ),
            measurementRepository = null,
            activeProfileId = null,
            howfarUf2Archive = null,
            isCurrentDay = true,
            onGoToImport = {},
            onOpenAnalysis = {},
            onRequestDelete = {}
        )
    }
}















