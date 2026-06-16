package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAveragedNrsCalculatorTest {

    private val calculator = SessionAveragedNrsCalculator()

    @Test
    fun calculate_sumsPerSessionNrsForDailyTotal() {
        val thresholds = defaultThresholds()
        val samples = listOf(
            // I keep each pair just over one second wide so both sessions satisfy the minimum naturally.
            NearworkSample(timestampMillis = 0L, distanceCm = 15.0, lux = 30.0),
            NearworkSample(timestampMillis = 1_000L, distanceCm = 15.0, lux = 30.0),
            // I push this pair far enough out to force a second distinct session under the 60-second break rule.
            NearworkSample(timestampMillis = 120_000L, distanceCm = 25.0, lux = 100.0),
            NearworkSample(timestampMillis = 121_000L, distanceCm = 25.0, lux = 100.0)
        )

        val result = calculator.calculate(samples, thresholds)
        val expectedSessionOne = NearworkRiskScoreCalculator().calculate(samples.take(2), durationHours = 1_000.0 / 3_600_000.0).nrs
        val expectedSessionTwo = NearworkRiskScoreCalculator().calculate(samples.takeLast(2), durationHours = 1_000.0 / 3_600_000.0).nrs

        assertEquals(expectedSessionOne + expectedSessionTwo, result.nrs, 1e-9)
        assertEquals(4, result.sampleCount)
        assertEquals(65.0, result.meanLuxDuringNearwork!!, 1e-9)
    }

    @Test
    fun calculate_discardsSingletonSessionsBelowMinimumDuration() {
        val thresholds = defaultThresholds()
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 15.0, lux = 30.0),
            NearworkSample(timestampMillis = 120_000L, distanceCm = 25.0, lux = 100.0)
        )

        val result = calculator.calculate(samples, thresholds)

        assertEquals(0.0, result.nrs, 1e-9)
        assertEquals(0, result.sampleCount)
        assertNull(result.meanLuxDuringNearwork)
    }

    @Test
    fun calculateWithSessions_returnsSessionBreakdownForWeeklyAggregation() {
        val thresholds = defaultThresholds()
        val samples = listOf(
            // I keep both of these inside one session so the breakdown proves the grouping shape too.
            NearworkSample(timestampMillis = 0L, distanceCm = 25.0, lux = 100.0),
            NearworkSample(timestampMillis = 5_000L, distanceCm = 25.0, lux = 100.0),
            // I separate this pair so the weekly code can flatten two distinct session values later.
            NearworkSample(timestampMillis = 120_000L, distanceCm = 15.0, lux = 30.0),
            NearworkSample(timestampMillis = 125_000L, distanceCm = 15.0, lux = 30.0)
        )

        val result = calculator.calculateWithSessions(samples, thresholds)

        assertEquals(2, result.sessionResults.size)
        assertEquals(result.sessionResults.sumOf { it.nrs }, result.aggregatedResult.nrs, 1e-9)
    }

    @Test
    fun calculate_returnsZeroWhenNoValidSessionsExist() {
        val thresholds = defaultThresholds().copy(
            nearworkDistanceThresholdCm = 20,
            minSessionDurationSeconds = 10
        )
        val samples = listOf(
            // I keep these far enough away that the session splitter should reject them as non-nearwork.
            NearworkSample(timestampMillis = 0L, distanceCm = 50.0, lux = 100.0),
            NearworkSample(timestampMillis = 5_000L, distanceCm = 50.0, lux = 100.0)
        )

        val result = calculator.calculate(samples, thresholds)

        assertEquals(0.0, result.nrs, 1e-9)
        assertEquals(0, result.sampleCount)
        assertNull(result.meanLuxDuringNearwork)
    }

    private fun defaultThresholds(): AnalysisThresholds {
        // I keep the defaults explicit here so the test reads like the real session policy.
        return AnalysisThresholds(
            lowLightThresholdLux = 55,
            nearworkDistanceThresholdCm = 60,
            breakGapSeconds = 60,
            minSessionDurationSeconds = 1,
            closeDistanceThresholdCm = 30,
            extremeCloseThresholdCm = 20
        )
    }
}
