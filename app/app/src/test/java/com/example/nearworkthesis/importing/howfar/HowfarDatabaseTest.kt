package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HowfarDatabaseTest {

    @Test
    fun codecFor_latestVersionReturnsExpectedCodec() {
        val codec = HowfarDatabase.codecFor(HowfarDatabase.latestVersion)

        assertEquals(HowfarRecord2024091501, codec)
        assertEquals(8, HowfarDatabase.latestRecordSize())
    }

    @Test
    fun codecFor_unsupportedVersionThrows() {
        val error = kotlin.runCatching {
            HowfarDatabase.codecFor(123)
        }.exceptionOrNull()

        assertEquals("Unsupported HowFar record version: 123", error?.message)
    }

    @Test
    fun decode_andToMeasurement_convertRawFieldsToCalibratedMeasurement() {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(100)
            .putShort(0x1FFF.toShort())
            .putShort(350.toShort())
            .array()

        val record = HowfarRecord2024091501.decode(payload)
        val measurement = record.toMeasurement()

        assertEquals(100L, record.timestampSeconds)
        assertEquals(0x1FFF, record.alsCode)
        assertEquals(350, record.tofDistanceMm)
        assertEquals(100_000L, measurement.timestampEpochMillis)
        assertEquals(35.0, measurement.distanceCm, 0.0)
        assertTrue(measurement.lux > 0.0)
    }

    @Test
    fun decode_rejectsTooSmallPayload() {
        val error = kotlin.runCatching {
            HowfarRecord2024091501.decode(ByteArray(7))
        }.exceptionOrNull()

        assertEquals("Record payload too small.", error?.message)
    }

    @Test
    fun howfarRecord_formatLocalDateTime_matchesSystemTimezoneFormatting() {
        val record = HowfarRecord(timestampSeconds = 0L, alsCode = 0, tofDistanceMm = 0)

        val formatted = record.formatLocalDateTime()

        val expected = java.time.Instant.ofEpochSecond(0L)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .toString()
        assertEquals(expected, formatted)
    }

    @Test
    fun opt3004CodeToLux_andCalibration_coverConversionBranches() {
        assertEquals(0.01, opt3004CodeToLux(0x0001), 0.0)
        assertEquals(0.02, opt3004CodeToLux(0x1001), 0.0)
        assertEquals(0.0, calibrateAlsLux(-100.0), 0.0)
        assertTrue(calibrateAlsLux(10.0) > 9.0)
    }
}
