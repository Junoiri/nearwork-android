package com.example.nearworkthesis.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    measurementRepository: MeasurementRepository,
    onSelectDay: (String) -> Unit,
    onInspectDay: (String) -> Unit,
    onGoToImport: () -> Unit
) {
    val activeProfileStore = LocalActiveProfileStore.current
    val profileRepository = LocalProfileRepository.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeProfileStore
        )
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    val activeProfileName = remember(state) {
        val dataState = state as? HistoryUiState.Data
        val profiles = dataState?.profiles.orEmpty()
        val activeProfileId = dataState?.activeProfileId
        profiles.firstOrNull { it.id == activeProfileId }?.name ?: profiles.firstOrNull()?.name ?: "Profile"
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryUiEvent.DayDeleted -> {
                    snackbarHostState.showSnackbar("Deleted data for ${event.localDay}")
                }
                is HistoryUiEvent.DeleteFailed -> {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state) {
                is HistoryUiState.Loading -> HistoryLoading()
                is HistoryUiState.Empty -> HistoryEmpty(onGoToImport = onGoToImport)
                is HistoryUiState.Error -> HistoryError(
                    message = (state as HistoryUiState.Error).message,
                    onRetry = { viewModel.retry() }
                )
                is HistoryUiState.Data -> HistoryContent(
                    data = state as HistoryUiState.Data,
                    onSelectDay = onSelectDay,
                    onInspectDay = onInspectDay,
                    onModeChange = viewModel::setMode,
                    onPrevMonth = viewModel::goToPreviousMonth,
                    onNextMonth = viewModel::goToNextMonth,
                    onEmptyDay = {
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "No data for this day",
                                actionLabel = "Import data"
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onGoToImport()
                            }
                        }
                    },
                    onRequestDelete = { day -> deleteTarget = day }
                )
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
                        viewModel.deleteDay(day)
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
private fun HistoryContent(
    data: HistoryUiState.Data,
    onSelectDay: (String) -> Unit,
    onInspectDay: (String) -> Unit,
    onModeChange: (HistoryViewMode) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEmptyDay: () -> Unit,
    onRequestDelete: (String) -> Unit
) {
    val accentColors = historyAccentColors()
    var selectedCalendarSummary by remember { mutableStateOf<MonthDaySummary?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryHeader()
        HistoryModeToggle(mode = data.mode, onModeChange = onModeChange)

        if (data.mode == HistoryViewMode.Calendar) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MonthHeader(
                        label = data.calendar.monthLabel,
                        onPrev = onPrevMonth,
                        onNext = onNextMonth
                    )
                }
                item {
                    WeekdayRow()
                }
                item {
                    HistoryCalendarGrid(
                        month = data.calendar.month,
                        daySummaries = data.calendar.daySummaries,
                        onShowDaySummary = { summary -> selectedCalendarSummary = summary },
                        onEmptyDay = onEmptyDay,
                        onInspectDay = onInspectDay,
                        onRequestDelete = onRequestDelete
                    )
                }
                if (data.calendar.daySummaries.isEmpty()) {
                    item {
                        Text(
                            text = "No entries this month",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            HistoryList(
                days = data.days,
                onSelectDay = onSelectDay,
                onInspectDay = onInspectDay,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    selectedCalendarSummary?.let { summary ->
        HistoryDaySummaryDialog(
            summary = summary,
            accentColors = accentColors,
            onDismiss = { selectedCalendarSummary = null },
            onOpenDay = {
                selectedCalendarSummary = null
                onSelectDay(summary.day)
            },
            onInspectDay = {
                selectedCalendarSummary = null
                onInspectDay(summary.day)
            }
        )
    }
}

@Composable
private fun HistoryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Browse your recorded days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryModeToggle(
    mode: HistoryViewMode,
    onModeChange: (HistoryViewMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == HistoryViewMode.Calendar,
            onClick = { onModeChange(HistoryViewMode.Calendar) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text("Calendar")
        }
        SegmentedButton(
            selected = mode == HistoryViewMode.List,
            onClick = { onModeChange(HistoryViewMode.List) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text("List")
        }
    }
}

@Composable
private fun MonthHeader(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayRow() {
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun HistoryCalendarGrid(
    month: YearMonth,
    daySummaries: Map<String, MonthDaySummary>,
    onShowDaySummary: (MonthDaySummary) -> Unit,
    onEmptyDay: () -> Unit,
    onInspectDay: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    val cells = rememberCalendarCells(month)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val date = cells[row * 7 + col]
                    HistoryCalendarCell(
                        date = date,
                        summary = date?.let { daySummaries[it.toString()] },
                        onShowDaySummary = onShowDaySummary,
                        onEmptyDay = onEmptyDay,
                        onInspectDay = onInspectDay,
                        onRequestDelete = onRequestDelete,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCalendarCell(
    date: LocalDate?,
    summary: MonthDaySummary?,
    onShowDaySummary: (MonthDaySummary) -> Unit,
    onEmptyDay: () -> Unit,
    onInspectDay: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (date == null) {
        Box(modifier = modifier.defaultMinSize(minHeight = 64.dp))
        return
    }
    val dayKey = date.toString()
    val hasData = summary != null
    val containerColor = if (hasData) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val dayColor = if (hasData) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .clickable {
                if (hasData) {
                    summary?.let(onShowDaySummary)
                } else {
                    onEmptyDay()
                }
            },
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = dayColor
                )
                if (hasData) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = { onInspectDay(dayKey) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QueryStats,
                                contentDescription = "Inspect raw vs processed",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(28.dp),
                            onClick = { onRequestDelete(dayKey) }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete day",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (hasData) {
                val status = resolveStatus(summary)
                if (status != null) {
                    StatusPill(label = status.label, containerColor = status.containerColor, contentColor = status.contentColor)
                }
            }
        }
    }
}

@Composable
private fun HistoryDaySummaryDialog(
    summary: MonthDaySummary,
    accentColors: HistoryAccentColors,
    onDismiss: () -> Unit,
    onOpenDay: () -> Unit,
    onInspectDay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${formatDayName(summary.day)} • ${formatDate(summary.day)}",
                color = accentColors.accent,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${summary.sampleCount} samples",
                    style = MaterialTheme.typography.titleMedium
                )
                HistoryDialogStatRow(label = "Dioptre-hours", value = formatMetricOrDash(summary.diopterHoursTotal))
                HistoryDialogStatRow(label = "NRS", value = formatMetricOrDash(summary.nrs))
                HistoryDialogStatRow(label = "Low-light minutes", value = summary.lowLightMinutes.toString())
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenDay) {
                    Text(
                        "Open day",
                        color = accentColors.accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                TextButton(onClick = onInspectDay) {
                    Text(
                        "Inspect",
                        color = accentColors.accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        "Close",
                        color = accentColors.accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun HistoryDialogStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private data class DayStatus(val label: String, val containerColor: androidx.compose.ui.graphics.Color, val contentColor: androidx.compose.ui.graphics.Color)

@Composable
private fun resolveStatus(summary: MonthDaySummary): DayStatus? {
    return when {
        summary.lowLightMinutes > 0 -> DayStatus(
            label = "Low",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> null
    }
}

@Composable
private fun StatusPill(
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HistoryLoading() {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.padding(8.dp))
        Text("Loading history...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HistoryEmpty(onGoToImport: () -> Unit) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text("No history yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.padding(4.dp))
        Text(
            "Import sample data to see daily summaries.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = onGoToImport) {
            Text("Go to Import")
        }
    }
}

@Composable
private fun HistoryError(
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
            // I use a plain warning here so we do not imply missing cloud connectivity in a local-only app.
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text("Unable to load history", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.padding(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = onRetry) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun HistoryList(
    days: List<HistoryDaySummary>,
    onSelectDay: (String) -> Unit,
    onInspectDay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(days) { day ->
            HistoryCard(day = day, onSelectDay = onSelectDay, onInspectDay = onInspectDay)
        }
    }
}

@Composable
private fun HistoryDayHeader(
    day: String,
    accentColors: HistoryAccentColors
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = formatDayName(day),
            style = MaterialTheme.typography.titleLarge,
            color = accentColors.accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatFullDate(day),
            style = MaterialTheme.typography.titleMedium,
            color = accentColors.accent
        )
    }
}

@Composable
private fun HistoryCard(
    day: HistoryDaySummary,
    onSelectDay: (String) -> Unit,
    onInspectDay: (String) -> Unit
) {
    val accentColors = historyAccentColors()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectDay(day.day) },
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                HistoryDayHeader(day = day.day, accentColors = accentColors)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { onSelectDay(day.day) },
                        label = { Text("${day.sampleCount} samples") }
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
            Text(
                // I surface the two summary metrics here because history rows need the same exposure context as daily and weekly cards.
                text = "D·h: ${formatMetricOrDash(day.diopterHoursTotal)} | NRS: ${formatMetricOrDash(day.nrs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = day.firstTimestampIso?.let { "First: ${formatTime(it)}" } ?: "First: —",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = day.lastTimestampIso?.let { "Last: ${formatTime(it)}" } ?: "Last: —",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun rememberCalendarCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()
    val offset = firstDay.dayOfWeek.value - 1
    val totalCells = 42
    return List(totalCells) { index ->
        val dayNumber = index - offset + 1
        if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
    }
}

private fun formatDate(day: String): String {
    return runCatching {
        LocalDate.parse(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(day)
}

private fun formatDayName(day: String): String {
    return runCatching {
        LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    }.getOrDefault(day)
}

private fun formatFullDate(day: String): String {
    return runCatching {
        LocalDate.parse(day).format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    }.getOrDefault(day)
}

private fun formatTime(iso: String): String {
    return runCatching {
        val normalized = iso.replace(" ", "T")
        val dateTime = java.time.LocalDateTime.parse(normalized)
        val dateLabel = dateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        val timeLabel = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        "$dateLabel $timeLabel"
    }.getOrDefault("—")
}

private fun formatMetricOrDash(value: Double): String {
    return if (value > 0.0 && value.isFinite()) String.format(Locale.US, "%.2f", value) else "—"
}

@Composable
private fun historyAccentColors(): HistoryAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        HistoryAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        HistoryAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class HistoryAccentColors(
    val accent: Color,
    val onAccent: Color
)

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val sample = listOf(
        HistoryDaySummary(
            day = "2025-12-16",
            sampleCount = 1200,
            avgDistanceCm = 75.0,
            avgLux = 300.0,
            diopterHoursTotal = 9.10,
            nrs = 48.72,
            firstTimestampIso = "2025-12-16T07:00:00",
            lastTimestampIso = "2025-12-16T19:00:00"
        )
    )
    val calendar = HistoryCalendarState(
        month = YearMonth.of(2025, 12),
        monthLabel = "December 2025",
        daySummaries = mapOf(
            "2025-12-16" to MonthDaySummary(
                day = "2025-12-16",
                sampleCount = 1200,
                diopterHoursTotal = 9.1,
                lowLightMinutes = 12,
                nrs = 48.72
            )
        )
    )
    NearworkTheme {
        HistoryContent(
            data = HistoryUiState.Data(
                mode = HistoryViewMode.Calendar,
                days = sample,
                calendar = calendar,
                analysisConfig = AnalysisConfig(
                    thresholds = com.example.nearworkthesis.domain.analysis.AnalysisThresholds(
                        lowLightThresholdLux = 300,
                        nearworkDistanceThresholdCm = 60,
                        breakGapSeconds = 120,
                        minSessionDurationSeconds = 300,
                        closeDistanceThresholdCm = 30,
                        extremeCloseThresholdCm = 20
                    ),
                    pipeline = com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig(
                        smoothingWindowSize = 5,
                        dedupeRule = "sliding",
                        distanceRangeMinCm = 10.0,
                        distanceRangeMaxCm = 250.0,
                        luxRangeMin = 0.0,
                        luxRangeMax = 10000.0,
                        gapThresholdSeconds = 300
                    ),
                    timeHandling = com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling(
                        timezoneId = "UTC",
                        statement = "Local device time"
                    )
                ),
                profiles = emptyList(),
                activeProfileId = null
            ),
            onSelectDay = {},
            onInspectDay = {},
            onModeChange = {},
            onPrevMonth = {},
            onNextMonth = {},
            onEmptyDay = {},
            onRequestDelete = {}
        )
    }
}















