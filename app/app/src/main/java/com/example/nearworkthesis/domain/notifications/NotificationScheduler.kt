package com.example.nearworkthesis.domain.notifications

import com.example.nearworkthesis.domain.ImportSummary

interface NotificationScheduler {
    fun ensureChannels()
    suspend fun rescheduleDailyReminder()
    suspend fun cancelDailyReminder()
    suspend fun enqueuePostImportSummary(summary: ImportSummary)
}
