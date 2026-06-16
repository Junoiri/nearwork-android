package com.example.nearworkthesis.domain.export

data class ResultsPackCsvs(
    val dailyResultsCsv: String,
    val sessionsResultsCsv: String,
    val importQualityCsv: String,
    val manifestJson: String,
    val daysWithSamples: Int
)

data class DailyResultsRow(
    val date: String,
    val sampleCount: Int,
    val diopterHoursTotal: Double,
    // I carry the daily session-average NRS explicitly so the CSV builder does not have to recompute analysis state.
    val nrsSessionAverage: Double,
    val lowLightMinutes: Int,
    val longestSessionSeconds: Long,
    val riskySessionCount: Int,
    val gapCount: Int?,
    val largestGapSeconds: Int?
)

data class SessionResultsRow(
    val date: String,
    val sessionStartIsoUtc: String,
    val sessionEndIsoUtc: String,
    val durationSeconds: Long,
    val avgDistanceCm: Double,
    val minDistanceCm: Double,
    // I store per-session mean lux here because the session CSV now needs the exported descriptive light metric.
    val meanLux: Double,
    val diopterHoursInSession: Double,
    // I store per-session NRS here so the CSV builder can emit the already aligned session-scoped score.
    val nrs: Double,
    val lowLightSecondsInSession: Long,
    val flagsCloseDistance: Int,
    val flagsLowLight: Int,
    val flagsExtremeClose: Int
)

data class ImportQualityRow(
    val importedAtIsoUtc: String,
    val sourceType: String?,
    val filename: String?,
    val totalRows: Int?,
    val insertedRows: Int?,
    val rejectedRows: Int?,
    val rejectedTimestampCount: Int?,
    val rejectedDistanceCount: Int?,
    val rejectedLuxCount: Int?,
    val duplicatesRemovedCount: Int?,
    val gapCount: Int?,
    val largestGapSeconds: Int?,
    val smoothingWindow: Int?,
    val thresholdsLowLightLux: Int,
    val thresholdsNearworkCm: Int,
    val thresholdsBreakGapSec: Int,
    val thresholdsMinSessionSec: Int,
    val thresholdsCloseDistanceCm: Int,
    val thresholdsExtremeCloseCm: Int
)
