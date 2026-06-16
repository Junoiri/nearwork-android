package com.example.nearworkthesis.feature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.core.util.AppConstants

@Composable
fun MethodsAssumptionsScreen(modifier: Modifier = Modifier) {
    val zone1 = AppConstants.ZONE_EXTREME_CLOSE_CM.toInt()
    val zone2 = AppConstants.ZONE_CLOSE_CM.toInt()
    val zone3 = AppConstants.ZONE_MODERATE_CM.toInt()
    val lowLightLux = AppConstants.LUX_TIER_1.toInt()
    val brightOutdoorLux = AppConstants.LUX_TIER_5.toInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Methods & Assumptions", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "How the app preprocesses data and computes nearwork metrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            MethodsSectionCard(
                title = "Preprocessing",
                body = "Raw samples are deduped by timestamp (keep last), filtered to valid distance/lux ranges, and smoothed with a 60-sample causal moving average before analysis."
            )
        }

        item {
            MethodsSectionCard(
                // I use the thesis spelling here so the explainer matches the rest of the app copy.
                title = "Dioptre-hours",
                body = "Dioptre-hours are computed from processed distances as the time-weighted sum of (1 / meters). Totals are reported per day and per session."
            )
        }

        item {
            MethodsSectionCard(
                // I keep the full NRS description here because this is the one info screen where the scoring model belongs.
                title = "Nearwork Risk Score (NRS)",
                body = "NRS_sample = vergence demand (D) x distance zone weight x light weight. Distance zone weights are x5.0 for < $zone1 cm, x2.5 for $zone1-$zone2 cm, x1.5 for $zone2-$zone3 cm, and x1.0 for >= $zone3 cm. Light weight decreases as lux rises, using six internal tiers from x1.50 at <= $lowLightLux lux to x0.20 above $brightOutdoorLux lux. The per-sample NRS average is then multiplied by 24, which makes the score independent of session duration. NRS is a research outcome variable; it does not constitute a clinical diagnosis."
            )
        }

        item {
            MethodsSectionCard(
                title = "Low-light exposure",
                body = "Minutes below your low-light threshold (Settings) count as low-light exposure."
            )
        }

        item {
            MethodsSectionCard(
                title = "Sessions & risk rules",
                body = "Sessions are contiguous nearwork segments within the nearwork distance threshold, split by breaks >= break-gap seconds, and require a minimum session duration. Session flags include Close distance for samples below $zone2 cm, Extreme close for samples below $zone1 cm, and Low light for lux at or below your configured low-light threshold."
            )
        }

        item {
            MethodsSectionCard(
                title = "Time handling",
                body = "Measurements are stored as UTC epoch millis. localDay is derived using the profile timezone, so daily summaries use local time while exports also include UTC timestamps."
            )
        }

        item {
            MethodsSectionCard(
                title = "Results Pack",
                body = "Results Pack exports include CSVs plus manifest.json with preprocessing parameters, thresholds, and timezone details for reproducibility."
            )
        }

        item {
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}

@Composable
private fun MethodsSectionCard(
    title: String,
    body: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
