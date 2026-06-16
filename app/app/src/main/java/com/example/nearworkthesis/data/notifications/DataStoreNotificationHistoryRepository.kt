package com.example.nearworkthesis.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nearworkthesis.domain.notifications.LastNotification
import com.example.nearworkthesis.domain.notifications.NotificationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationHistoryDataStore by preferencesDataStore(name = "notification_history")

class DataStoreNotificationHistoryRepository(
    private val context: Context
) : NotificationHistoryRepository {

    override fun observeLastNotification(): Flow<LastNotification?> {
        return context.notificationHistoryDataStore.data.map { prefs ->
            val title = prefs[Keys.title].orEmpty()
            val body = prefs[Keys.body].orEmpty()
            val sentAt = prefs[Keys.sentAt] ?: 0L
            if (title.isBlank() || body.isBlank() || sentAt <= 0L) {
                null
            } else {
                LastNotification(title = title, body = body, sentAtEpochMillis = sentAt)
            }
        }
    }

    override suspend fun setLastNotification(notification: LastNotification) {
        context.notificationHistoryDataStore.edit { prefs ->
            prefs[Keys.title] = notification.title
            prefs[Keys.body] = notification.body
            prefs[Keys.sentAt] = notification.sentAtEpochMillis
        }
    }

    private object Keys {
        val title = stringPreferencesKey("last_notification_title")
        val body = stringPreferencesKey("last_notification_body")
        val sentAt = longPreferencesKey("last_notification_epoch_millis")
    }
}
