package com.example.nearworkthesis.domain.model

data class ImportSession(
    val id: Long = 0,
    val profileId: Long,
    val importedAtEpochMillis: Long,
    val filename: String,
    val totalRows: Int = 0,
    val insertedRows: Int = 0,
    val rejectedRows: Int = 0,
    val invalidTimestampCount: Int = 0,
    val invalidDistanceCount: Int = 0,
    val invalidLuxCount: Int = 0,
    val duplicatesRemovedCount: Int = 0,
    val gapCount: Int = 0,
    val largestGapDurationMillis: Long? = null,
    val firstTimestampEpochMillis: Long? = null,
    val lastTimestampEpochMillis: Long? = null,
    val source: String = "HOWFAR_USB",
    val timezoneId: String = "UTC",
    val note: String? = null,
    val status: String? = null,
    val appVersion: String? = null,
    val schemaVersion: String? = null
)

