// Tests the CSV parser rules: required columns, rejection reasons, and gap detection.
package com.example.nearworkthesis.data.importer

import com.example.nearworkthesis.importing.SampleCsvParser

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleCsvParserTest {

    private val parser = SampleCsvParser()

    @Test
    fun parse_countsInvalidReasons_andDuplicates_andGaps() {
        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T07:00:01,50,100")
            appendLine("2025-12-16T07:00:01,55,110") // duplicate timestamp
            appendLine("invalid-ts,50,100") // invalid timestamp
            appendLine("2025-12-16T07:00:02,999,100") // invalid distance
            appendLine("2025-12-16T07:00:03,50,-1") // invalid lux
            appendLine("2025-12-16T07:10:03,50,100") // large gap (10 minutes)
        }

        val result = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)).use { parser.parse(it) }

        assertEquals(7, result.totalRows)
        assertEquals(3, result.rejectedRows)
        assertEquals(1, result.invalidTimestampCount)
        assertEquals(1, result.invalidDistanceCount)
        assertEquals(1, result.invalidLuxCount)
        assertEquals(1, result.duplicatesInFileCount)

        // 4 valid rows, but one duplicate timestamp in-file => 3 unique timestamps before gap row => 4 unique total
        assertEquals(4, result.measurements.size)

        assertTrue(result.gaps.isEmpty())
    }

    @Test
    fun parse_handlesQuotedFields() {
        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("\"2025-12-16T07:00:00\",\"50\",\"100\"")
        }

        val result = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)).use { parser.parse(it) }
        assertEquals(1, result.totalRows)
        assertEquals(0, result.rejectedRows)
        assertEquals(1, result.measurements.size)
    }

    @Test
    fun parse_ignores20SecondGapBelowMinimumThreshold() {
        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T07:00:01,50,100")
            appendLine("2025-12-16T07:00:21,50,100") // 20s gap
        }

        val result = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)).use { parser.parse(it) }
        assertTrue(result.gaps.isEmpty())
    }

    @Test
    fun parse_acceptsHowfarColumnAliases_andIgnoresExtraDatetimeColumn() {
        val csv = buildString {
            appendLine("datetime,timestamp,alx_lx,tof_distance_mm")
            appendLine("2026-06-05T12:34:32,1780655672,44.16,1000")
            appendLine("2026-06-05T12:34:37,1780655677,48.32,707")
        }

        val result = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)).use { parser.parse(it) }

        assertEquals(2, result.totalRows)
        assertEquals(0, result.rejectedRows)
        assertEquals(2, result.measurements.size)
        assertEquals(100.0, result.measurements[0].distanceCm, 0.0001)
        assertEquals(44.16, result.measurements[0].lux, 0.0001)
        assertEquals(70.7, result.measurements[1].distanceCm, 0.0001)
        assertEquals(48.32, result.measurements[1].lux, 0.0001)
    }
}

