package com.example.nearworkthesis.domain.model

data class HistoryDaySummary(
    val day: String,
    val sampleCount: Int,
    val avgDistanceCm: Double?,
    val avgLux: Double?,
    val diopterHoursTotal: Double,
    val nrs: Double,
    val firstTimestampIso: String?,
    val lastTimestampIso: String?
)
