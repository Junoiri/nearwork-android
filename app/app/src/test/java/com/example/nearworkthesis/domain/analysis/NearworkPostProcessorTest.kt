package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearworkPostProcessorTest {

    private val processor = NearworkPostProcessor()
    private val settings = NearworkSettings(
        lowLightThresholdLux = 300,
        nearworkDistanceThresholdCm = 60,
        breakGapSeconds = 60,
        minSessionDurationSeconds = 1,
        closeDistanceThresholdCm = 30,
        extremeCloseThresholdCm = 20
    )

    @Test
    fun compute_withoutPreprocessing_fallsBackToRawSamples() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 20.0, lux = 100.0),
            NearworkSample(timestampMillis = 1_000L, distanceCm = 50.0, lux = 500.0),
            NearworkSample(timestampMillis = 2_000L, distanceCm = 80.0, lux = 1_200.0)
        )

        val result = processor.compute(
            samples = samples,
            settings = settings,
            preprocessing = null
        )

        assertEquals(1L, result.totalNearTimeUnder40CmSeconds)
        assertEquals(1L, result.timeBelow30CmSeconds)
        assertEquals(1L, result.timeIntermediate40To70CmSeconds)
        assertEquals(1L, result.timeFarAbove70CmSeconds)
        assertEquals(1.0 / 3.0, result.pctTimeNear40Cm!!, 0.000001)
        assertEquals(1L, result.timeAbove2_5DiopterSeconds)
        assertEquals(1L, result.timeAbove3_0DiopterSeconds)
        assertEquals(1.0 / 60.0, result.timeOutdoor1000LuxMinutes!!, 0.000001)
        assertEquals(1.0 / 3.0, result.pctOutdoorTime!!, 0.000001)
        assertEquals(100.0, result.meanLuxDuringNearwork!!, 0.0)
        assertEquals(1.0, result.distanceCoveragePct!!, 0.0)
        assertEquals(1.0, result.luxCoveragePct!!, 0.0)
        assertEquals(1.0, result.diopterCoveragePct!!, 0.0)
        assertEquals(1, result.transitionMatrix["NEAR->INTERMEDIATE"])
        assertEquals(1, result.transitionMatrix["INTERMEDIATE->FAR"])
        assertEquals(1L, result.dwellTimesByStateSeconds["NEAR"])
        assertEquals(1L, result.dwellTimesByStateSeconds["INTERMEDIATE"])
        assertEquals(1L, result.dwellTimesByStateSeconds["FAR"])
        assertEquals(1, result.flaggedSessionCount)
        assertEquals(setOf(NearworkRiskReason.CloseDistance, NearworkRiskReason.LowLight), result.flaggedSessions.single().reasons)
        assertTrue(result.compositeRiskScore!! > 0.0)
    }

    @Test
    fun compute_prefersSmoothedDistanceSeriesWhenPreprocessingProvidesIt() {
        val rawSamples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 20.0, lux = 10.0),
            NearworkSample(timestampMillis = 1_000L, distanceCm = 20.0, lux = 10.0),
            NearworkSample(timestampMillis = 2_000L, distanceCm = 20.0, lux = 10.0)
        )
        val preprocessing = PreprocessingResult(
            tSeconds = listOf(0, 1, 2),
            sInterpDistanceCm = listOf(10.0, 10.0, 10.0),
            sFilterDistanceCm = listOf(80.0, 85.0, 90.0),
            sInterpIlluminationLux = listOf(1_000.0, 500.0, 100.0),
            sFilterIlluminationLux = listOf(1_000.0, 500.0, 100.0),
            samples = rawSamples,
            interpolatedSamples = rawSamples,
            stats = PreprocessingStats(
                rawCount = 3,
                dedupedCount = 0,
                rejectedCount = 0,
                outputCount = 3,
                smoothingWindowSize = 1
            )
        )

        val result = processor.compute(
            samples = rawSamples,
            settings = settings,
            preprocessing = preprocessing
        )

        assertEquals(0L, result.totalNearTimeUnder40CmSeconds)
        assertEquals(3L, result.timeFarAbove70CmSeconds)
        assertNull(result.meanLuxDuringNearwork)
        assertEquals(1.0 / 60.0, result.timeOutdoor1000LuxMinutes!!, 0.000001)
        assertEquals(1.0 / 3.0, result.pctOutdoorTime!!, 0.000001)
        assertEquals(0L, result.dwellTimesByStateSeconds["NEAR"])
        assertEquals(0L, result.dwellTimesByStateSeconds["INTERMEDIATE"])
        assertEquals(3L, result.dwellTimesByStateSeconds["FAR"])
    }

    @Test
    fun compute_usesInterpolatedDistance_andRawLuxFallback_whenPreprocessingSeriesAreIncomplete() {
        val rawSamples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 20.0, lux = 50.0),
            NearworkSample(timestampMillis = 1_000L, distanceCm = 80.0, lux = 60.0)
        )
        val preprocessing = PreprocessingResult(
            tSeconds = listOf(0, 1),
            sInterpDistanceCm = listOf(35.0, 75.0),
            sFilterDistanceCm = listOf(Double.NaN, Double.NaN),
            sInterpIlluminationLux = listOf(999.0),
            sFilterIlluminationLux = listOf(999.0),
            samples = rawSamples,
            interpolatedSamples = rawSamples,
            stats = PreprocessingStats(
                rawCount = 2,
                dedupedCount = 0,
                rejectedCount = 0,
                outputCount = 2,
                smoothingWindowSize = 1
            )
        )

        val result = processor.compute(
            samples = rawSamples,
            settings = settings,
            preprocessing = preprocessing
        )

        assertEquals(1L, result.totalNearTimeUnder40CmSeconds)
        assertEquals(0L, result.timeBelow30CmSeconds)
        assertEquals(0L, result.timeIntermediate40To70CmSeconds)
        assertEquals(1L, result.timeFarAbove70CmSeconds)
        assertEquals(50.0, result.meanLuxDuringNearwork!!, 0.0)
        assertEquals(1.0, result.luxCoveragePct!!, 0.0)
        assertEquals(0, result.flaggedSessionCount)
        assertEquals(0.0, result.compositeRiskScore!!, 0.0)
    }
}
