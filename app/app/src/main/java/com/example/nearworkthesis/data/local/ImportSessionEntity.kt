package com.example.nearworkthesis.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_sessions",
    indices = [
        Index(value = ["profileId"])
    ]
)
data class ImportSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

