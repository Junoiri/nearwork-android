package com.example.nearworkthesis.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalMeasurementRepository
import com.example.nearworkthesis.app.LocalNearworkRiskScoreCalculator
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.BabyBlueIce
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun WeeklyScreen(
    modifier: Modifier = Modifier,
    onOpenDay: (String) -> Unit,
    onInspectDay: (String) -> Unit,
    onGoToImport: () -> Unit
) {
    val measurementRepository = LocalMeasurementRepository.current
    val activeProfileStore = LocalActiveProfileStore.current
    val nearworkRiskScoreCalculator = LocalNearworkRiskScoreCalculator.current
    val viewModel: WeeklyViewModel = viewModel(
        factory = WeeklyViewModel.factory(
            measurementRepository = measurementRepository,
            activeProfileStore = activeProfileStore,
            nearworkRiskScoreCalculator = nearworkRiskScoreCalculator
        )
    )
    val state by viewModel.uiState.collectAsState()
    var showWeekPicker by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is WeeklyUiState.Loading -> WeeklyLoading()
            is WeeklyUiState.Empty -> WeeklyEmpty(onGoToImport = onGoToImport)
            is WeeklyUiState.Error -> WeeklyError(
                message = (state as WeeklyUiState.Error).message,
                onRetry = { viewModel.retry() }
            )
            is WeeklyUiState.Data -> {
                val data = state as WeeklyUiState.Data
                Box(modifier = Modifier.fillMaxSize()) {
                    WeeklyContent(
                        days = data.days,
                        totalNrs = data.totalNrs,
                        selectedRange = data.selectedRange,
                        onOpenDay = onOpenDay,
                        onInspectDay = onInspectDay,
                        onOpenWeekPicker = { showWeekPicker = true },
                        analysisConfig = data.analysisConfig
                    )

                    WeeklyWeekPickerOverlay(
                        visible = showWeekPicker,
                        selectedRange = data.selectedRange,
                        ranges = data.availableRanges,
                        onSelectRange = { range ->
                            viewModel.selectRange(range)
                            showWeekPicker = false
                        },
                        onShiftWeek = { offsetWeeks ->
                            val newEnd = data.selectedRange.end.plusDays(offsetWeeks * 7L)
                            viewModel.selectRange(
                                WeekRange(start = newEnd.minusDays(6), end = newEnd)
                            )
                        },
                        onDismiss = { showWeekPicker = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(16.dp))
        Text("Loading weekly overview...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WeeklyEmpty(onGoToImport: () -> Unit) {
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
        Text("Nothing this week", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            "Import data to see your last 7 days at a glance.",
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
private fun WeeklyError(
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
            // I avoid cloud imagery here because this screen is fully local and the error is not a sync failure.
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text("Unable to load weekly overview", style = MaterialTheme.typography.titleMedium)
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
private fun WeeklyContent(
    days: List<WeeklyDaySummary>,
    totalNrs: Double,
    selectedRange: WeekRange,
    onOpenDay: (String) -> Unit,
    onInspectDay: (String) -> Unit,
    onOpenWeekPicker: () -> Unit,
    analysisConfig: AnalysisConfig
) {
    val accentColors = weeklyAccentColors()
    val rangeLabel = remember(selectedRange) { weeklyRangeLabel(selectedRange) }
    val chartDays = remember(days, selectedRange) { buildWeeklyChartDays(days, selectedRange) }
    val totalSamples = remember(days) { days.sumOf { it.sampleCount } }
    val totalDiopterHours = remember(days) { days.sumOf { it.diopterHoursTotal } }
    val recordedDays = remember(days) { days.size }
    val avgDistance = remember(days) { weightedAverage(days) { it.avgDistanceCm } }
    val avgLux = remember(days) { weightedAverage(days) { it.avgLux } }
    var selectedTabIndex by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Weekly",
                            style = MaterialTheme.typography.headlineMedium,
                            color = accentColors.accent
                        )
                        Text(
                            "Your last 7 days overview",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = onOpenWeekPicker,
                        label = {
                            Text(
                                text = rangeLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
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
        }

        if (selectedTabIndex == 0) {
            item {
                WeeklyBarChart(
                    days = chartDays,
                    onOpenDay = onOpenDay,
                    title = "D-h per day",
                    maxLabelSuffix = "D-h",
                    accentColors = accentColors,
                    valueProvider = { it?.diopterHoursTotal ?: 0.0 }
                )
            }
            item {
                WeeklyBarChart(
                    days = chartDays,
                    onOpenDay = onOpenDay,
                    title = "NRS per day",
                    maxLabelSuffix = "NRS",
                    accentColors = accentColors,
                    valueProvider = { it?.nrs ?: 0.0 }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        // I keep the British spelling here so the dashboard matches the thesis wording.
                        title = "Dioptre-hours",
                        headline = format2(totalDiopterHours),
                        subtitle = "Total D-h",
                        accentColors = accentColors
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        title = "NRS",
                        headline = format2(totalNrs),
                        subtitle = "Weekly value",
                        accentColors = accentColors
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        title = "Days worn",
                        headline = recordedDays.toString(),
                        subtitle = "In range",
                        accentColors = accentColors
                    )
                }
            }
        } else {
            item {
                WeeklyBarChart(
                    days = chartDays,
                    onOpenDay = onOpenDay,
                    title = "D-h per day",
                    maxLabelSuffix = "D-h",
                    accentColors = accentColors,
                    valueProvider = { it?.diopterHoursTotal ?: 0.0 }
                )
            }

            item {
                WeeklyBarChart(
                    days = chartDays,
                    onOpenDay = onOpenDay,
                    title = "NRS per day",
                    maxLabelSuffix = "NRS",
                    accentColors = accentColors,
                    valueProvider = { it?.nrs ?: 0.0 }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        title = "Measurements",
                        headline = totalSamples.toString(),
                        subtitle = "Total",
                        accentColors = accentColors
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        title = "Distance",
                        headline = avgDistance?.let { format1(it) } ?: "-",
                        subtitle = "Avg cm",
                        accentColors = accentColors
                    )
                    SummaryCard(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .heightIn(min = 132.dp),
                        title = "Lux",
                        headline = avgLux?.let { format0(it) } ?: "-",
                        subtitle = "Avg",
                        accentColors = accentColors
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timeline,
                        contentDescription = null,
                        tint = accentColors.accent
                    )
                    Text(
                        "Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColors.accent
                    )
                }
            }

            items(days, key = { it.day }) { day ->
                WeeklyDayCard(day = day, onOpenDay = onOpenDay, onInspectDay = onInspectDay)
            }
        }
    }
}

@Composable
private fun WeeklyWeekPickerOverlay(
    visible: Boolean,
    selectedRange: WeekRange,
    ranges: List<WeekRange>,
    onSelectRange: (WeekRange) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onDismiss() }
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Select week", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onShiftWeek(-1) }) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronLeft,
                                    contentDescription = "Previous week"
                                )
                            }
                            Text(
                                weeklyRangeLabel(selectedRange),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { onShiftWeek(1) }) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "Next week"
                                )
                            }
                        }

                        if (ranges.isEmpty()) {
                            Text(
                                text = "No weeks available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(ranges) { range ->
                                    TextButton(
                                        onClick = { onSelectRange(range) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(weeklyRangeLabel(range))
                                            if (range == selectedRange) {
                                                Text(
                                                    text = "Selected",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(
    days: List<WeeklyChartDay>,
    onOpenDay: (String) -> Unit,
    title: String,
    maxLabelSuffix: String,
    accentColors: WeeklyAccentColors,
    valueProvider: (WeeklyDaySummary?) -> Double
) {
    val maxValueForSeries = days.maxOfOrNull { valueProvider(it.summary) } ?: 0.0
    val maxLabel = if (maxValueForSeries > 0.0) format1(maxValueForSeries) else "0"
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = if (isSystemInDarkTheme()) Periwinkle else BabyBlueIce
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColors.accent
            )
            Text(
                "Max $maxLabel $maxLabelSuffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val count = days.size.coerceAtLeast(1)
                val gapPx = 8.dp.toPx()
                val barWidth = ((size.width - gapPx * (count - 1)) / count.toFloat()).coerceAtLeast(0f)
                val maxValue = maxValueForSeries.takeIf { it > 0.0 } ?: 1.0

                drawLine(
                    color = baselineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )

                days.forEachIndexed { index, day ->
                    val value = valueProvider(day.summary).coerceAtLeast(0.0)
                    if (value <= 0.0) return@forEachIndexed
                    val heightPx = (value / maxValue).toFloat() * size.height
                    val left = index * (barWidth + gapPx)
                    val top = size.height - heightPx
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, heightPx),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                }
            }

            Row(modifier = Modifier.matchParentSize()) {
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(enabled = day.summary?.sampleCount ?: 0 > 0) {
                                onOpenDay(day.date.toString())
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val isToday = day.date == today
                Text(
                    text = formatChartDayLabel(day.date),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = accentColors.accent,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    headline: String,
    subtitle: String,
    accentColors: WeeklyAccentColors
) {
    ElevatedCard(
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColors.accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(headline, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.size(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyDayCard(
    day: WeeklyDaySummary,
    onOpenDay: (String) -> Unit,
    onInspectDay: (String) -> Unit
) {
    val accentColors = weeklyAccentColors()
    val dayNameLabel = remember(day.day) { formatDayName(day.day) }
    val fullDateLabel = remember(day.day) { formatLongDay(day.day) }
    val distanceLabel = day.avgDistanceCm?.let { "${format1(it)} cm" } ?: "-"
    val luxLabel = day.avgLux?.let { "${format0(it)} lux" } ?: "-"
    val diopterHoursLabel = "${format2(day.diopterHoursTotal)} D-h"

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .clickable { onOpenDay(day.day) },
        shape = CardDefaults.elevatedShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dayNameLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColors.accent
                    )
                    Text(
                        fullDateLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColors.accent
                    )
                    Text(
                        "${day.sampleCount} samples",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { onOpenDay(day.day) },
                        label = { Text("Open") }
                    )
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = { onInspectDay(day.day) }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QueryStats,
                            contentDescription = "Inspect raw vs processed"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "D-h: $diopterHoursLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Lux: $luxLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(6.dp))
            Text(
                "Distance: $distanceLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun weeklyRangeLabel(range: WeekRange): String {
    return "${formatShort(range.start)}-${formatShort(range.end)}"
}

private fun weightedAverage(
    days: List<WeeklyDaySummary>,
    value: (WeeklyDaySummary) -> Double?
): Double? {
    var weightedSum = 0.0
    var usedWeight = 0.0
    for (day in days) {
        val v = value(day) ?: continue
        val w = day.sampleCount.toDouble()
        if (w <= 0.0) continue
        weightedSum += v * w
        usedWeight += w
    }
    return if (usedWeight <= 0.0) null else weightedSum / usedWeight
}

private fun parseDayOrNull(day: String): LocalDate? =
    runCatching { LocalDate.parse(day) }.getOrNull()

private fun formatDay(day: String): String {
    val parsed = parseDayOrNull(day) ?: return day
    return parsed.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
}

private fun formatChartDayLabel(date: LocalDate): String =
    "${date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))}\n${date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}"

private fun formatDayName(day: String): String {
    val parsed = parseDayOrNull(day) ?: return day
    return parsed.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
}

private fun formatLongDay(day: String): String {
    val parsed = parseDayOrNull(day) ?: return day
    return parsed.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
}

private fun formatShort(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun format0(value: Double): String = String.format(Locale.US, "%.0f", value)

private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)

@Composable
private fun weeklyAccentColors(): WeeklyAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        WeeklyAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        WeeklyAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class WeeklyAccentColors(
    val accent: androidx.compose.ui.graphics.Color,
    val onAccent: androidx.compose.ui.graphics.Color
)

private data class WeeklyChartDay(
    val date: LocalDate,
    val summary: WeeklyDaySummary?
)

private fun resolveWeeklyWindowDays(range: WeekRange): List<LocalDate> {
    return List(7) { index -> range.start.plusDays(index.toLong()) }
}

private fun buildWeeklyChartDays(days: List<WeeklyDaySummary>, range: WeekRange): List<WeeklyChartDay> {
    val summariesByDay = days.mapNotNull { summary ->
        parseDayOrNull(summary.day)?.let { it to summary }
    }.toMap()
    return resolveWeeklyWindowDays(range).map { date ->
        WeeklyChartDay(date = date, summary = summariesByDay[date])
    }
}

@Preview(showBackground = true)
@Composable
private fun WeeklyScreenPreview() {
    val sample = listOf(
        WeeklyDaySummary(
            day = "2025-12-16",
            sampleCount = 850,
            avgDistanceCm = 70.4,
            avgLux = 280.0,
            diopterHoursTotal = 5.25,
            nrs = 44.10,
            lowLightMinutes = 18,
            firstTimestampIso = "2025-12-16T07:00:00",
            lastTimestampIso = "2025-12-16T19:30:00"
        ),
        WeeklyDaySummary(
            day = "2025-12-17",
            sampleCount = 1100,
            avgDistanceCm = 74.1,
            avgLux = 310.0,
            diopterHoursTotal = 9.10,
            nrs = 53.34,
            lowLightMinutes = 55,
            firstTimestampIso = "2025-12-17T07:10:00",
            lastTimestampIso = "2025-12-17T18:50:00"
        )
    )
    val selectedRange = WeekRange(
        start = LocalDate.of(2025, 12, 11),
        end = LocalDate.of(2025, 12, 17)
    )
    NearworkTheme {
        WeeklyContent(
            days = sample,
            totalNrs = 48.72,
            selectedRange = selectedRange,
            analysisConfig = AnalysisConfig(
                thresholds = com.example.nearworkthesis.domain.analysis.AnalysisThresholds(
                    lowLightThresholdLux = 300,
                    nearworkDistanceThresholdCm = 60,
                    breakGapSeconds = 60,
                    minSessionDurationSeconds = 60,
                    closeDistanceThresholdCm = 30,
                    extremeCloseThresholdCm = 20
                ),
                pipeline = com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig(
                    smoothingWindowSize = 5,
                    dedupeRule = "same timestamp keep last",
                    distanceRangeMinCm = 10.0,
                    distanceRangeMaxCm = 200.0,
                    luxRangeMin = 0.0,
                    luxRangeMax = 50_000.0,
                    gapThresholdSeconds = 60
                ),
                timeHandling = com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling(
                    timezoneId = "UTC",
                    statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
                )
            ),
            onOpenDay = {},
            onInspectDay = {},
            onOpenWeekPicker = {}
        )
    }
}










