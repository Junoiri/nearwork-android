package com.example.nearworkthesis.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalDiopterHoursCalculator
import com.example.nearworkthesis.app.LocalHowfarStorageRepository
import com.example.nearworkthesis.app.LocalMeasurementRepository
import com.example.nearworkthesis.app.LocalNearworkRiskScoreCalculator
import com.example.nearworkthesis.app.LocalNotificationHistoryRepository
import com.example.nearworkthesis.app.LocalProfileRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.core.ui.theme.LavenderVeil
import com.example.nearworkthesis.core.ui.theme.VintageGrape
import com.example.nearworkthesis.core.ui.theme.White
import com.example.nearworkthesis.domain.notifications.LastNotification
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    importViewModel: ImportViewModel,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val measurementRepository = LocalMeasurementRepository.current
    val profileRepository = LocalProfileRepository.current
    val activeProfileStore = LocalActiveProfileStore.current
    val diopterHoursCalculator = LocalDiopterHoursCalculator.current
    val nearworkRiskScoreCalculator = LocalNearworkRiskScoreCalculator.current
    val notificationHistoryRepository = LocalNotificationHistoryRepository.current
    val howfarStorageRepository = LocalHowfarStorageRepository.current
    val notificationViewModel: HomeNotificationViewModel = viewModel(
        factory = HomeNotificationViewModel.factory(notificationHistoryRepository)
    )
    val dashboardViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext,
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeProfileStore,
            diopterHoursCalculator = diopterHoursCalculator,
            nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
            howfarStorageRepository = howfarStorageRepository
        )
    )
    val model by importViewModel.uiModel.collectAsState()
    val lastNotification by notificationViewModel.lastNotification.collectAsState()
    val dashboardState by dashboardViewModel.uiState.collectAsState()
    val howfarState by dashboardViewModel.howfarUiState.collectAsState()
    LaunchedEffect(Unit) {
        importViewModel.snackbarEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Import data or jump back into your latest dashboard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val state = dashboardState) {
                HomeUiState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                HomeUiState.NoData -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "No data recorded today.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is HomeUiState.Data -> {
                    HomeDashboardCard(state = state)
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
                    val accentColors = homeScreenAccentColors()
                    Text(
                        text = "Import data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColors.accent
                    )
                    Text(
                        "Open the dedicated import screen to bring in CSV or HowFar device data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val howfarStatusLabel = when (howfarState) {
                        is HomeHowfarUiState.Ready -> "Ready"
                        is HomeHowfarUiState.Error -> "Unavailable"
                        HomeHowfarUiState.Disconnected -> "Disconnected"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HowFar status: $howfarStatusLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenImport,
                            enabled = !model.isImporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColors.accent,
                                contentColor = accentColors.onAccent
                            )
                        ) {
                            Text("Open Import screen")
                        }
                        IconButton(
                            onClick = { dashboardViewModel.refreshHowfarAvailability() },
                            enabled = !model.isImporting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh HowFar status"
                            )
                        }
                    }
                }
            }

            model.lastResult?.let { last ->
                LastImportPreviewCard(last = last, onOpenImport = onOpenImport)
            }

            LatestNotificationCard(
                lastNotification = lastNotification,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun HomeDashboardCard(state: HomeUiState.Data) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text(
                state.activeProfileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HomeMetricRow(label = "Today's D-h", value = formatDashboardDouble(state.diopterHours))
            HomeMetricRow(label = "Today's NRS", value = formatDashboardDouble(state.nrs))
            HomeMetricRow(label = "Sessions today", value = state.sessionCount.toString())
        }
    }
}

@Composable
private fun HomeMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LastImportPreviewCard(
    last: ImportLastResult,
    onOpenImport: () -> Unit
) {
    val title = when (last.outcome) {
        ImportOutcome.Success -> "Last import: success"
        ImportOutcome.NoNewData -> "Last import: no new data"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Rows read: ${last.summary.totalRows}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Inserted: ${last.summary.insertedRows}  Rejected: ${last.summary.rejectedRows}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenImport) {
                Text("Review full details")
            }
        }
    }
}

@Composable
private fun LatestNotificationCard(
    lastNotification: LastNotification?,
    onOpenSettings: () -> Unit
) {
    val accentColors = homeScreenAccentColors()
    val formattedTime = remember(lastNotification?.sentAtEpochMillis) {
        lastNotification?.sentAtEpochMillis?.let { formatTimestamp(it) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Latest notification",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColors.accent
            )
            if (lastNotification == null) {
                Text(
                    "No notifications yet. Enable reminders in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    lastNotification.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    lastNotification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formattedTime.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(contentColor = accentColors.accent)
            ) {
                Text("Open notification settings")
            }
        }
    }
}

@Composable
private fun homeScreenAccentColors(): HomeScreenAccentColors {
    val isDarkTheme = isSystemInDarkTheme()
    return if (isDarkTheme) {
        HomeScreenAccentColors(accent = LavenderVeil, onAccent = Graphite)
    } else {
        HomeScreenAccentColors(accent = VintageGrape, onAccent = White)
    }
}

private data class HomeScreenAccentColors(
    val accent: Color,
    val onAccent: Color
)

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    return formatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
}

private fun formatDashboardDouble(value: Double): String = String.format(Locale.US, "%.2f", value)

