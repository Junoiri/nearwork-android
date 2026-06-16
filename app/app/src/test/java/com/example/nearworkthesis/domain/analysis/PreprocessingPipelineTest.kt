package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreprocessingPipelineTest {

    @Test
    fun process_withEmptyInput_returnsEmptyResultAndZeroStats() {
        val result = PreprocessingPipeline().process(emptyList())

        assertTrue(result.tSeconds.isEmpty())
        assertTrue(result.samples.isEmpty())
        assertEquals(0, result.stats.rawCount)
        assertEquals(0, result.stats.outputCount)
        assertEquals(0, result.stats.rejectedCount)
    }

    @Test
    fun process_rejectsInvalidSamples_andReportsStats() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1
            )
        )

        val result = pipeline.process(
            listOf(
                NearworkSample(timestampMillis = Long.MIN_VALUE, distanceCm = 40.0, lux = 100.0),
                NearworkSample(timestampMillis = 1_000L, distanceCm = 0.0, lux = 100.0),
                NearworkSample(timestampMillis = 2_000L, distanceCm = 300.0, lux = 100.0),
                NearworkSample(timestampMillis = 3_000L, distanceCm = 40.0, lux = Double.NaN),
                NearworkSample(timestampMillis = 4_000L, distanceCm = 50.0, lux = 250.0),
            )
        )

        assertEquals(5, result.stats.rawCount)
        assertEquals(4, result.stats.rejectedCount)
        assertEquals(0, result.stats.dedupedCount)
        assertEquals(1, result.stats.outputCount)
        assertEquals(listOf(0), result.tSeconds)
        assertEquals(50.0, result.samples.single().distanceCm, 0.0)
        assertEquals(250.0, result.samples.single().lux, 0.0)
    }

    @Test
    fun process_whenAllSamplesAreRejected_returnsEmptySeriesWithCounts() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1
            )
        )

        val result = pipeline.process(
            listOf(
                NearworkSample(timestampMillis = 1_000L, distanceCm = 0.0, lux = 100.0),
                NearworkSample(timestampMillis = 2_000L, distanceCm = 250.0, lux = 100.0),
                NearworkSample(timestampMillis = 3_000L, distanceCm = 40.0, lux = Double.NaN),
            )
        )

        assertTrue(result.tSeconds.isEmpty())
        assertTrue(result.interpolatedSamples.isEmpty())
        assertEquals(3, result.stats.rawCount)
        assertEquals(3, result.stats.rejectedCount)
        assertEquals(0, result.stats.outputCount)
    }

    @Test
    fun process_sortsSamples_andKeepsLastDuplicateTimestamp() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1
            )
        )

        val result = pipeline.process(
            listOf(
                NearworkSample(timestampMillis = 3_000L, distanceCm = 60.0, lux = 300.0),
                NearworkSample(timestampMillis = 2_000L, distanceCm = 20.0, lux = 100.0),
                NearworkSample(timestampMillis = 1_000L, distanceCm = 10.0, lux = 50.0),
                NearworkSample(timestampMillis = 2_000L, distanceCm = 25.0, lux = 120.0),
            )
        )

        assertEquals(1, result.stats.dedupedCount)
        assertEquals(listOf(0, 1, 2), result.tSeconds)
        assertEquals(1_000L, result.interpolatedSamples[0].timestampMillis)
        assertEquals(10.0, result.interpolatedSamples[0].distanceCm, 0.0)
        assertEquals(25.0, result.interpolatedSamples[1].distanceCm, 0.0)
        assertEquals(120.0, result.interpolatedSamples[1].lux, 0.0)
        assertEquals(60.0, result.interpolatedSamples[2].distanceCm, 0.0)
    }

    @Test
    fun process_blocksInterpolationAcrossLargeGaps() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1,
                interpolationGapThresholdSeconds = 10
            )
        )

        val result = pipeline.process(
            listOf(
                NearworkSample(timestampMillis = 0L, distanceCm = 40.0, lux = 100.0),
                NearworkSample(timestampMillis = 20_000L, distanceCm = 60.0, lux = 200.0),
            )
        )

        assertEquals(listOf(0, 1, 2, 3, 4), result.tSeconds.take(5))
        assertEquals(40.0, result.sInterpDistanceCm.first(), 0.0)
        assertEquals(60.0, result.sInterpDistanceCm.last(), 0.0)
        assertTrue(result.sInterpDistanceCm[10].isNaN())
        assertTrue(result.sInterpIlluminationLux[10].isNaN())
    }

    @Test
    fun process_withRollingMeanIllumination_usesFilteredLuxInOutputSamples() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 2
            )
        )

        val result = pipeline.process(
            rawSamples = listOf(
                NearworkSample(timestampMillis = 0L, distanceCm = 40.0, lux = 100.0),
                NearworkSample(timestampMillis = 1_000L, distanceCm = 40.0, lux = 300.0),
                NearworkSample(timestampMillis = 2_000L, distanceCm = 40.0, lux = 500.0),
            ),
            illuminationSmoothing = PreprocessingPipeline.IlluminationSmoothing.ROLLING_MEAN_60
        )

        assertEquals(listOf(100.0, 200.0, 400.0), result.samples.map { it.lux })
        assertEquals(listOf(100.0, 300.0, 500.0), result.interpolatedSamples.map { it.lux })
        assertEquals(listOf(100.0, 200.0, 400.0), result.sFilterIlluminationLux)
    }

    @Test
    fun processTimeOfDay_rollsAcrossMidnightWithoutGoingBackwards() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1
            )
        )

        val result = pipeline.processTimeOfDay(
            Time_stamp = listOf("23:59:58", "23:59:59", "00:00:01"),
            Distance = listOf(30.0, 40.0, 60.0),
            Lux = listOf(100.0, 200.0, 400.0)
        )

        assertEquals(listOf(0, 1, 2, 3), result.tSeconds)
        assertEquals(30.0, result.sInterpDistanceCm[0], 0.0)
        assertEquals(40.0, result.sInterpDistanceCm[1], 0.0)
        assertEquals(50.0, result.sInterpDistanceCm[2], 0.0)
        assertEquals(60.0, result.sInterpDistanceCm[3], 0.0)
        assertEquals(0, result.duplicateCount)
    }

    @Test
    fun processTimeOfDay_withoutDistanceImputation_keepsOnlyExactWholeSeconds() {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1,
                robustness = RobustnessConfig(
                    distanceImputation = false
                )
            )
        )

        val result = pipeline.processTimeOfDay(
            Time_stamp = listOf("10:00:00.250", "10:00:01.750"),
            Distance = listOf(20.0, 40.0),
            Lux = listOf(100.0, 200.0)
        )

        assertEquals(listOf(0, 1), result.tSeconds)
        assertEquals(20.0, result.sInterpDistanceCm[0], 0.0)
        assertTrue(result.sInterpDistanceCm[1].isNaN())
        assertEquals(100.0, result.sInterpIlluminationLux[0], 0.0)
        assertTrue(result.sInterpIlluminationLux[1].isNaN())
    }

    @Test(expected = IllegalArgumentException::class)
    fun processTimeOfDay_rejectsUnsupportedClockTokens() {
        val pipeline = PreprocessingPipeline()

        pipeline.processTimeOfDay(
            Time_stamp = listOf("25:00:00"),
            Distance = listOf(20.0),
            Lux = listOf(100.0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun processTimeOfDay_rejectsMismatchedDistanceSize() {
        PreprocessingPipeline().processTimeOfDay(
            Time_stamp = listOf("10:00:00"),
            Distance = listOf(20.0, 30.0),
            Lux = listOf(100.0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun processTimeOfDay_rejectsMismatchedLuxSize() {
        PreprocessingPipeline().processTimeOfDay(
            Time_stamp = listOf("10:00:00"),
            Distance = listOf(20.0),
            Lux = listOf(100.0, 200.0)
        )
    }
}
