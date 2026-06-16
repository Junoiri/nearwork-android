package com.example.nearworkthesis.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDERS_ID = "nearwork_reminders"
    const val IMPORTS_ID = "nearwork_imports"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminder = NotificationChannel(
            REMINDERS_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Educational daily reminders"
        }
        val imports = NotificationChannel(
            IMPORTS_ID,
            "Imports",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Import summaries"
        }
        manager.createNotificationChannel(reminder)
        manager.createNotificationChannel(imports)
    }
}
