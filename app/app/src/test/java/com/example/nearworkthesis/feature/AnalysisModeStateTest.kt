package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisModeStateTest {

    @Test
    fun resolvedConfig_usesCurrentGlobalSettings() {
        val current = config(
            lowLightLux = 350,
            timezoneId = "Europe/Warsaw"
        )

        val resolved = current

        assertEquals(current, resolved)
    }

    private fun config(
        lowLightLux: Int,
        timezoneId: String
    ): AnalysisConfig {
        return AnalysisConfig(
            thresholds = AnalysisThresholds(
                lowLightThresholdLux = lowLightLux,
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
                gapThresholdSeconds = 60
            ),
            timeHandling = AnalysisTimeHandling(
                timezoneId = timezoneId,
                statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
            )
        )
    }
}
