// Quick check that a real UF2 file can be parsed into measurements.
package com.example.nearworkthesis.importing.howfar

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HowfarDataParserTest {

    @Test
    fun reconstructMonotonicTimestampMillis_shiftsResetCyclesForward() {
        val corrected = reconstructMonotonicTimestampMillis(
            listOf(
                946_684_800_000L,
                946_684_805_000L,
                946_684_810_000L,
                946_684_800_000L,
                946_684_805_000L
            ),
            anchorMillis = 1_700_000_000_000L,
            forceAnchorShift = true
        )

        assertEquals(
            listOf(
                1_699_999_984_000L,
                1_699_999_989_000L,
                1_699_999_994_000L,
                1_699_999_995_000L,
                1_700_000_000_000L
            ),
            corrected
        )
    }

    @Test
    fun reconstructMonotonicTimestampMillis_preserves_same_second_duplicates_in_cycle() {
        val corrected = reconstructMonotonicTimestampMillis(
            listOf(
                946_684_800_000L,
                946_684_800_000L,
                946_684_805_000L
            ),
            anchorMillis = 1_700_000_000_000L,
            forceAnchorShift = true
        )

        assertEquals(
            listOf(
                1_699_999_995_000L,
                1_699_999_995_000L,
                1_700_000_000_000L
            ),
            corrected
        )
    }

    @Test
    fun opt3004CodeToLux_decodesKnownCode() {
        // I keep the raw decode test separate so calibration changes do not hide bit-packing regressions.
        val lux = opt3004CodeToLux(0x1234)

        assertEquals(11.28, lux, 0.000001)
    }

    @Test
    fun calibrateAlsLux_appliesRegressionConstants() {
        // I pin the thesis regression here so any future tweak has to be intentional.
        val calibratedLux = calibrateAlsLux(11.28)

        assertEquals(11.23512, calibratedLux, 0.000001)
    }

    @Test
    fun opt3004CodeToLux_returnsZeroForZeroCode() {
        // I floor calibration at zero so a valid dark reading cannot become a rejected or clamped negative lux.
        val lux = calibrateAlsLux(opt3004CodeToLux(0x0000))

        assertEquals(0.0, lux, 0.0)
    }

    @Test
    fun toMeasurement_usesCalibratedLuxValue() {
        // I verify the record path directly so parsed measurements inherit the calibrated lux, not the raw decode.
        val measurement = HowfarRecord(
            timestampSeconds = 1L,
            alsCode = 0x1234,
            tofDistanceMm = 250
        ).toMeasurement()

        assertEquals(11.23512, measurement.lux, 0.000001)
        assertEquals(25.0, measurement.distanceCm, 0.0)
    }

    @Test
    fun opt3004CodeToLux_returnsZeroForZeroCode_beforeCalibration() {
        // I leave the raw zero-code behavior explicit so decode and calibration stay independently testable.
        val lux = opt3004CodeToLux(0x0000)

        assertEquals(0.0, lux, 0.0)
    }

    @Test
    fun opt3004CodeToLux_handlesHighRangeCodeWithoutOverflow() {
        // I still want the raw decoder finite even before the calibration step is applied.
        val lux = opt3004CodeToLux(0xFFFF)

        assertTrue("Expected lux to stay positive.", lux > 0.0)
        assertFalse("Expected lux to remain finite.", lux.isInfinite() || lux.isNaN())
    }

    @Test
    // The UF2 file is the same data the device exposes over USB storage.
    fun parseUf2_withRealFile_producesMeasurements() {
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")
        val result = HowfarDataParser().parseUf2(uf2Bytes)

        assertTrue("Expected UF2 file to be non-empty.", uf2Bytes.isNotEmpty())
        assertTrue("Expected firmware version to be present.", result.firmwareVersion > 0)
        assertTrue(
            "Expected record count to be >= decoded measurements.",
            result.parseResult.totalRows >= result.measurements.size
        )
        if (result.measurements.isNotEmpty()) {
            val anyTimestamp = result.measurements.maxOf { it.timestampEpochMillis }
            assertTrue("Expected at least one positive timestamp.", anyTimestamp > 0L)
        }
    }

    @Test
    fun parseUf2_withCropStart_discardsMeasurementsBeforeWindow() {
        val anchorMillis = 1_700_000_000_000L
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")
        val fullResult = HowfarDataParser().parseUf2(
            uf2Bytes,
            anchorMillis = anchorMillis
        )
        val uncroppedLastTimestamp = fullResult.parseResult.lastTimestampEpochMillis
            ?: error("Expected parsed timestamps.")
        val cropStartMillis = uncroppedLastTimestamp - (60L * 60L * 1000L)

        val croppedResult = HowfarDataParser().parseUf2(
            uf2Bytes,
            anchorMillis = anchorMillis,
            cropStartMillis = cropStartMillis
        )

        assertTrue("Expected some records to be cropped.", croppedResult.croppedByTimeWindowCount > 0)
        assertEquals(
            croppedResult.croppedByTimeWindowCount,
            croppedResult.parseResult.croppedByTimeWindowCount
        )
        assertTrue(
            "Expected cropped result to contain fewer measurements.",
            croppedResult.measurements.size < fullResult.measurements.size
        )
        assertTrue(
            "Expected all retained timestamps to stay within the selected window.",
            croppedResult.measurements.all { it.timestampEpochMillis >= cropStartMillis }
        )
    }

    @Test
    fun reconstructMonotonicTimestampMillis_skipsAnchorWhenLastTimestampIsPlausible() {
        val corrected = reconstructMonotonicTimestampMillis(
            listOf(
                1_700_000_000_000L - 10_000L,
                1_700_000_000_000L - 5_000L,
                1_700_000_000_000L
            ),
            anchorMillis = 1_700_000_000_000L,
            forceAnchorShift = false
        )

        assertEquals(
            listOf(
                1_699_999_990_000L,
                1_699_999_995_000L,
                1_700_000_000_000L
            ),
            corrected
        )
    }

    @Test
    fun reconstructMonotonicTimestampMillis_emptyInput_returnsEmptyList() {
        val corrected = reconstructMonotonicTimestampMillis(
            emptyList(),
            anchorMillis = 1_700_000_000_000L,
            forceAnchorShift = false
        )

        assertTrue(corrected.isEmpty())
    }

    @Test
    fun reconstructMonotonicTimestampMillis_defaultOverload_preservesAlreadyPlausibleSeries() {
        val now = System.currentTimeMillis()
        val rawTimestamps = listOf(now - 10_000L, now - 5_000L, now)

        val corrected = reconstructMonotonicTimestampMillis(rawTimestamps)

        assertEquals(rawTimestamps, corrected)
    }

    @Test
    fun parseUf2_withCropEnd_discardsMeasurementsAfterWindow() {
        val anchorMillis = 1_700_000_000_000L
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")
        val fullResult = HowfarDataParser().parseUf2(
            uf2Bytes,
            anchorMillis = anchorMillis,
            forceAnchorShift = true
        )
        val uncroppedFirstTimestamp = fullResult.parseResult.firstTimestampEpochMillis
            ?: error("Expected parsed timestamps.")
        val cropEndMillis = uncroppedFirstTimestamp + (60L * 60L * 1000L)

        val croppedResult = HowfarDataParser().parseUf2(
            uf2Bytes,
            anchorMillis = anchorMillis,
            cropEndMillis = cropEndMillis,
            forceAnchorShift = true
        )

        assertTrue("Expected some records to be cropped by the end window.", croppedResult.croppedByEndWindowCount > 0)
        assertEquals(
            croppedResult.croppedByEndWindowCount,
            croppedResult.parseResult.croppedByEndWindowCount
        )
        assertTrue(
            "Expected all retained timestamps to stay before the selected end window.",
            croppedResult.measurements.all { it.timestampEpochMillis <= cropEndMillis }
        )
    }

    @Test
    fun parseUf2_withUnsupportedFirmwareOverride_throws() {
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")

        try {
            HowfarDataParser().parseUf2(
                uf2Bytes,
                firmwareVersionOverride = HowfarDatabase.latestVersion + 1
            )
            fail("Expected unsupported firmware override to fail.")
        } catch (expected: IllegalStateException) {
            assertTrue(
                "Expected unsupported-version message.",
                expected.message?.contains("Unsupported HowFar record version") == true
            )
        }
    }

    private fun readResource(path: String): ByteArray {
        val stream: InputStream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Test resource not found: $path")
        return stream.use { it.readBytes() }
    }
}
