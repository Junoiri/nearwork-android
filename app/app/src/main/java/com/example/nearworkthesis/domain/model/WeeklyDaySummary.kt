package com.example.nearworkthesis.domain.model

data class WeeklyDaySummary(
    val day: String,
    val sampleCount: Int,
    val avgDistanceCm: Double?,
    val avgLux: Double?,
    val diopterHoursTotal: Double,
    val nrs: Double,
    val lowLightMinutes: Int,
    val firstTimestampIso: String?,
    val lastTimestampIso: String?
)
