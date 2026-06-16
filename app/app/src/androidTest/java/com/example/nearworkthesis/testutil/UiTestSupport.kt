package com.example.nearworkthesis.testutil

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import com.example.nearworkthesis.app.MainActivity
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling

fun mainActivityUiTestIntent(
    context: Context = ApplicationProvider.getApplicationContext()
): Intent {
    return Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_SKIP_SPLASH_FOR_UI_TESTS, true)
}

fun ComposeTestRule.waitForTag(
    tag: String,
    timeoutMillis: Long = 5_000L
) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

fun defaultAnalysisConfig(timezoneId: String = "UTC"): AnalysisConfig {
    return AnalysisConfig(
        thresholds = AnalysisThresholds(
            lowLightThresholdLux = 300,
            nearworkDistanceThresholdCm = 60,
            breakGapSeconds = 120,
            minSessionDurationSeconds = 300,
            closeDistanceThresholdCm = 30,
            extremeCloseThresholdCm = 20
        ),
        pipeline = AnalysisPipelineConfig(
            smoothingWindowSize = 5,
            dedupeRule = "same timestamp keep last",
            distanceRangeMinCm = 10.0,
            distanceRangeMaxCm = 250.0,
            luxRangeMin = 0.0,
            luxRangeMax = 50_000.0,
            gapThresholdSeconds = 300
        ),
        timeHandling = AnalysisTimeHandling(
            timezoneId = timezoneId,
            statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
        )
    )
}
