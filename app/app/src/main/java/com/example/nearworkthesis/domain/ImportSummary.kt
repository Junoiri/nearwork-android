package com.example.nearworkthesis.domain

data class ImportGap(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMillis: Long
)

data class ImportSummary(
    val filename: String,
    val sourceType: ImportSourceType = ImportSourceType.ASSET,
    val totalRows: Int,
    val insertedRows: Int,
    val rejectedRows: Int,
    val invalidTimestampCount: Int = 0,
    val invalidDistanceCount: Int = 0,
    val invalidLuxCount: Int = 0,
    val croppedByTimeWindowCount: Int = 0,
    val croppedByEndWindowCount: Int = 0,
    val duplicatesRemovedCount: Int = 0,
    val gapCount: Int = 0,
    val largestGapDurationMillis: Long? = null,
    val gaps: List<ImportGap> = emptyList(),
    val firstTimestampEpochMillis: Long?,
    val lastTimestampEpochMillis: Long?,
    val timezoneId: String? = null,
    val lowLightThresholdLux: Int? = null,
    val nearworkDistanceThresholdCm: Int? = null,
    val breakGapSeconds: Int? = null,
    val minSessionDurationSeconds: Int? = null,
    val closeDistanceThresholdCm: Int? = null,
    val extremeCloseThresholdCm: Int? = null,
    val smoothingWindowSize: Int? = null,
    val duplicateResolutionPolicy: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING,
    val duplicatesEncounteredCount: Int = 0,
    val duplicatesKeptCount: Int = 0,
    val duplicatesReplacedCount: Int = 0
)

