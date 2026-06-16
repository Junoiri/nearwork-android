package com.example.nearworkthesis.domain.notifications

import kotlinx.coroutines.flow.Flow

data class LastNotification(
    val title: String,
    val body: String,
    val sentAtEpochMillis: Long
)

interface NotificationHistoryRepository {
    fun observeLastNotification(): Flow<LastNotification?>
    suspend fun setLastNotification(notification: LastNotification)
}
