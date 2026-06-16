package com.example.nearworkthesis.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object NotificationWorkHelper {
    const val DAILY_REMINDER_WORK = "daily_reminder_work"
    const val POST_IMPORT_WORK = "post_import_summary_work"

    fun scheduleDailyReminder(
        context: Context,
        timeLocal: String,
        zoneId: ZoneId
    ) {
        val delay = calculateInitialDelay(timeLocal, zoneId)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DAILY_REMINDER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_REMINDER_WORK)
    }

    private fun calculateInitialDelay(timeLocal: String, zoneId: ZoneId): Duration {
        val parsed = runCatching { LocalTime.parse(timeLocal, TIME_FORMAT) }.getOrElse { DEFAULT_TIME }
        val now = ZonedDateTime.now(zoneId)
        var target = now.with(parsed).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return Duration.between(now, target)
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DEFAULT_TIME: LocalTime = LocalTime.of(19, 0)
