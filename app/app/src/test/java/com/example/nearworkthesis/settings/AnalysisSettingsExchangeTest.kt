package com.example.nearworkthesis.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalysisSettingsExchangeTest {

    @Test
    fun suggestedFilename_usesProvidedDate() {
        val filename = AnalysisSettingsExchange.suggestedFilename(LocalDate.parse("2026-06-15"))

        assertEquals("howfar_settings_2026-06-15.json", filename)
    }

    @Test
    fun roundTrip_preservesPortableAnalysisSettings() {
        val settings = PortableAnalysisSettings(
            lowLightThresholdLux = 55,
            nearworkDistanceThresholdCm = 60,
            breakGapSeconds = 120,
            minSessionDurationSeconds = 180,
            closeDistanceThresholdCm = 30,
            extremeCloseThresholdCm = 20,
            replaceAlsSingleSampleSpikes = true,
            alsSpikeThresholdLux = 300.0
        )

        val json = AnalysisSettingsExchange.toJson(
            settings = settings,
            exportedAtIsoUtc = "2026-06-07T12:00:00Z"
        )
        val parsed = AnalysisSettingsExchange.parse(json)

        assertEquals(settings, parsed)
        assertTrue(json.contains("\"fileType\": \"howfar_analysis_settings\""))
        assertTrue(json.contains("\"formatVersion\": 1"))
    }

    @Test
    fun parse_rejectsUnrecognisedFileType() {
        val json = """
            {
              "fileType": "other_app_settings",
              "formatVersion": 1,
              "lowLightThresholdLux": 55,
              "nearworkDistanceThresholdCm": 60,
              "breakGapSeconds": 60,
              "minSessionDurationSeconds": 60,
              "closeDistanceThresholdCm": 30,
              "extremeCloseThresholdCm": 20,
              "replaceAlsSingleSampleSpikes": true,
              "alsSpikeThresholdLux": 300.0
            }
        """.trimIndent()

        val error = runCatching { AnalysisSettingsExchange.parse(json) }.exceptionOrNull()

        assertEquals("Unrecognised HowFar settings file.", error?.message)
    }

    @Test
    fun parse_rejectsUnsupportedVersion() {
        val json = """
            {
              "fileType": "howfar_analysis_settings",
              "formatVersion": 99,
              "lowLightThresholdLux": 55,
              "nearworkDistanceThresholdCm": 60,
              "breakGapSeconds": 60,
              "minSessionDurationSeconds": 60,
              "closeDistanceThresholdCm": 30,
              "extremeCloseThresholdCm": 20,
              "replaceAlsSingleSampleSpikes": true,
              "alsSpikeThresholdLux": 300.0
            }
        """.trimIndent()

        val error = runCatching { AnalysisSettingsExchange.parse(json) }.exceptionOrNull()

        assertEquals("Unsupported settings file version: 99.", error?.message)
    }

    @Test
    fun parse_rejectsInvalidThresholdOrdering() {
        val json = """
            {
              "fileType": "howfar_analysis_settings",
              "formatVersion": 1,
              "lowLightThresholdLux": 55,
              "nearworkDistanceThresholdCm": 60,
              "breakGapSeconds": 60,
              "minSessionDurationSeconds": 60,
              "closeDistanceThresholdCm": 20,
              "extremeCloseThresholdCm": 20,
              "replaceAlsSingleSampleSpikes": true,
              "alsSpikeThresholdLux": 300.0
            }
        """.trimIndent()

        val error = runCatching { AnalysisSettingsExchange.parse(json) }.exceptionOrNull()

        assertEquals(
            "Extreme-close threshold must be strictly less than close-distance threshold in settings file.",
            error?.message
        )
    }

    @Test
    fun parse_rejectsMalformedNonObjectInput() {
        val error = runCatching { AnalysisSettingsExchange.parse("[]") }.exceptionOrNull()

        assertEquals("Malformed settings file: expected a JSON object.", error?.message)
    }

    @Test
    fun parse_rejectsMissingField() {
        val json = """
            {
              "fileType": "howfar_analysis_settings",
              "formatVersion": 1,
              "lowLightThresholdLux": 55,
              "nearworkDistanceThresholdCm": 60,
              "breakGapSeconds": 60,
              "minSessionDurationSeconds": 60,
              "closeDistanceThresholdCm": 30,
              "replaceAlsSingleSampleSpikes": true,
              "alsSpikeThresholdLux": 300.0
            }
        """.trimIndent()

        val error = runCatching { AnalysisSettingsExchange.parse(json) }.exceptionOrNull()

        assertEquals("Malformed settings file: missing extremeCloseThresholdCm.", error?.message)
    }

    @Test
    fun parse_rejectsOutOfRangeLowLightThreshold() {
        val json = """
            {
              "fileType": "howfar_analysis_settings",
              "formatVersion": 1,
              "lowLightThresholdLux": 50001,
              "nearworkDistanceThresholdCm": 60,
              "breakGapSeconds": 60,
              "minSessionDurationSeconds": 60,
              "closeDistanceThresholdCm": 30,
              "extremeCloseThresholdCm": 20,
              "replaceAlsSingleSampleSpikes": true,
              "alsSpikeThresholdLux": 300.0
            }
        """.trimIndent()

        val error = runCatching { AnalysisSettingsExchange.parse(json) }.exceptionOrNull()

        assertEquals("Invalid low-light threshold in settings file.", error?.message)
    }

    @Test
    fun toJson_normalizesNonFiniteAlsSpikeThreshold() {
        val json = AnalysisSettingsExchange.toJson(
            settings = PortableAnalysisSettings(
                lowLightThresholdLux = 55,
                nearworkDistanceThresholdCm = 60,
                breakGapSeconds = 120,
                minSessionDurationSeconds = 180,
                closeDistanceThresholdCm = 30,
                extremeCloseThresholdCm = 20,
                replaceAlsSingleSampleSpikes = true,
                alsSpikeThresholdLux = Double.NaN
            ),
            exportedAtIsoUtc = "2026-06-07T12:00:00Z"
        )

        val parsed = AnalysisSettingsExchange.parse(json)

        assertTrue(json.contains("\"alsSpikeThresholdLux\": 0.0"))
        assertEquals(0.0, parsed.alsSpikeThresholdLux, 0.0)
    }
}
