package com.example.nearworkthesis.domain.analysis

data class ValidationSummary(
    val rawSampleCount: Int,
    val processedSampleCount: Int,
    val rejectedOutliersCount: Int,
    val dedupedCount: Int,
    val gapCount: Int,
    val maxGapSeconds: Int,
    val avgDistanceRawCm: Double?,
    val avgDistanceProcessedCm: Double?,
    val avgDistanceAbsDiffCm: Double?,
    val silentDropCount: Int
)

data class DataAnalysisDay(
    val day: String,
    val rawSamples: List<NearworkSample>,
    val processedSamples: List<NearworkSample>,
    val summary: ValidationSummary
)

enum class NearworkRiskReason {
    CloseDistance,
    ExtremeClose,
    LowLight,
}

data class NearworkSessionRisk(
    val session: NearworkSession,
    val reasons: Set<NearworkRiskReason>
)

data class DailySessionInsights(
    val sessions: List<NearworkSession>,
    val longestSession: NearworkSession?,
    val flaggedSessions: List<NearworkSessionRisk>
)
