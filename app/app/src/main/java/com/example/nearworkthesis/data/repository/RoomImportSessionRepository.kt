package com.example.nearworkthesis.data.repository

import com.example.nearworkthesis.data.local.NearworkDao
import com.example.nearworkthesis.domain.model.ImportSession
import com.example.nearworkthesis.domain.repository.ImportSessionRepository

class RoomImportSessionRepository(
    private val nearworkDao: NearworkDao
) : ImportSessionRepository {

    override suspend fun getSessionsForProfile(profileId: Long): List<ImportSession> {
        return nearworkDao.getImportSessionsForProfile(profileId).map { entity ->
            ImportSession(
                id = entity.id,
                profileId = entity.profileId,
                importedAtEpochMillis = entity.importedAtEpochMillis,
                filename = entity.filename,
                totalRows = entity.totalRows,
                insertedRows = entity.insertedRows,
                rejectedRows = entity.rejectedRows,
                invalidTimestampCount = entity.invalidTimestampCount,
                invalidDistanceCount = entity.invalidDistanceCount,
                invalidLuxCount = entity.invalidLuxCount,
                duplicatesRemovedCount = entity.duplicatesRemovedCount,
                gapCount = entity.gapCount,
                largestGapDurationMillis = entity.largestGapDurationMillis,
                firstTimestampEpochMillis = entity.firstTimestampEpochMillis,
                lastTimestampEpochMillis = entity.lastTimestampEpochMillis,
                source = entity.source,
                note = entity.note,
                status = entity.status,
                appVersion = entity.appVersion,
                schemaVersion = entity.schemaVersion,
                timezoneId = entity.timezoneId
            )
        }
    }

    override suspend fun upsertSession(session: ImportSession): Long {
        return nearworkDao.upsertImportSession(
            session.toEntity()
        )
    }
}

private fun ImportSession.toEntity() = com.example.nearworkthesis.data.local.ImportSessionEntity(
    id = id,
    profileId = profileId,
    importedAtEpochMillis = importedAtEpochMillis,
    filename = filename,
    totalRows = totalRows,
    insertedRows = insertedRows,
    rejectedRows = rejectedRows,
    invalidTimestampCount = invalidTimestampCount,
    invalidDistanceCount = invalidDistanceCount,
    invalidLuxCount = invalidLuxCount,
    duplicatesRemovedCount = duplicatesRemovedCount,
    gapCount = gapCount,
    largestGapDurationMillis = largestGapDurationMillis,
    firstTimestampEpochMillis = firstTimestampEpochMillis,
    lastTimestampEpochMillis = lastTimestampEpochMillis,
    source = source,
    note = note,
    status = status,
    appVersion = appVersion,
    schemaVersion = schemaVersion,
    timezoneId = timezoneId
)

