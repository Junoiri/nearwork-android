package com.example.nearworkthesis.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalHowfarUf2Archive
import com.example.nearworkthesis.app.LocalNearworkRiskScoreCalculator
import com.example.nearworkthesis.core.ui.components.AppScaffold
import com.example.nearworkthesis.core.ui.components.QuickExportCard
import com.example.nearworkthesis.core.ui.components.QuickExportFormat
import com.example.nearworkthesis.core.ui.components.QuickExportSeries
import com.example.nearworkthesis.core.ui.components.QuickExportState
import com.example.nearworkthesis.core.ui.theme.BabyBlueIce
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.core.util.writeBytesToDownloads
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.importing.howfar.HowfarUf2Archive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DataAnalysisScreen(
    modifier: Modifier = Modifier,
    measurementRepository: com.example.nearworkthesis.domain.repository.MeasurementRepository,
    selectedDate: String?,
    onGoToImport: () -> Unit = {},
    onBack: () -> Unit
) {
    val activeProfileStore = LocalActiveProfileStore.current
    val nearworkRiskScoreCalculator = LocalNearworkRiskScoreCalculator.current
    val viewModel: DataAnalysisViewModel = viewModel(
        factory = DataAnalysisViewModel.factory(
            measurementRepository = measurementRepository,
            activeProfileStore = activeProfileStore,
            nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
            selectedDate = selectedDate
        )
    )
    val state by viewModel.uiState.collectAsState()

    val activeProfileId by activeProfileStore.observeActiveProfileId().collectAsState(initial = null)
    val howfarUf2Archive = LocalHowfarUf2Archive.current
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val dayLabel = remember(state) {
        val day = (state as? DataAnalysisUiState.Data)?.analysis?.day ?: selectedDate
        if (day.isNullOrBlank()) "--" else formatDate(day)
    }
    val debugDay = (state as? DataAnalysisUiState.Data)?.analysis?.day ?: selectedDate

    AppScaffold(
        title = "Raw vs Processed",
        showBack = true,
        onBack = onBack,
        debugOverlayDate = debugDay,
        actions = {
            AssistChip(
                onClick = {},
                label = { Text(dayLabel) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Outlined.History, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is DataAnalysisUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(modifier = Modifier.size(16.dp))
                        Text("Preparing validation view...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is DataAnalysisUiState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        Text("No samples for this day", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            "Import data to inspect raw vs processed timelines.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(24.dp))
                        androidx.compose.material3.Button(onClick = onGoToImport) {
                            Text("Go to Import")
                        }
                    }
                }
                is DataAnalysisUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (state as DataAnalysisUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is DataAnalysisUiState.Data -> {
                    DataAnalysisContent(
                        analysis = (state as DataAnalysisUiState.Data).analysis,
                        sessionInsights = (state as DataAnalysisUiState.Data).sessionInsights,
                        nrsResult = (state as DataAnalysisUiState.Data).nrsResult,
                        analysisConfig = (state as DataAnalysisUiState.Data).analysisConfig,
                        gapThresholdSeconds = (state as DataAnalysisUiState.Data).analysisConfig.pipeline.gapThresholdSeconds,
                        measurementRepository = measurementRepository,
                        activeProfileId = activeProfileId,
                        howfarUf2Archive = howfarUf2Archive,
                        onRequestDelete = { deleteTarget = it }
                    )
                }
            }
        }
    }

    deleteTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete day data?") },
            text = { Text("Delete measurements for $day. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val profileId = activeProfileId
                        deleteTarget = null
                        if (profileId == null) {
                            deleteError = "No active profile selected."
                            return@TextButton
                        }
                        scope.launch {
                            val deleted = runCatching {
                                measurementRepository.deleteDay(profileId, day)
                            }.getOrElse { -1 }
                            if (deleted > 0) {
                                onBack()
                            } else {
                                deleteError = "Unable to delete day data."
                            }
                        }
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

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text("OK") } },
            title = { Text("Delete failed") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun DataAnalysisContent(
    analysis: DataAnalysisDay,
    sessionInsights: DailySessionInsights,
    nrsResult: com.example.nearworkthesis.domain.analysis.NrsResult,
    analysisConfig: com.example.nearworkthesis.domain.analysis.AnalysisConfig,
    gapThresholdSeconds: Int,
    measurementRepository: com.example.nearworkthesis.domain.repository.MeasurementRepository,
    activeProfileId: Long?,
    howfarUf2Archive: HowfarUf2Archive,
    onRequestDelete: (String) -> Unit
) {
    var showRaw by remember { mutableStateOf(true) }
    var showProcessed by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentColors = dataAnalysisAccentColors()
    var exportFormat by remember { mutableStateOf(QuickExportFormat.Csv) }
    var exportSeries by remember { mutableStateOf(QuickExportSeries.Raw) }
    var exportState by remember { mutableStateOf<QuickExportState>(QuickExportState.Idle) }
    var zoomLevel by remember { mutableStateOf(1) }
    var cropToThreshold by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SessionRiskBadges(sessionInsights = sessionInsights)

        QuickExportCard(
            exportFormat = exportFormat,
            exportSeries = exportSeries,
            exportState = exportState,
            onSelectFormat = { exportFormat = it },
            onSelectSeries = { exportSeries = it },
            onDismissMessage = { exportState = QuickExportState.Idle },
            colors = com.example.nearworkthesis.core.ui.components.QuickExportCardColors(
                accent = accentColors.accent,
                onAccent = accentColors.onAccent,
                buttonAccent = accentColors.accent,
                onButtonAccent = accentColors.onAccent
            ),
            titleTextStyle = MaterialTheme.typography.titleLarge,
            onExport = {
                val profileId = activeProfileId
                if (profileId == null) {
                    exportState = QuickExportState.Error("No active profile selected.")
                    return@QuickExportCard
                }
                exportState = QuickExportState.Exporting
                scope.launch {
                    exportState = runCatching {
                        when (exportFormat) {
                            QuickExportFormat.Csv -> {
                                val csv = when (exportSeries) {
                                    QuickExportSeries.Raw -> measurementRepository.exportRawCsv(profileId, analysis.day, analysis.day)
                                    QuickExportSeries.Processed -> measurementRepository.exportProcessedCsv(
                                        profileId = profileId,
                                        startDay = analysis.day,
                                        endDay = analysis.day,
                                        config = analysisConfig
                                    )
                                }
                                val filename = if (exportSeries == QuickExportSeries.Raw) {
                                    "nearwork_raw_${analysis.day}.csv"
                                } else {
                                    "nearwork_processed_${analysis.day}.csv"
                                }
                                writeBytesToDownloads(context, filename, "text/csv", csv.toByteArray())
                                QuickExportState.Success(filename)
                            }
                            QuickExportFormat.Uf2 -> {
                                val snapshot = howfarUf2Archive.loadLatest(profileId)
                                    ?: return@runCatching QuickExportState.Error("No UF2 snapshot available yet.")
                                val filename = "howfar_${analysis.day}.uf2"
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                onClick = { showRaw = !showRaw },
                selected = showRaw,
                modifier = Modifier.height(40.dp),
                label = { Text("Raw", style = MaterialTheme.typography.titleSmall) }
            )
            Spacer(modifier = Modifier.width(10.dp))
            FilterChip(
                onClick = { showProcessed = !showProcessed },
                selected = showProcessed,
                modifier = Modifier.height(40.dp),
                label = { Text("Processed", style = MaterialTheme.typography.titleSmall) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                onClick = { cropToThreshold = !cropToThreshold },
                selected = cropToThreshold,
                modifier = Modifier.height(40.dp),
                label = { Text("Crop graph to threshold", style = MaterialTheme.typography.titleSmall) }
            )
        }

        DistanceTimelineChart(
            modifier = Modifier.fillMaxWidth(),
            rawSamples = if (showRaw) analysis.rawSamples else emptyList(),
            processedSamples = if (showProcessed) analysis.processedSamples else emptyList(),
            gapReferenceRawSamples = analysis.rawSamples,
            gapReferenceProcessedSamples = analysis.processedSamples,
            gapThresholdSeconds = gapThresholdSeconds,
            zoomLevel = zoomLevel,
            processedColor = accentColors.processedLine,
            onZoomIn = { if (zoomLevel < 20) zoomLevel += 1 },
            onZoomOut = { if (zoomLevel > 1) zoomLevel -= 1 },
            cropToThreshold = cropToThreshold,
            thresholdCm = analysisConfig.thresholds.closeDistanceThresholdCm.toDouble()
        )

        ValidationSummaryCard(analysis = analysis, nrsResult = nrsResult)

        TextButton(
            onClick = { onRequestDelete(analysis.day) },
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Delete day data")
        }
    }
}

@Composable
private fun SessionRiskBadges(sessionInsights: DailySessionInsights) {
    val totalSessions = sessionInsights.sessions.size
    val flaggedSessions = sessionInsights.flaggedSessions.size
    val longestDuration = sessionInsights.longestSession?.durationSeconds

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(onClick = {}, label = { Text("Sessions: $totalSessions") })
        AssistChip(onClick = {}, label = { Text("Risky sessions: $flaggedSessions") })
        if (longestDuration != null) {
            AssistChip(onClick = {}, label = { Text("Longest: ${formatDuration(longestDuration)}") })
        }
    }
}
@Composable
private fun ValidationSummaryCard(
    analysis: DataAnalysisDay,
    nrsResult: com.example.nearworkthesis.domain.analysis.NrsResult
) {
    val summary = analysis.summary
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Validation summary", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val gapsLabel = if (summary.gapCount == 0) "No gaps" else "Gaps detected"
                val gapsColors = if (summary.gapCount == 0) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                AssistChip(onClick = {}, label = { Text(gapsLabel) }, colors = gapsColors)

                val silentLabel = if (summary.silentDropCount == 0) "No silent drops" else "Silent drops: ${summary.silentDropCount}"
                val silentColors = if (summary.silentDropCount == 0) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                AssistChip(onClick = {}, label = { Text(silentLabel) }, colors = silentColors)
            }

            Text(
                text = "Raw samples: ${summary.rawSampleCount}    Processed: ${summary.processedSampleCount}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Rejected outliers: ${summary.rejectedOutliersCount}    Deduped: ${summary.dedupedCount}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Gaps: ${summary.gapCount}    Max gap: ${summary.maxGapSeconds}s",
                style = MaterialTheme.typography.bodyMedium
            )

            val avgRaw = summary.avgDistanceRawCm
            val avgProcessed = summary.avgDistanceProcessedCm
            val diff = summary.avgDistanceAbsDiffCm
            Text(
                text = "Avg distance raw: ${format1OrDash(avgRaw)} cm    processed: ${format1OrDash(avgProcessed)} cm",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Avg abs difference: ${format1OrDash(diff)} cm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Nearwork Risk Score: ${String.format(Locale.US, "%.2f", nrsResult.nrs)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DistanceTimelineChart(
    modifier: Modifier,
    rawSamples: List<NearworkSample>,
    processedSamples: List<NearworkSample>,
    gapReferenceRawSamples: List<NearworkSample>,
    gapReferenceProcessedSamples: List<NearworkSample>,
    gapThresholdSeconds: Int,
    zoomLevel: Int,
    processedColor: Color,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    cropToThreshold: Boolean,
    thresholdCm: Double
) {
    val maxPoints = 1500
    val rawFiltered = remember(rawSamples, cropToThreshold, thresholdCm) {
        if (cropToThreshold) rawSamples.filter { it.distanceCm >= thresholdCm } else rawSamples
    }
    val processedFiltered = remember(processedSamples, cropToThreshold, thresholdCm) {
        if (cropToThreshold) processedSamples.filter { it.distanceCm >= thresholdCm } else processedSamples
    }
    val rawHasData = rawFiltered.isNotEmpty()
    val processedHasData = processedFiltered.isNotEmpty()
    val gapThresholdMillis = gapThresholdSeconds.coerceAtLeast(0) * 1000L
    val gapIntervals = remember(gapReferenceRawSamples, gapReferenceProcessedSamples, gapThresholdMillis) {
        if (gapThresholdMillis <= 0L) {
            emptyList()
        } else {
            mergeGapIntervals(
                computeGapIntervals(gapReferenceRawSamples, gapThresholdMillis) +
                    computeGapIntervals(gapReferenceProcessedSamples, gapThresholdMillis)
            )
        }
    }

    val rawColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val labelColorArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val yLabelPaint = remember(labelColorArgb) {
        android.graphics.Paint().apply {
            color = labelColorArgb
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
    }
    val xLabelPaint = remember(labelColorArgb) {
        android.graphics.Paint().apply {
            color = labelColorArgb
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    ElevatedCard(
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distance timeline", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onZoomOut, enabled = zoomLevel > 1) {
                        Icon(imageVector = Icons.Filled.Remove, contentDescription = "Zoom out")
                    }
                    Text("${zoomLevel}x", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = onZoomIn, enabled = zoomLevel < 20) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = "Zoom in")
                    }
                }
            }

            if (rawFiltered.isEmpty() && processedFiltered.isEmpty()) {
                Text(
                    text = "No series selected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val density = LocalDensity.current
            val axisPaddingLeftPx = with(density) { 48.dp.toPx() }
            val axisPaddingBottomPx = with(density) { 28.dp.toPx() }
            val axisPaddingTopPx = with(density) { 8.dp.toPx() }
            val axisPaddingRightPx = with(density) { 12.dp.toPx() }

            val chartViewportHeight = 220.dp
            var viewportWidth by remember { mutableStateOf(0) }
            val allForScale = rawFiltered + processedFiltered
            val fullXMin = allForScale.minOf { it.timestampMillis }
            val fullXMax = allForScale.maxOf { it.timestampMillis }
            val rawDown = remember(rawFiltered, maxPoints) { downsampleByTimeBucket(rawFiltered, maxPoints) }
            val processedDown = remember(processedFiltered, maxPoints) { downsampleByTimeBucket(processedFiltered, maxPoints) }
            val allDown = rawDown + processedDown
            var yMin = allDown.minOf { it.distanceCm }.toFloat()
            val yMax = allDown.maxOf { it.distanceCm }.toFloat()
            if (cropToThreshold && thresholdCm > yMin) {
                yMin = thresholdCm.toFloat()
            }
            val xSpan = (fullXMax - fullXMin).coerceAtLeast(1L).toFloat()
            val ySpan = (yMax - yMin).takeIf { it > 0f } ?: 1f
            val yTickCount = (3 + (zoomLevel - 1)).coerceIn(3, 9)
            val xTickStepMinutes = remember(zoomLevel) { zoomLevelToMinuteStep(zoomLevel) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color = rawColor, label = "Raw", enabled = rawHasData)
                LegendDot(color = processedColor, label = "Processed", enabled = processedHasData)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartViewportHeight)
                    .onSizeChanged { size: IntSize -> viewportWidth = size.width }
                    .horizontalScroll(rememberScrollState())
            ) {
                val contentWidth = if (viewportWidth <= 0) {
                    (320 * zoomLevel).dp
                } else {
                    with(density) { (viewportWidth * zoomLevel).toDp() }
                }
                Canvas(
                    modifier = Modifier
                        .width(contentWidth)
                        .height(chartViewportHeight)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                        val all = rawDown + processedDown
                        if (all.isEmpty()) return@Canvas
                        val xMin = fullXMin
                        val xMax = fullXMax

                        val width = size.width
                        val height = size.height

                        val plotLeft = axisPaddingLeftPx
                        val plotRight = width - axisPaddingRightPx
                        val plotTop = axisPaddingTopPx
                        val plotBottom = height - axisPaddingBottomPx

                        fun xToPx(t: Long): Float {
                            val frac = ((t - xMin).toFloat() / xSpan).coerceIn(0f, 1f)
                            return plotLeft + frac * (plotRight - plotLeft)
                        }

                        fun yToPx(y: Double): Float {
                            val frac = (((y.toFloat() - yMin) / ySpan)).coerceIn(0f, 1f)
                            return plotBottom - frac * (plotBottom - plotTop)
                        }

                        // Axes
                        drawLine(
                            color = outlineColor,
                            start = Offset(plotLeft, plotBottom),
                            end = Offset(plotRight, plotBottom),
                            strokeWidth = 2f
                        )

                        // Grid + ticks
                        val yTicks = (0 until yTickCount).map { idx ->
                            if (yTickCount == 1) yMin else yMin + (ySpan * idx / (yTickCount - 1).toFloat())
                        }
                        yTicks.forEach { yTick ->
                            val yPx = yToPx(yTick.toDouble())
                            drawLine(
                                color = outlineVariantColor,
                                start = Offset(plotLeft, yPx),
                                end = Offset(plotRight, yPx),
                                strokeWidth = 1f
                            )
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    String.format(Locale.US, "%.0f", yTick),
                                    plotLeft - 6f,
                                    yPx + 8f,
                                    yLabelPaint
                                )
                            }
                        }

                        val xTicks = computeTimeTicks(
                            startMillis = xMin,
                            endMillis = xMax,
                            stepMinutes = xTickStepMinutes
                        )
                        val minTimeLabelSpacing = computeMinTimeLabelSpacing(
                            labelPaint = xLabelPaint,
                            zoomLevel = zoomLevel
                        )
                        var lastLabelX = Float.NEGATIVE_INFINITY
                        xTicks.forEach { t ->
                            val xPx = xToPx(t)
                            drawLine(
                                color = outlineVariantColor,
                                start = Offset(xPx, plotBottom),
                                end = Offset(xPx, plotBottom + 6f),
                                strokeWidth = 2f
                            )
                            val label = formatHm(t)
                            if (xPx - lastLabelX >= minTimeLabelSpacing) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        label,
                                        xPx,
                                        height - 4f,
                                        xLabelPaint
                                    )
                                }
                                lastLabelX = xPx
                            }
                        }

                        fun drawSeries(samples: List<NearworkSample>, color: Color) {
                            if (samples.size < 2) return
                            var path = Path()
                            var hasPoint = false
                            var prevTs: Long? = null
                            samples.forEach { sample ->
                                val x = xToPx(sample.timestampMillis)
                                val y = yToPx(sample.distanceCm)
                                if (!hasPoint) {
                                    path.moveTo(x, y)
                                    hasPoint = true
                                    prevTs = sample.timestampMillis
                                } else {
                                    val last = prevTs ?: sample.timestampMillis
                                    val shouldBreak = hasGapBetween(last, sample.timestampMillis, gapIntervals)
                                    if (shouldBreak) {
                                        drawPath(path = path, color = color, style = Stroke(width = 3f))
                                        path = Path()
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                    prevTs = sample.timestampMillis
                                }
                            }
                            if (hasPoint) {
                                drawPath(path = path, color = color, style = Stroke(width = 3f))
                            }
                        }

                        drawSeries(rawDown, rawColor)
                        drawSeries(processedDown, processedColor)
                    }
                }
            }

        }
    }

@Composable
private fun LegendDot(color: Color, label: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val dotColor = if (enabled) color else MaterialTheme.colorScheme.outlineVariant
        Canvas(modifier = Modifier.size(10.dp)) {
            drawOval(color = dotColor, size = Size(size.width, size.height))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun downsampleByTimeBucket(samples: List<NearworkSample>, maxPoints: Int): List<NearworkSample> {
    if (samples.size <= maxPoints) return samples
    val sorted = samples.sortedBy { it.timestampMillis }
    val first = sorted.first().timestampMillis
    val last = sorted.last().timestampMillis
    val span = (last - first).coerceAtLeast(1L)
    val bucketSizeMillis = (span.toDouble() / maxPoints.toDouble()).toLong().coerceAtLeast(1L)

    val output = ArrayList<NearworkSample>(maxPoints)
    var bucketStart = first
    var bucketEnd = bucketStart + bucketSizeMillis

    var sumTs = 0.0
    var sumDist = 0.0
    var sumLux = 0.0
    var count = 0

    fun flush() {
        if (count <= 0) return
        output.add(
            NearworkSample(
                timestampMillis = (sumTs / count.toDouble()).toLong(),
                distanceCm = sumDist / count.toDouble(),
                lux = sumLux / count.toDouble()
            )
        )
        sumTs = 0.0
        sumDist = 0.0
        sumLux = 0.0
        count = 0
    }

    var i = 0
    while (i < sorted.size) {
        val s = sorted[i]
        if (s.timestampMillis < bucketEnd) {
            sumTs += s.timestampMillis.toDouble()
            sumDist += s.distanceCm
            sumLux += s.lux
            count += 1
            i += 1
        } else {
            flush()
            bucketStart = bucketEnd
            bucketEnd = bucketStart + bucketSizeMillis
        }
    }
    flush()

    return if (output.size <= maxPoints) output else output.take(maxPoints)
}

private fun format1OrDash(value: Double?): String = value?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

private fun formatDuration(durationSeconds: Long): String {
    val totalMinutes = durationSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
private fun formatDate(day: String): String {
    return runCatching {
        LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(day)
}

private fun formatHm(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalTime()
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun computeTimeTicks(startMillis: Long, endMillis: Long, stepMinutes: Int): List<Long> {
    val stepMillis = stepMinutes.coerceAtLeast(1) * 60_000L
    val firstTick = (startMillis / stepMillis) * stepMillis
    val ticks = ArrayList<Long>()
    var current = firstTick
    while (current <= endMillis) {
        ticks += current
        current += stepMillis
    }
    if (ticks.isEmpty() || ticks.last() < endMillis) {
        ticks += endMillis
    }
    return ticks
}

private fun zoomLevelToMinuteStep(zoomLevel: Int): Int {
    val clampedZoom = zoomLevel.coerceIn(1, 20)
    val progress = (clampedZoom - 1) / 19f
    val step = kotlin.math.exp(kotlin.math.ln(60f) + progress * (kotlin.math.ln(1f) - kotlin.math.ln(60f)))
    return step.toInt().coerceAtLeast(1)
}

private fun computeMinTimeLabelSpacing(
    labelPaint: android.graphics.Paint,
    zoomLevel: Int
): Float {
    val labelWidth = labelPaint.measureText("00:00")
    val zoomPadding = when {
        zoomLevel >= 16 -> 38f
        zoomLevel >= 10 -> 30f
        zoomLevel >= 5 -> 24f
        else -> 18f
    }
    return (labelWidth + zoomPadding).coerceAtLeast(72f)
}

private data class DataAnalysisAccentColors(
    val accent: Color,
    val onAccent: Color,
    val processedLine: Color
)

@Composable
private fun dataAnalysisAccentColors(): DataAnalysisAccentColors {
    return if (isSystemInDarkTheme()) {
        DataAnalysisAccentColors(
            accent = LavenderVeil,
            onAccent = Graphite,
            processedLine = Periwinkle
        )
    } else {
        DataAnalysisAccentColors(
            accent = VintageGrape,
            onAccent = White,
            processedLine = BabyBlueIce
        )
    }
}

private data class GapInterval(val start: Long, val end: Long)

private fun computeGapIntervals(samples: List<NearworkSample>, thresholdMillis: Long): List<GapInterval> {
    if (samples.size < 2) return emptyList()
    val sorted = samples.sortedBy { it.timestampMillis }
    val gaps = ArrayList<GapInterval>()
    var prev = sorted.first().timestampMillis
    for (i in 1 until sorted.size) {
        val current = sorted[i].timestampMillis
        if (current - prev > thresholdMillis) {
            gaps.add(GapInterval(prev, current))
        }
        prev = current
    }
    return gaps
}

private fun mergeGapIntervals(intervals: List<GapInterval>): List<GapInterval> {
    if (intervals.isEmpty()) return emptyList()
    val sorted = intervals.sortedBy { it.start }
    val merged = ArrayList<GapInterval>()
    var current = sorted.first()
    for (i in 1 until sorted.size) {
        val next = sorted[i]
        if (next.start <= current.end) {
            current = current.copy(end = maxOf(current.end, next.end))
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

private fun hasGapBetween(start: Long, end: Long, gaps: List<GapInterval>): Boolean {
    if (gaps.isEmpty()) return false
    val from = minOf(start, end)
    val to = maxOf(start, end)
    return gaps.any { gap ->
        (gap.start in from..to) || (gap.end in from..to)
    }
}











