package com.example.nearworkthesis.data.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

interface NotificationPermissionChecker {
    fun canPostNotifications(): Boolean
}

class NotificationPermissionCheckerImpl(
    private val context: Context
) : NotificationPermissionChecker {
    override fun canPostNotifications(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
