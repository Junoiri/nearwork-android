package com.example.nearworkthesis.domain.analysis

data class NearworkSession(
    val startTimestampMillis: Long,
    val endTimestampMillis: Long,
    val durationSeconds: Long,
    val avgDistanceCm: Double,
    val minDistanceCm: Double,
    val diopterHoursInSession: Double,
    val lowLightSecondsInSession: Long
)

