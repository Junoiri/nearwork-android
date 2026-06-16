package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearworkRiskScoreCalculatorTest {

    private val calculator = NearworkRiskScoreCalculator()
    private val oneHour = 1.0

    @Test
    fun emptyInput_returnsZero() {
        val result = calculator.calculate(emptyList(), durationHours = oneHour)

        assertEquals(0.0, result.nrs, 1e-9)
        assertEquals(0, result.sampleCount)
        assertEquals(null, result.meanLuxDuringNearwork)
    }

    @Test
    fun singleSample25cmAnd100lux_matchesExpectedValue() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 25.0, lux = 100.0)
        )

        val result = calculator.calculate(samples, durationHours = oneHour)
        assertEquals(4.8986165538, result.nrs, 1e-8)
        assertEquals(1, result.sampleCount)
        assertEquals(100.0, result.meanLuxDuringNearwork!!, 1e-9)
    }

    @Test
    fun distanceUnder20cm_andDimLux_matchesExpectedValue() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 15.0, lux = 30.0)
        )

        val result = calculator.calculate(samples, durationHours = oneHour)
        val expected = (100.0 / 15.0) * 5.0 * ((1.0 / kotlin.math.log10(40.0)) * 1.5) * oneHour
        assertEquals(expected, result.nrs, 1e-9)
    }

    @Test
    fun distanceAt25cm_appliesCloseWeight() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 25.0, lux = 300.0)
        )

        val result = calculator.calculate(samples, durationHours = oneHour)
        val expected = (100.0 / 25.0) * 2.5 * (1.0 / kotlin.math.log10(310.0)) * oneHour
        assertEquals(expected, result.nrs, 1e-9)
    }

    @Test
    fun luxAtOrBelow55_appliesDimTierMultiplier() {
        val dim = calculator.calculate(
            listOf(NearworkSample(timestampMillis = 0L, distanceCm = 30.0, lux = 55.0)),
            durationHours = oneHour
        )
        val indoor = calculator.calculate(
            listOf(NearworkSample(timestampMillis = 0L, distanceCm = 30.0, lux = 56.0)),
            durationHours = oneHour
        )

        val expectedDim = (100.0 / 30.0) * 1.5 * ((1.0 / kotlin.math.log10(65.0)) * 1.5) * oneHour
        assertEquals(expectedDim, dim.nrs, 1e-9)
        assertTrue(dim.nrs > indoor.nrs)
    }

    @Test
    fun distanceAt40cm_usesNewThirtyToFiftyTier() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 40.0, lux = 200.0)
        )

        val result = calculator.calculate(samples, durationHours = 2.0)
        val expected = (100.0 / 40.0) * 1.5 * (1.0 / kotlin.math.log10(210.0)) * 2.0
        assertEquals(expected, result.nrs, 1e-9)
    }

    @Test
    fun distanceAt60cm_appliesDefaultWeight() {
        val samples = listOf(
            NearworkSample(timestampMillis = 0L, distanceCm = 60.0, lux = 100.0)
        )

        val result = calculator.calculate(samples, durationHours = oneHour)
        val expected = (100.0 / 60.0) * 1.0 * (1.0 / kotlin.math.log10(110.0)) * oneHour
        assertEquals(expected, result.nrs, 1e-9)
    }

    @Test
    fun luxAbove3000_reducesNrsComparedTo300lux() {
        val baseline = calculator.calculate(
            listOf(NearworkSample(timestampMillis = 0L, distanceCm = 30.0, lux = 300.0)),
            durationHours = oneHour
        )
        val bright = calculator.calculate(
            listOf(NearworkSample(timestampMillis = 0L, distanceCm = 30.0, lux = 3500.0)),
            durationHours = oneHour
        )

        assertTrue(bright.nrs < baseline.nrs)
    }

    @Test
    fun luxAbove50000_isExcludedFromMeanInsteadOfPullingNrsDown() {
        val validA = NearworkSample(timestampMillis = 0L, distanceCm = 30.0, lux = 55.0)
        val invalidHighLux = NearworkSample(timestampMillis = 1_000L, distanceCm = 30.0, lux = 60_000.0)
        val validB = NearworkSample(timestampMillis = 2_000L, distanceCm = 30.0, lux = 300.0)

        val result = calculator.calculate(listOf(validA, invalidHighLux, validB), durationHours = oneHour)
        val expected = calculator.calculate(listOf(validA, validB), durationHours = oneHour)

        assertEquals(expected.nrs, result.nrs, 1e-9)
        assertEquals(2, result.sampleCount)
        assertEquals(expected.meanLuxDuringNearwork, result.meanLuxDuringNearwork)
    }

    @Test
    fun zeroDuration_returnsZero() {
        val result = calculator.calculate(
            samples = listOf(NearworkSample(timestampMillis = 0L, distanceCm = 25.0, lux = 100.0)),
            durationHours = 0.0
        )

        assertEquals(0.0, result.nrs, 1e-9)
        assertEquals(0, result.sampleCount)
        assertEquals(null, result.meanLuxDuringNearwork)
    }
}
