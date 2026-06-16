package com.example.nearworkthesis.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.nearworkthesis.domain.ImportSummary
import java.time.ZoneId

interface NotificationWorkController {
    fun scheduleDailyReminder(timeLocal: String, zoneId: ZoneId)
    fun cancelDailyReminder()
    fun enqueuePostImportSummary(summary: ImportSummary, firstDay: String?, lastDay: String?, openDay: String?)
}

class NotificationWorkControllerImpl(
    private val context: Context
) : NotificationWorkController {
    override fun scheduleDailyReminder(timeLocal: String, zoneId: ZoneId) {
        NotificationWorkHelper.scheduleDailyReminder(context, timeLocal, zoneId)
    }

    override fun cancelDailyReminder() {
        NotificationWorkHelper.cancelDailyReminder(context)
    }

    override fun enqueuePostImportSummary(summary: ImportSummary, firstDay: String?, lastDay: String?, openDay: String?) {
        val request = OneTimeWorkRequestBuilder<PostImportSummaryWorker>()
            .setInputData(
                workDataOf(
                    PostImportSummaryWorker.KEY_INSERTED to summary.insertedRows,
                    PostImportSummaryWorker.KEY_REJECTED to summary.rejectedRows,
                    PostImportSummaryWorker.KEY_FIRST_DAY to (firstDay ?: ""),
                    PostImportSummaryWorker.KEY_LAST_DAY to (lastDay ?: ""),
                    PostImportSummaryWorker.KEY_OPEN_DAY to (openDay ?: "")
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(NotificationWorkHelper.POST_IMPORT_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
