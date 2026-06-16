package com.example.nearworkthesis.domain.analysis

data class AnalysisThresholds(
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int,
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistanceThresholdCm: Int,
    val extremeCloseThresholdCm: Int
)

data class AnalysisPipelineConfig(
    val smoothingWindowSize: Int,
    val dedupeRule: String,
    val distanceRangeMinCm: Double,
    val distanceRangeMaxCm: Double,
    val luxRangeMin: Double,
    val luxRangeMax: Double,
    val gapThresholdSeconds: Int,
    val robustness: RobustnessConfig = RobustnessConfig()
)

data class AnalysisTimeHandling(
    val timezoneId: String,
    val statement: String
)

data class AnalysisConfig(
    val thresholds: AnalysisThresholds,
    val pipeline: AnalysisPipelineConfig,
    val timeHandling: AnalysisTimeHandling
)
