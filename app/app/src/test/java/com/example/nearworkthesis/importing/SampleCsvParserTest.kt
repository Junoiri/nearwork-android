package com.example.nearworkthesis.importing

import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleCsvParserTest {

    @Test
    fun parse_emptyInput_returnsEmptyResult() {
        val parser = SampleCsvParser()

        val result = parser.parse(ByteArrayInputStream(ByteArray(0)))

        assertTrue(result.measurements.isEmpty())
        assertEquals(0, result.totalRows)
        assertEquals(0, result.rejectedRows)
        assertEquals(0, result.duplicatesInFileCount)
    }

    @Test
    fun parse_missingRequiredColumns_throws() {
        val parser = SampleCsvParser()
        val bytes = "timestamp,distance_cm\n2026-01-01 00:00:00,30\n".toByteArray()

        val error = kotlin.runCatching {
            parser.parse(ByteArrayInputStream(bytes))
        }.exceptionOrNull()

        assertEquals("Missing required CSV columns.", error?.message)
    }

    @Test
    fun parse_supportsMillimeterDistanceAndAlternativeLuxColumn() {
        val parser = SampleCsvParser()
        val bytes = """
            timestamp,tof_distance_mm,alx_lx
            2026-01-01 00:00:00,350,120
        """.trimIndent().toByteArray()

        val result = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(1, result.measurements.size)
        assertEquals(35.0, result.measurements.single().distanceCm, 0.0)
        assertEquals(120.0, result.measurements.single().lux, 0.0)
    }

    @Test
    fun parse_acceptsEpochSecondsAndIsoOffsetTimestamps() {
        val parser = SampleCsvParser()
        val bytes = """
            timestamp,distance_cm,illumination_lux
            1710000000,30,100
            2026-01-01T00:00:05+00:00,35,110
        """.trimIndent().toByteArray()

        val result = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(2, result.measurements.size)
        assertEquals(1_710_000_000_000L, result.measurements.first().timestampEpochMillis)
        assertEquals(1_767_225_605_000L, result.measurements.last().timestampEpochMillis)
    }

    @Test
    fun parse_countsInvalidTimestampDistanceAndLuxRows() {
        val parser = SampleCsvParser()
        val bytes = """
            timestamp,distance_cm,illumination_lux
            bad,30,100
            2026-01-01 00:00:00,0,100
            2026-01-01 00:00:01,30,-1
            2026-01-01 00:00:02,40,120
        """.trimIndent().toByteArray()

        val result = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(4, result.totalRows)
        assertEquals(3, result.rejectedRows)
        assertEquals(1, result.invalidTimestampCount)
        assertEquals(1, result.invalidDistanceCount)
        assertEquals(1, result.invalidLuxCount)
        assertEquals(1, result.measurements.size)
    }

    @Test
    fun parse_countsDuplicatesOnlyWhenTimestampDeduplicationIsEnabled() {
        val csv = """
            timestamp,distance_cm,illumination_lux
            2026-01-01 00:00:00,30,100
            2026-01-01 00:00:00,31,101
        """.trimIndent().toByteArray()

        val defaultResult = SampleCsvParser().parse(ByteArrayInputStream(csv))
        val disabledResult = SampleCsvParser(
            robustness = RobustnessConfig(deduplicateTimestamps = false)
        ).parse(ByteArrayInputStream(csv))

        assertEquals(1, defaultResult.duplicatesInFileCount)
        assertEquals(0, disabledResult.duplicatesInFileCount)
    }

    @Test
    fun parse_detectsLargeGapBetweenAcceptedRows() {
        val parser = SampleCsvParser()
        val bytes = """
            timestamp,distance_cm,illumination_lux
            2026-01-01 00:00:00,30,100
            2026-01-01 00:00:10,31,101
            2026-01-01 00:00:20,32,102
            2026-01-01 00:01:40,33,103
        """.trimIndent().toByteArray()

        val result = parser.parse(ByteArrayInputStream(bytes))

        assertEquals(1, result.gaps.size)
        assertEquals(80_000L, result.gaps.single().durationMillis)
        assertEquals(result.measurements.first().timestampEpochMillis, result.firstTimestampEpochMillis)
        assertEquals(result.measurements.last().timestampEpochMillis, result.lastTimestampEpochMillis)
    }

    @Test
    fun buildParseResultFromMeasurements_sortsRowsAndCountsDuplicates() {
        val result = buildParseResultFromMeasurements(
            measurements = listOf(
                ParsedMeasurement(2_000L, 32.0, 120.0),
                ParsedMeasurement(1_000L, 30.0, 100.0),
                ParsedMeasurement(1_000L, 31.0, 110.0)
            ),
            totalRows = 5,
            rejectedRows = 2,
            invalidTimestampCount = 1,
            invalidDistanceCount = 1,
            invalidLuxCount = 0,
            croppedByTimeWindowCount = 3,
            croppedByEndWindowCount = 4
        )

        assertEquals(listOf(1_000L, 1_000L, 2_000L), result.measurements.map { it.timestampEpochMillis })
        assertEquals(1, result.duplicatesInFileCount)
        assertEquals(1_000L, result.firstTimestampEpochMillis)
        assertEquals(2_000L, result.lastTimestampEpochMillis)
        assertEquals(5, result.totalRows)
        assertEquals(2, result.rejectedRows)
        assertEquals(3, result.croppedByTimeWindowCount)
        assertEquals(4, result.croppedByEndWindowCount)
    }

    @Test
    fun buildParseResultFromMeasurements_emptyList_returnsEmptyMetadata() {
        val result = buildParseResultFromMeasurements(emptyList())

        assertTrue(result.measurements.isEmpty())
        assertEquals(0, result.duplicatesInFileCount)
        assertEquals(null, result.firstTimestampEpochMillis)
        assertEquals(null, result.lastTimestampEpochMillis)
    }
}
