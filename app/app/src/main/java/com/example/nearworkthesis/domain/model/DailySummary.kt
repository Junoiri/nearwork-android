package com.example.nearworkthesis.domain.model

data class DailySummary(
    val day: String,
    val sampleCount: Int,
    val avgDistanceCm: Double?,
    val minDistanceCm: Double?,
    val maxDistanceCm: Double?,
    val avgLux: Double?,
    val minLux: Double?,
    val maxLux: Double?,
    val diopterHoursTotal: Double,
    val lowLightMinutes: Int,
    val firstTimestampIso: String?,
    val lastTimestampIso: String?
)
