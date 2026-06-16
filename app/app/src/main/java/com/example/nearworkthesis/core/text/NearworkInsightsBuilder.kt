package com.example.nearworkthesis.core.text

import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.model.DailySummary
import java.util.Locale

data class DailyInsightsThresholds(
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int
)

object NearworkInsightsBuilder {
    fun build(
        summary: DailySummary,
        sessionInsights: DailySessionInsights,
        thresholds: DailyInsightsThresholds
    ): List<String> {
        val bullets = ArrayList<String>(3)
        bullets.add("Total dioptre-hours: ${format1(summary.diopterHoursTotal)} D-h.")

        val lowLight = if (summary.lowLightMinutes > 0) {
            "Low-light time: ${summary.lowLightMinutes} min below ${thresholds.lowLightThresholdLux} lux."
        } else {
            "No low-light time below ${thresholds.lowLightThresholdLux} lux."
        }
        bullets.add(lowLight)

        summary.avgDistanceCm?.let { avg ->
            val relation = if (avg <= thresholds.nearworkDistanceThresholdCm) "within" else "above"
            bullets.add(
                "Average distance: ${format1(avg)} cm, $relation your nearwork threshold (${thresholds.nearworkDistanceThresholdCm} cm)."
            )
        }

        return bullets.take(3)
    }

    private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
}
