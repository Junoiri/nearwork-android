package com.example.nearworkthesis.domain.export

import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsPackManifestBuilderTest {

    @Test
    fun manifest_containsRequiredKeys() {
        val settingsUsed = AnalysisConfig(
            thresholds = AnalysisThresholds(
                lowLightThresholdLux = 300,
                nearworkDistanceThresholdCm = 60,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 60,
                closeDistanceThresholdCm = 30,
                extremeCloseThresholdCm = 20
            ),
            pipeline = AnalysisPipelineConfig(
                smoothingWindowSize = 5,
                dedupeRule = "same timestamp keep last",
                distanceRangeMinCm = 10.0,
                distanceRangeMaxCm = 200.0,
                luxRangeMin = 0.0,
                luxRangeMax = 50_000.0,
                gapThresholdSeconds = 90
            ),
            timeHandling = AnalysisTimeHandling(
                timezoneId = "Europe/Warsaw",
                statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
            )
        )

        val manifest = ResultsPackManifestBuilder.Manifest(
            exportCreatedAtIsoUtc = "2025-12-18T00:00:00",
            appVersionName = "1.0-test",
            appVersionCode = 42,
            profileId = 7L,
            profileName = "Test Profile",
            startDay = "2025-12-01",
            endDay = "2025-12-07",
            preprocessingSmoothingWindow = 5,
            dedupeRule = "same timestamp keep last",
            duplicateResolutionPolicy = "keep_existing",
            distanceRangeMinCm = 10.0,
            distanceRangeMaxCm = 200.0,
            luxRangeMin = 0.0,
            luxRangeMax = 50_000.0,
            gapDetectionThresholdSeconds = 90.0,
            sessionNearworkDistanceThresholdCm = 60,
            sessionBreakGapSeconds = 60,
            sessionMinSessionDurationSeconds = 60,
            sessionCloseDistanceThresholdCm = 30,
            sessionExtremeCloseThresholdCm = 20,
            lowLightThresholdLux = 300,
            timezoneId = "Europe/Warsaw",
            timeHandlingStatement = "measurements stored as epoch millis UTC; localDay derived in timezoneId",
            settingsUsed = settingsUsed
        )

        val json = ResultsPackManifestBuilder.build(manifest)

        assertTrue(json.contains("\"settingsUsed\":{\"thresholds\":{\"low_light_threshold_lux\":300"))
        assertTrue(json.contains("\"dataSources\":[\"daily\",\"sessions\",\"import_quality\"]"))
        assertTrue(json.contains("\"smoothingWindow\":5"))
        assertTrue(json.contains("\"duplicateResolutionPolicy\":\"keep_existing\""))
        assertTrue(json.contains("\"gapDetectionThresholdSeconds\":90.0"))
        assertTrue(json.contains("\"low_light_threshold_lux\":300"))
        assertTrue(json.contains("\"close_distance_threshold_cm\":30"))
        assertTrue(json.contains("\"extreme_close_threshold_cm\":20"))
        assertTrue(json.contains("\"nearwork_distance_threshold_cm\":60"))
        assertTrue(json.contains("\"profileId\":7"))
        assertTrue(json.contains("\"timezoneId\":\"Europe/Warsaw\""))
        assertTrue(json.contains("\"dayGrouping\":\"measurements stored as epoch millis UTC; localDay derived in timezoneId\""))
        assertTrue(!json.contains("\"analysisMode\""))
        assertTrue(!json.contains("\"snapshotSource\""))
        assertTrue(!json.contains("\"snapshotFallbackReason\""))
    }

    @Test
    fun escapingAndNonFiniteFormatting_useStableFallbacks() {
        val settingsUsed = AnalysisConfig(
            thresholds = AnalysisThresholds(
                lowLightThresholdLux = 300,
                nearworkDistanceThresholdCm = 60,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 60,
                closeDistanceThresholdCm = 30,
                extremeCloseThresholdCm = 20
            ),
            pipeline = AnalysisPipelineConfig(
                smoothingWindowSize = 5,
                dedupeRule = "rule\twith tab",
                distanceRangeMinCm = Double.NaN,
                distanceRangeMaxCm = Double.POSITIVE_INFINITY,
                luxRangeMin = Double.NEGATIVE_INFINITY,
                luxRangeMax = 50_000.0,
                gapThresholdSeconds = 90
            ),
            timeHandling = AnalysisTimeHandling(
                timezoneId = "UTC",
                statement = "line1\nline2"
            )
        )
        val manifest = ResultsPackManifestBuilder.Manifest(
            exportCreatedAtIsoUtc = "2025-12-18T00:00:00",
            appVersionName = "1.0-\"quoted\"",
            appVersionCode = 42,
            profileId = 7L,
            profileName = "Test\\Profile",
            startDay = "2025-12-01",
            endDay = "2025-12-07",
            preprocessingSmoothingWindow = 5,
            dedupeRule = "same\nrule",
            duplicateResolutionPolicy = "keep\tnew",
            distanceRangeMinCm = Double.NaN,
            distanceRangeMaxCm = Double.POSITIVE_INFINITY,
            luxRangeMin = Double.NEGATIVE_INFINITY,
            luxRangeMax = 50_000.0,
            gapDetectionThresholdSeconds = Double.NaN,
            sessionNearworkDistanceThresholdCm = 60,
            sessionBreakGapSeconds = 60,
            sessionMinSessionDurationSeconds = 60,
            sessionCloseDistanceThresholdCm = 30,
            sessionExtremeCloseThresholdCm = 20,
            lowLightThresholdLux = 300,
            timezoneId = "UTC",
            timeHandlingStatement = "tabs\tand\nlines",
            settingsUsed = settingsUsed
        )

        val json = ResultsPackManifestBuilder.build(manifest)

        assertTrue(json.contains("\"appVersionName\":\"1.0-\\\"quoted\\\"\""))
        assertTrue(json.contains("\"profileName\":\"Test\\\\Profile\""))
        assertTrue(json.contains("\"dedupeRule\":\"same\\nrule\""))
        assertTrue(json.contains("\"duplicateResolutionPolicy\":\"keep\\tnew\""))
        assertTrue(json.contains("\"dayGrouping\":\"tabs\\tand\\nlines\""))
        assertTrue(json.contains("\"distanceCmMin\":0.0"))
        assertTrue(json.contains("\"distanceCmMax\":0.0"))
        assertTrue(json.contains("\"luxMin\":0.0"))
        assertTrue(json.contains("\"gapDetectionThresholdSeconds\":0.0"))
        assertTrue(json.contains("\"statement\":\"line1\\nline2\""))

        val formatDouble = Class.forName("com.example.nearworkthesis.domain.export.ResultsPackManifestBuilderKt")
            .getDeclaredMethod("formatDouble", Double::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val escapeJson = Class.forName("com.example.nearworkthesis.domain.export.ResultsPackManifestBuilderKt")
            .getDeclaredMethod("escapeJson", String::class.java)
            .apply { isAccessible = true }

        assertEquals("0.0", formatDouble.invoke(null, Double.NaN))
        assertEquals("12.5", formatDouble.invoke(null, 12.5))
        assertEquals("", escapeJson.invoke(null, ""))
        assertEquals("a\\n\\t\\\"b\\\\c", escapeJson.invoke(null, "a\n\t\"b\\c"))
    }
}
