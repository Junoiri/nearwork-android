package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class AlsSingleSampleSpikeReplacementTest {

    @Test
    fun apply_replacesMidSequenceSpikeWithNeighborAverage() {
        val result = processLux(listOf(100.0, 120.0, 1000.0, 140.0, 150.0))

        assertEquals(listOf(100.0, 120.0, 130.0, 140.0, 150.0), result.sInterpIlluminationLux)
        assertEquals(130.0, result.samples[2].lux, 0.0)
    }

    @Test
    fun apply_keepsLargeValidTransition() {
        val result = processLux(listOf(50.0, 8000.0, 8100.0))

        assertEquals(listOf(50.0, 8000.0, 8100.0), result.sInterpIlluminationLux)
    }

    @Test
    fun apply_leavesEdgeSamplesAlone() {
        val result = processLux(listOf(1000.0, 100.0, 120.0, 1000.0))

        assertEquals(listOf(1000.0, 100.0, 120.0, 1000.0), result.sInterpIlluminationLux)
    }

    private fun processLux(luxValues: List<Double>): PreprocessingResult {
        val pipeline = PreprocessingPipeline(
            PreprocessingPipeline.Config(
                smoothingWindowSize = 1,
                robustness = RobustnessConfig(
                    alsSingleSampleSpikeThresholdLux = 300.0
                )
            )
        )
        val samples = luxValues.mapIndexed { index, lux ->
            NearworkSample(
                timestampMillis = index * 1000L,
                distanceCm = 40.0,
                lux = lux
            )
        }

        return pipeline.process(samples)
    }
}
