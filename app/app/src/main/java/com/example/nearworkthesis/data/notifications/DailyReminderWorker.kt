package com.example.nearworkthesis.data.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nearworkthesis.R
import com.example.nearworkthesis.app.MainActivity
import com.example.nearworkthesis.data.local.Migrations
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.data.repository.RoomProfileRepository
import com.example.nearworkthesis.data.notifications.DataStoreNotificationHistoryRepository
import com.example.nearworkthesis.domain.notifications.LastNotification
import com.example.nearworkthesis.navigation.Route
import com.example.nearworkthesis.settings.DataStoreActiveProfileStore
import com.example.nearworkthesis.settings.DataStoreSettingsStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsStore = DataStoreSettingsStore(applicationContext)
        val enabled = settingsStore.observeDailyReminderEnabled().first()
        if (!enabled) return Result.success()

        val timeLocal = settingsStore.observeDailyReminderTimeLocal().first()
        val activeProfileStore = DataStoreActiveProfileStore(applicationContext)
        val activeProfileId = activeProfileStore.observeActiveProfileId().first() ?: return Result.success()

        val database = createDatabase(applicationContext)
        try {
            val profileRepository = RoomProfileRepository(database.nearworkDao())
            val profile = profileRepository.getProfile(activeProfileId)
            val zoneId = resolveZoneId(profile?.timezoneId)
            val today = LocalDate.now(zoneId).toString()
            val measurements = database.nearworkDao().getMeasurementsForLocalDay(activeProfileId, today)
            val body = if (measurements.isEmpty()) {
                "No samples for today yet - import or check demo data"
            } else {
                "Check today's nearwork summary"
            }

            NotificationChannels.ensure(applicationContext)
            val intent = Intent(Intent.ACTION_VIEW, Route.Daily.deepLink(today)).apply {
                setClass(applicationContext, MainActivity::class.java)
            }
            val pendingIntent = PendingIntentFactory.activity(applicationContext, DAILY_REQUEST_CODE, intent)

            val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDERS_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Daily reminder")
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = NotificationManagerCompat.from(applicationContext)
            val hasPermission = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission || !notificationManager.areNotificationsEnabled()) {
                return Result.success()
            }
            notificationManager.notify(DAILY_NOTIFICATION_ID, notification)
            DataStoreNotificationHistoryRepository(applicationContext).setLastNotification(
                LastNotification(
                    title = "Daily reminder",
                    body = body,
                    sentAtEpochMillis = System.currentTimeMillis()
                )
            )
            NotificationWorkHelper.scheduleDailyReminder(applicationContext, timeLocal, zoneId)
            return Result.success()
        } finally {
            database.close()
        }
    }

    private fun resolveZoneId(timezoneId: String?): ZoneId {
        if (timezoneId.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(timezoneId) }.getOrElse { ZoneId.systemDefault() }
    }

    private fun createDatabase(context: Context): NearworkDatabase {
        return Room.databaseBuilder(context, NearworkDatabase::class.java, "nearwork-db")
            .addMigrations(
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6
            )
            .build()
    }
}

private const val DAILY_NOTIFICATION_ID = 1001
private const val DAILY_REQUEST_CODE = 2001





