package com.example.nearworkthesis.domain.model

data class Measurement(
    val id: Long = 0,
    val profileId: Long,
    val sessionId: Long,
    val timestampEpochMillis: Long,
    val localDay: String,
    val distanceCm: Double,
    val lux: Double
)

