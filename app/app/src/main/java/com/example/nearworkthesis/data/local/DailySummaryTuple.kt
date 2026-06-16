package com.example.nearworkthesis.data.local

data class DailySummaryTuple(
    val day: String,
    val sampleCount: Int,
    val avgDistanceCm: Double?,
    val minDistanceCm: Double?,
    val maxDistanceCm: Double?,
    val avgLux: Double?,
    val minLux: Double?,
    val maxLux: Double?,
    val firstTimestampIso: String?,
    val lastTimestampIso: String?
)
