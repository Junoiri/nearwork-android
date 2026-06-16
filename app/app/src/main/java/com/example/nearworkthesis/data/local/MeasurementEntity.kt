package com.example.nearworkthesis.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurements",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["sessionId"]),
        Index(value = ["profileId", "timestampEpochMillis"], unique = true),
        Index(value = ["profileId", "localDay"])
    ]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val sessionId: Long,
    val timestampEpochMillis: Long,
    val localDay: String,
    val distanceCm: Double,
    val lux: Double
)

