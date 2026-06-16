package com.example.nearworkthesis.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.app.LocalSettingsStore
import java.time.ZoneId
import java.util.Locale

@Composable
fun DebugOverlayBar(dateLabel: String?) {
    val settingsStore = LocalSettingsStore.current
    val lowLux by settingsStore.observeLowLightThresholdLux().collectAsState(initial = 300)
    val nearworkCm by settingsStore.observeNearworkDistanceThresholdCm().collectAsState(initial = 60)
    val closeDistanceCm by settingsStore.observeCloseDistanceThresholdCm().collectAsState(initial = 30)
    val extremeCloseCm by settingsStore.observeExtremeCloseThresholdCm().collectAsState(initial = 20)

    val profileRepository = LocalProfileRepository.current
    val activeProfileStore = LocalActiveProfileStore.current
    val profiles by profileRepository.observeProfiles().collectAsState(initial = emptyList())
    val activeId by activeProfileStore.observeActiveProfileId().collectAsState(initial = null)
    val activeProfile = remember(profiles, activeId) {
        profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }
    val activeName = activeProfile?.name ?: "Profile"
    val activeTimezoneId = activeProfile?.timezoneId ?: ZoneId.systemDefault().id

    val line = remember(activeName, dateLabel, lowLux, nearworkCm, closeDistanceCm, extremeCloseCm, activeTimezoneId) {
        val date = dateLabel ?: "?"
        "Profile: $activeName | Date: $date | TZ: $activeTimezoneId | Day grouping: localDay in $activeTimezoneId | LL ${lowLux}lx | NW ${nearworkCm}cm | Close ${closeDistanceCm}cm | Extreme ${extremeCloseCm}cm"
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = line,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

