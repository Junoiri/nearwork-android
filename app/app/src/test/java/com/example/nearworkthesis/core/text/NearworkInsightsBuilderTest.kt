package com.example.nearworkthesis.core.text

import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkRiskReason
import com.example.nearworkthesis.domain.analysis.NearworkSession
import com.example.nearworkthesis.domain.analysis.NearworkSessionRisk
import com.example.nearworkthesis.domain.model.DailySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearworkInsightsBuilderTest {

    @Test
    fun build_usesThresholdsAndMetrics() {
        val summary = DailySummary(
            day = "2025-12-16",
            sampleCount = 100,
            avgDistanceCm = 45.0,
            minDistanceCm = 20.0,
            maxDistanceCm = 80.0,
            avgLux = 300.0,
            minLux = 10.0,
            maxLux = 800.0,
            diopterHoursTotal = 9.5,
            lowLightMinutes = 12,
            firstTimestampIso = null,
            lastTimestampIso = null
        )
        val insights = DailySessionInsights(
            sessions = emptyList(),
            longestSession = null,
            flaggedSessions = emptyList()
        )
        val thresholds = DailyInsightsThresholds(
            lowLightThresholdLux = 300,
            nearworkDistanceThresholdCm = 60
        )

        val bullets = NearworkInsightsBuilder.build(summary, insights, thresholds)

        assertEquals(3, bullets.size)
        assertTrue(bullets[0].contains("9.5"))
        assertTrue(bullets[1].contains("300"))
        assertTrue(bullets[2].contains("60"))
    }

    @Test
    fun build_omitsSessionRiskBullet() {
        val summary = DailySummary(
            day = "2025-12-16",
            sampleCount = 50,
            avgDistanceCm = 55.0,
            minDistanceCm = 30.0,
            maxDistanceCm = 90.0,
            avgLux = 250.0,
            minLux = 12.0,
            maxLux = 700.0,
            diopterHoursTotal = 6.1,
            lowLightMinutes = 0,
            firstTimestampIso = null,
            lastTimestampIso = null
        )
        val session = NearworkSession(
            startTimestampMillis = 1L,
            endTimestampMillis = 2L,
            durationSeconds = 60L,
            avgDistanceCm = 30.0,
            minDistanceCm = 20.0,
            diopterHoursInSession = 1.0,
            lowLightSecondsInSession = 30L
        )
        val risk = NearworkSessionRisk(session = session, reasons = setOf(NearworkRiskReason.ExtremeClose))
        val insights = DailySessionInsights(
            sessions = listOf(session),
            longestSession = session,
            flaggedSessions = listOf(risk)
        )
        val thresholds = DailyInsightsThresholds(
            lowLightThresholdLux = 300,
            nearworkDistanceThresholdCm = 60
        )

        val bullets = NearworkInsightsBuilder.build(summary, insights, thresholds)

        assertEquals(3, bullets.size)
        assertTrue(bullets.none { it.contains("Risk flags") })
    }
}
