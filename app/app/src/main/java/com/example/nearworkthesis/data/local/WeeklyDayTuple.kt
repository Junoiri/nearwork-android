package com.example.nearworkthesis.data.local

data class WeeklyDayTuple(
    val day: String,
    val sampleCount: Int,
    val avgDistanceCm: Double?,
    val avgLux: Double?,
    val firstTimestampIso: String?,
    val lastTimestampIso: String?
)

