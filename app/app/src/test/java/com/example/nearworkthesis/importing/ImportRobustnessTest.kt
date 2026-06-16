package com.example.nearworkthesis.importing

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRobustnessTest {

    @Test
    fun rejectTimestamp_rejectsNonPositiveAndSentinelValues() {
        val robustness = RobustnessConfig()

        assertTrue(ImportMeasurementRejector.rejectTimestamp(0L, robustness))
        assertTrue(ImportMeasurementRejector.rejectTimestamp(-1L, robustness))
        assertTrue(ImportMeasurementRejector.rejectTimestamp(Long.MAX_VALUE, robustness))
    }

    @Test
    fun rejectTimestamp_withoutNonFiniteGuard_acceptsSentinelTimestamp() {
        val robustness = RobustnessConfig(guardNonFiniteValues = false)

        assertFalse(ImportMeasurementRejector.rejectTimestamp(Long.MAX_VALUE, robustness))
    }

    @Test
    fun rejectDistance_rejectsZeroNonFiniteAndOutOfRangeValues() {
        val robustness = RobustnessConfig()

        assertTrue(ImportMeasurementRejector.rejectDistance(0.0, 10.0, 200.0, robustness))
        assertTrue(ImportMeasurementRejector.rejectDistance(Double.NaN, 10.0, 200.0, robustness))
        assertTrue(ImportMeasurementRejector.rejectDistance(250.0, 10.0, 200.0, robustness))
        assertFalse(ImportMeasurementRejector.rejectDistance(35.0, 10.0, 200.0, robustness))
    }

    @Test
    fun rejectDistance_withoutRangeAndZeroGuards_acceptsBoundaryViolations() {
        val robustness = RobustnessConfig(
            rejectTofZeroDistance = false,
            rejectOutOfRangeDistance = false
        )

        assertFalse(ImportMeasurementRejector.rejectDistance(0.0, 10.0, 200.0, robustness))
        assertFalse(ImportMeasurementRejector.rejectDistance(500.0, 10.0, 200.0, robustness))
    }

    @Test
    fun rejectLux_rejectsNonFiniteAndOutOfRangeValues() {
        val robustness = RobustnessConfig()

        assertTrue(ImportMeasurementRejector.rejectLux(Double.POSITIVE_INFINITY, 0.0, 50_000.0, robustness))
        assertTrue(ImportMeasurementRejector.rejectLux(-1.0, 0.0, 50_000.0, robustness))
        assertTrue(ImportMeasurementRejector.rejectLux(60_000.0, 0.0, 50_000.0, robustness))
        assertFalse(ImportMeasurementRejector.rejectLux(500.0, 0.0, 50_000.0, robustness))
    }

    @Test
    fun dedupe_keepExisting_keepsFirstMeasurementForDuplicateTimestamp() {
        val measurements = listOf(
            ParsedMeasurement(1_000L, 30.0, 100.0),
            ParsedMeasurement(1_000L, 35.0, 120.0),
            ParsedMeasurement(2_000L, 40.0, 140.0)
        )

        val deduped = ImportTimestampDeduplicator.dedupe(
            measurements = measurements,
            policy = DuplicateResolutionPolicy.KEEP_EXISTING,
            robustness = RobustnessConfig()
        )

        assertEquals(2, deduped.size)
        assertEquals(30.0, deduped[0].distanceCm, 0.0)
        assertEquals(2_000L, deduped[1].timestampEpochMillis)
    }

    @Test
    fun dedupe_replaceWithNew_keepsLatestMeasurementForDuplicateTimestamp() {
        val measurements = listOf(
            ParsedMeasurement(1_000L, 30.0, 100.0),
            ParsedMeasurement(1_000L, 35.0, 120.0),
            ParsedMeasurement(2_000L, 40.0, 140.0)
        )

        val deduped = ImportTimestampDeduplicator.dedupe(
            measurements = measurements,
            policy = DuplicateResolutionPolicy.REPLACE_WITH_NEW,
            robustness = RobustnessConfig()
        )

        assertEquals(2, deduped.size)
        assertEquals(35.0, deduped[0].distanceCm, 0.0)
        assertEquals(120.0, deduped[0].lux, 0.0)
    }

    @Test
    fun dedupe_whenDisabled_returnsOriginalMeasurements() {
        val measurements = listOf(
            ParsedMeasurement(2_000L, 40.0, 140.0),
            ParsedMeasurement(1_000L, 30.0, 100.0),
            ParsedMeasurement(1_000L, 35.0, 120.0)
        )

        val deduped = ImportTimestampDeduplicator.dedupe(
            measurements = measurements,
            policy = DuplicateResolutionPolicy.KEEP_EXISTING,
            robustness = RobustnessConfig(deduplicateTimestamps = false)
        )

        assertEquals(measurements, deduped)
    }

    @Test
    fun detect_returnsLargeGapUsingTypicalSamplingInterval() {
        val timestamps = listOf(0L, 10_000L, 20_000L, 95_000L, 105_000L)

        val gaps = ImportGapDetector.detect(timestamps, RobustnessConfig())

        assertEquals(1, gaps.size)
        assertEquals(20_000L, gaps[0].startEpochMillis)
        assertEquals(95_000L, gaps[0].endEpochMillis)
        assertEquals(75_000L, gaps[0].durationMillis)
    }

    @Test
    fun detect_whenDisabled_returnsNoGaps() {
        val timestamps = listOf(0L, 10_000L, 200_000L)

        val gaps = ImportGapDetector.detect(
            timestamps,
            RobustnessConfig(detectImportGaps = false)
        )

        assertTrue(gaps.isEmpty())
    }

    @Test
    fun detect_withLessThanTwoTimestamps_returnsNoGaps() {
        assertTrue(ImportGapDetector.detect(emptyList(), RobustnessConfig()).isEmpty())
        assertTrue(ImportGapDetector.detect(listOf(1_000L), RobustnessConfig()).isEmpty())
    }
}
