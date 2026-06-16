package com.example.nearworkthesis.domain.model

data class MonthDaySummary(
    val day: String,
    val sampleCount: Int,
    val diopterHoursTotal: Double,
    val nrs: Double,
    val lowLightMinutes: Int
)
