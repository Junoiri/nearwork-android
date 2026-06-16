package com.example.nearworkthesis.data.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nearworkthesis.R
import com.example.nearworkthesis.app.MainActivity
import com.example.nearworkthesis.domain.notifications.LastNotification
import com.example.nearworkthesis.navigation.Route

class PostImportSummaryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val inserted = inputData.getInt(KEY_INSERTED, 0)
        val rejected = inputData.getInt(KEY_REJECTED, 0)
        val firstDay = inputData.getString(KEY_FIRST_DAY).orEmpty()
        val lastDay = inputData.getString(KEY_LAST_DAY).orEmpty()
        val openDay = inputData.getString(KEY_OPEN_DAY).orEmpty()

        if (inserted == 0 && rejected == 0) return Result.success()

        val range = when {
            firstDay.isNotBlank() && lastDay.isNotBlank() && firstDay != lastDay -> "$firstDay to $lastDay"
            firstDay.isNotBlank() -> firstDay
            lastDay.isNotBlank() -> lastDay
            else -> "recent samples"
        }

        NotificationChannels.ensure(applicationContext)
        val intent = Intent(Intent.ACTION_VIEW, Route.Daily.deepLink(openDay.ifBlank { null })).apply {
            setClass(applicationContext, MainActivity::class.java)
        }
        val pendingIntent = PendingIntentFactory.activity(applicationContext, IMPORT_REQUEST_CODE, intent)

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.IMPORTS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Import complete")
            .setContentText("$inserted inserted, $rejected rejected - $range")
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
        notificationManager.notify(IMPORT_NOTIFICATION_ID, notification)
        DataStoreNotificationHistoryRepository(applicationContext).setLastNotification(
            LastNotification(
                title = "Import complete",
                body = "$inserted inserted, $rejected rejected - $range",
                sentAtEpochMillis = System.currentTimeMillis()
            )
        )
        return Result.success()
    }

    companion object {
        const val KEY_INSERTED = "inserted"
        const val KEY_REJECTED = "rejected"
        const val KEY_FIRST_DAY = "first_day"
        const val KEY_LAST_DAY = "last_day"
        const val KEY_OPEN_DAY = "open_day"
    }
}

private const val IMPORT_NOTIFICATION_ID = 1002
private const val IMPORT_REQUEST_CODE = 2002





