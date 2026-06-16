package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class DiopterHoursCalculatorTest {

    private val calculator = DiopterHoursCalculator()

    @Test
    fun constant33cm_forOneHour_isAbout3DiopterHours() {
        val start = 0L
        val end = 3_600_000L
        val samples = listOf(
            NearworkSample(timestampMillis = start, distanceCm = 33.0, lux = 500.0),
            NearworkSample(timestampMillis = end, distanceCm = 33.0, lux = 500.0)
        )

        val result = calculator.calculate(samples).totalDiopterHours
        assertEquals(3.0, result, 0.05)
    }

    @Test
    fun constant50cm_forOneHour_is2DiopterHours() {
        val start = 0L
        val end = 3_600_000L
        val samples = listOf(
            NearworkSample(timestampMillis = start, distanceCm = 50.0, lux = 500.0),
            NearworkSample(timestampMillis = end, distanceCm = 50.0, lux = 500.0)
        )

        val result = calculator.calculate(samples).totalDiopterHours
        assertEquals(2.0, result, 1e-9)
    }
}

