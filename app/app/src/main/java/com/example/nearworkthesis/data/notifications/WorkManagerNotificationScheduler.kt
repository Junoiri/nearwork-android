package com.example.nearworkthesis.data.notifications

import android.content.Context
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class WorkManagerNotificationScheduler(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val activeProfileStore: ActiveProfileStore,
    private val profileRepository: ProfileRepository,
    private val workController: NotificationWorkController = NotificationWorkControllerImpl(context),
    private val permissionChecker: NotificationPermissionChecker = NotificationPermissionCheckerImpl(context),
    private val channelInitializer: () -> Unit = { NotificationChannels.ensure(context) }
) : NotificationScheduler {

    override fun ensureChannels() {
        channelInitializer.invoke()
    }

    override suspend fun rescheduleDailyReminder() {
        val enabled = settingsStore.observeDailyReminderEnabled().first()
        if (!enabled) {
            cancelDailyReminder()
            return
        }

        val timeLocal = settingsStore.observeDailyReminderTimeLocal().first()
        val profileId = activeProfileStore.observeActiveProfileId().first() ?: return
        val profile = profileRepository.getProfile(profileId)
        val zoneId = resolveZoneId(profile?.timezoneId)
        channelInitializer.invoke()
        workController.scheduleDailyReminder(timeLocal, zoneId)
    }

    override suspend fun cancelDailyReminder() {
        workController.cancelDailyReminder()
    }

    override suspend fun enqueuePostImportSummary(summary: ImportSummary) {
        val enabled = settingsStore.observePostImportNotificationEnabled().first()
        if (!enabled) return
        if (!permissionChecker.canPostNotifications()) return

        val zoneId = resolveZoneId(summary.timezoneId)
        val firstDay = summary.firstTimestampEpochMillis?.let { toLocalDay(it, zoneId) }
        val lastDay = summary.lastTimestampEpochMillis?.let { toLocalDay(it, zoneId) }
        val openDay = lastDay ?: firstDay

        channelInitializer.invoke()
        workController.enqueuePostImportSummary(summary, firstDay, lastDay, openDay)
    }

    private fun resolveZoneId(timezoneId: String?): ZoneId {
        if (timezoneId.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(timezoneId) }.getOrElse { ZoneId.systemDefault() }
    }

    private fun toLocalDay(epochMillis: Long, zoneId: ZoneId): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toString()
    }
}

