package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampDeduplicatorTest {

    @Test
    fun dedupeEpochRows_keepsLastValueForDuplicateTimestamp_andSortsAscending() {
        val rows = TimestampDeduplicator.dedupeEpochRows(
            epochMillis = listOf(3_000L, 1_000L, 1_000L, 2_000L),
            distance = listOf(30.0, 10.0, 11.0, 20.0),
            lux = listOf(300.0, 100.0, 110.0, 200.0),
            robustness = RobustnessConfig()
        )

        assertEquals(listOf(1_000L, 2_000L, 3_000L), rows.map { it.epochMillis })
        assertEquals(listOf(11.0, 20.0, 30.0), rows.map { it.distanceCm })
        assertEquals(listOf(110.0, 200.0, 300.0), rows.map { it.lux })
    }

    @Test
    fun dedupeEpochRows_whenDisabled_preservesDuplicatesAfterSorting() {
        val rows = TimestampDeduplicator.dedupeEpochRows(
            epochMillis = listOf(2_000L, 1_000L, 1_000L),
            distance = listOf(20.0, 10.0, 11.0),
            lux = listOf(200.0, 100.0, 110.0),
            robustness = RobustnessConfig(
                deduplicateTimestamps = false
            )
        )

        assertEquals(listOf(1_000L, 1_000L, 2_000L), rows.map { it.epochMillis })
        assertEquals(listOf(10.0, 11.0, 20.0), rows.map { it.distanceCm })
    }

    @Test
    fun dedupeTimeRows_keepsLastValueForDuplicateSecond_andSortsAscending() {
        val rows = TimestampDeduplicator.dedupeTimeRows(
            timeStamp = listOf(2.0, 0.0, 1.0, 1.0),
            distance = listOf(20.0, 0.0, 10.0, 11.0),
            lux = listOf(200.0, 0.0, 100.0, 110.0),
            robustness = RobustnessConfig()
        )

        assertEquals(listOf(0.0, 1.0, 2.0), rows.map { it.timeSec })
        assertEquals(listOf(0.0, 11.0, 20.0), rows.map { it.distanceCm })
        assertEquals(listOf(0.0, 110.0, 200.0), rows.map { it.lux })
    }

    @Test
    fun dedupeTimeRows_whenDisabled_preservesDuplicatesAfterSorting() {
        val rows = TimestampDeduplicator.dedupeTimeRows(
            timeStamp = listOf(1.0, 0.0, 1.0),
            distance = listOf(10.0, 0.0, 11.0),
            lux = listOf(100.0, 0.0, 110.0),
            robustness = RobustnessConfig(
                deduplicateTimestamps = false
            )
        )

        assertEquals(listOf(0.0, 1.0, 1.0), rows.map { it.timeSec })
        assertEquals(listOf(0.0, 10.0, 11.0), rows.map { it.distanceCm })
    }
}
