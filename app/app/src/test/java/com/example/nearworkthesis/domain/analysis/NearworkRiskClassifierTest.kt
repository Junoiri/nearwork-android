package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearworkRiskClassifierTest {

    @Test
    fun classifyFlaggedSessions_addsCloseAndExtremeFlagsFromMinDistance() {
        val session = NearworkSession(
            startTimestampMillis = 0L,
            endTimestampMillis = 60_000L,
            durationSeconds = 60L,
            avgDistanceCm = 24.0,
            minDistanceCm = 18.0,
            diopterHoursInSession = 1.0,
            lowLightSecondsInSession = 0L
        )

        val flagged = NearworkRiskClassifier.classifyFlaggedSessions(
            sessions = listOf(session),
            closeDistanceThresholdCm = 30.0,
            extremeCloseThresholdCm = 20.0
        )

        assertEquals(1, flagged.size)
        assertTrue(NearworkRiskReason.CloseDistance in flagged.first().reasons)
        assertTrue(NearworkRiskReason.ExtremeClose in flagged.first().reasons)
    }

    @Test
    fun classifyFlaggedSessions_includesLowLightAndFiltersUnflaggedSessions() {
        val flaggedSession = NearworkSession(
            startTimestampMillis = 0L,
            endTimestampMillis = 60_000L,
            durationSeconds = 60L,
            avgDistanceCm = 35.0,
            minDistanceCm = 32.0,
            diopterHoursInSession = 1.0,
            lowLightSecondsInSession = 30L
        )
        val unflaggedSession = flaggedSession.copy(
            startTimestampMillis = 120_000L,
            endTimestampMillis = 180_000L,
            minDistanceCm = 35.0,
            lowLightSecondsInSession = 0L
        )

        val flagged = NearworkRiskClassifier.classifyFlaggedSessions(
            sessions = listOf(unflaggedSession, flaggedSession),
            closeDistanceThresholdCm = 30.0,
            extremeCloseThresholdCm = 20.0
        )

        assertEquals(1, flagged.size)
        assertTrue(NearworkRiskReason.LowLight in flagged.first().reasons)
        assertFalse(flagged.any { it.session.startTimestampMillis == unflaggedSession.startTimestampMillis })
    }
}
