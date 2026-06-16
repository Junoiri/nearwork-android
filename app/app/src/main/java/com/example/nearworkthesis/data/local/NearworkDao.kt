package com.example.nearworkthesis.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NearworkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity): Long

    @Query("SELECT * FROM profiles")
    suspend fun getProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: Long): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAtEpochMillis ASC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportSession(session: ImportSessionEntity): Long

    @Query("SELECT * FROM import_sessions WHERE profileId = :profileId")
    suspend fun getImportSessionsForProfile(profileId: Long): List<ImportSessionEntity>

    @Query("SELECT * FROM import_sessions WHERE profileId = :profileId ORDER BY importedAtEpochMillis DESC, id DESC")
    suspend fun getImportSessionsForProfileNewest(profileId: Long): List<ImportSessionEntity>

    @Query("DELETE FROM import_sessions WHERE profileId = :profileId")
    suspend fun deleteImportSessionsForProfile(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurements(measurements: List<MeasurementEntity>): List<Long>

    @Query(
        """
            SELECT timestampEpochMillis
            FROM measurements
            WHERE profileId = :profileId
              AND timestampEpochMillis IN (:timestamps)
        """
    )
    suspend fun getExistingMeasurementTimestamps(
        profileId: Long,
        timestamps: List<Long>
    ): List<Long>

    @Query(
        """
            UPDATE measurements
            SET sessionId = :sessionId,
                localDay = :localDay,
                distanceCm = :distanceCm,
                lux = :lux
            WHERE profileId = :profileId
              AND timestampEpochMillis = :timestampEpochMillis
        """
    )
    suspend fun updateMeasurementByProfileAndTimestamp(
        profileId: Long,
        timestampEpochMillis: Long,
        sessionId: Long,
        localDay: String,
        distanceCm: Double,
        lux: Double
    ): Int

    @Query("SELECT * FROM measurements WHERE profileId = :profileId")
    suspend fun getMeasurementsForProfile(profileId: Long): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurements WHERE profileId = :profileId")
    suspend fun countMeasurements(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM measurements WHERE profileId = :profileId")
    fun observeMeasurementCount(profileId: Long): Flow<Int>

    @Query("DELETE FROM measurements WHERE profileId = :profileId")
    suspend fun deleteMeasurementsForProfile(profileId: Long)

    @Query(
        """
            DELETE FROM measurements
            WHERE profileId = :profileId
              AND localDay = :localDay
        """
    )
    suspend fun deleteMeasurementsForLocalDay(profileId: Long, localDay: String): Int

    @Query(
        """
            SELECT MAX(localDay)
            FROM measurements
            WHERE profileId = :profileId
        """
    )
    suspend fun getLatestLocalDay(profileId: Long): String?

    @Query(
        """
            SELECT 
                localDay AS day,
                COUNT(*) AS sampleCount,
                AVG(distanceCm) AS avgDistanceCm,
                MIN(distanceCm) AS minDistanceCm,
                MAX(distanceCm) AS maxDistanceCm,
                AVG(lux) AS avgLux,
                MIN(lux) AS minLux,
                MAX(lux) AS maxLux,
                MIN(datetime(timestampEpochMillis / 1000, 'unixepoch')) AS firstTimestampIso,
                MAX(datetime(timestampEpochMillis / 1000, 'unixepoch')) AS lastTimestampIso
            FROM measurements
            WHERE profileId = :profileId
              AND localDay = :day
            GROUP BY localDay
        """
    )
    fun getDailySummary(
        profileId: Long,
        day: String
    ): Flow<DailySummaryTuple?>

    @Query(
        """
            SELECT 
                localDay AS day,
                COUNT(*) as sampleCount,
                AVG(distanceCm) as avgDistanceCm,
                AVG(lux) as avgLux,
                MIN(datetime(timestampEpochMillis / 1000, 'unixepoch')) as firstTimestampIso,
                MAX(datetime(timestampEpochMillis / 1000, 'unixepoch')) as lastTimestampIso
            FROM measurements
            WHERE profileId = :profileId
            GROUP BY day
            ORDER BY day DESC
        """
    )
    fun getHistoryDays(profileId: Long): Flow<List<HistoryDayTuple>>

    @Query(
        """
            SELECT
                localDay AS day,
                COUNT(*) as sampleCount,
                AVG(distanceCm) as avgDistanceCm,
                AVG(lux) as avgLux,
                MIN(datetime(timestampEpochMillis / 1000, 'unixepoch')) as firstTimestampIso,
                MAX(datetime(timestampEpochMillis / 1000, 'unixepoch')) as lastTimestampIso
            FROM measurements
            WHERE profileId = :profileId
              AND localDay IN (
                SELECT day FROM (
                    SELECT DISTINCT localDay AS day
                    FROM measurements
                    WHERE profileId = :profileId
                    ORDER BY day DESC
                    LIMIT :days
                )
              )
            GROUP BY day
            ORDER BY day ASC
        """
    )
    fun getLastNDays(
        profileId: Long,
        days: Int
    ): Flow<List<WeeklyDayTuple>>

    @Query(
        """
            SELECT DISTINCT localDay AS day
            FROM measurements
            WHERE profileId = :profileId
            ORDER BY day ASC
        """
    )
    fun observeAvailableDays(profileId: Long): Flow<List<String>>

    @Query(
        """
            SELECT DISTINCT localDay AS day
            FROM measurements
            WHERE profileId = :profileId
            ORDER BY day ASC
        """
    )
    suspend fun getAvailableDays(profileId: Long): List<String>

    @Query(
        """
            SELECT * FROM measurements
            WHERE profileId = :profileId
              AND localDay = :localDay
            ORDER BY timestampEpochMillis ASC
        """
    )
    suspend fun getMeasurementsForLocalDay(
        profileId: Long,
        localDay: String
    ): List<MeasurementEntity>

    @Query(
        """
            SELECT * FROM measurements
            WHERE profileId = :profileId
              AND localDay >= :startDay
              AND localDay <= :endDay
            ORDER BY timestampEpochMillis ASC
        """
    )
    suspend fun getMeasurementsForLocalDayRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): List<MeasurementEntity>

    @Query(
        """
            SELECT 
                localDay AS day,
                COUNT(*) AS sampleCount,
                AVG(distanceCm) AS avgDistanceCm,
                MIN(distanceCm) AS minDistanceCm,
                MAX(distanceCm) AS maxDistanceCm,
                AVG(lux) AS avgLux,
                MIN(lux) AS minLux,
                MAX(lux) AS maxLux,
                MIN(datetime(timestampEpochMillis / 1000, 'unixepoch')) AS firstTimestampIso,
                MAX(datetime(timestampEpochMillis / 1000, 'unixepoch')) AS lastTimestampIso
            FROM measurements
            WHERE profileId = :profileId
              AND localDay >= :startDay
              AND localDay <= :endDay
            GROUP BY day
            ORDER BY day ASC
        """
    )
    fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): Flow<List<DailySummaryTuple>>

    @Query(
        """
            SELECT 
                localDay AS day,
                COUNT(*) AS sampleCount
            FROM measurements
            WHERE profileId = :profileId
              AND localDay >= :startDay
              AND localDay <= :endDay
            GROUP BY day
            ORDER BY day ASC
        """
    )
    fun observeDaySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): Flow<List<MonthDaySummaryTuple>>
}



