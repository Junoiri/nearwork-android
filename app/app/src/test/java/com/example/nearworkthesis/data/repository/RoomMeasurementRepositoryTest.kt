package com.example.nearworkthesis.data.repository

import com.example.nearworkthesis.data.local.DailySummaryTuple
import com.example.nearworkthesis.data.local.HistoryDayTuple
import com.example.nearworkthesis.data.local.ImportSessionEntity
import com.example.nearworkthesis.data.local.MeasurementEntity
import com.example.nearworkthesis.data.local.NearworkDao
import com.example.nearworkthesis.data.local.ProfileEntity
import com.example.nearworkthesis.data.local.WeeklyDayTuple
import com.example.nearworkthesis.data.local.MonthDaySummaryTuple
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMeasurementRepositoryTest {

    @Test
    fun lastNDays_usesLocalDayFromDaoTuples() = runBlocking {
        val day1 = "2024-12-31"
        val day2 = "2025-01-02"
        val dao = FakeNearworkDao(
            lastNDays = listOf(
                WeeklyDayTuple(day = day1, sampleCount = 1, avgDistanceCm = 50.0, avgLux = 100.0, firstTimestampIso = null, lastTimestampIso = null),
                WeeklyDayTuple(day = day2, sampleCount = 1, avgDistanceCm = 60.0, avgLux = 200.0, firstTimestampIso = null, lastTimestampIso = null)
            ),
            measurementsByDay = mapOf(
                day1 to listOf(sampleMeasurement(day1, 1L)),
                day2 to listOf(sampleMeasurement(day2, 2L))
            )
        )
        val repo: MeasurementRepository = RoomMeasurementRepository(dao, FakeSettingsStore())

        val summaries = repo.getLastNDays(profileId = 1L, days = 2).first()

        assertEquals(listOf(day1, day2), summaries.map { it.day })
        assertEquals(listOf(day1, day2), dao.requestedLocalDays)
    }

    @Test
    fun exportRawCsv_filtersByLocalDayRange() = runBlocking {
        val day1 = "2025-01-01"
        val day2 = "2025-01-02"
        val measurements = listOf(
            sampleMeasurement(day1, 100L),
            sampleMeasurement(day2, 200L)
        )
        val dao = FakeNearworkDao(
            availableDays = listOf(day1, day2),
            rangeMeasurements = measurements
        )
        val repo: MeasurementRepository = RoomMeasurementRepository(dao, FakeSettingsStore())

        val csv = repo.exportRawCsv(profileId = 1L, startDay = day2, endDay = day2)

        assertEquals(day2, dao.lastRangeStartDay)
        assertEquals(day2, dao.lastRangeEndDay)
        assertTrue(csv.contains(",200,"))
        assertFalse(csv.contains(",100,"))
    }

    @Test
    fun deleteDay_callsDaoAndReturnsCount() = runBlocking {
        val dao = FakeNearworkDao(deleteDayCount = 4)
        val repo: MeasurementRepository = RoomMeasurementRepository(dao, FakeSettingsStore())

        val deleted = repo.deleteDay(profileId = 5L, localDay = "2025-01-10")

        assertEquals(5L, dao.lastDeleteProfileId)
        assertEquals("2025-01-10", dao.lastDeleteLocalDay)
        assertEquals(4, deleted)
    }
}

private class FakeNearworkDao(
    private val lastNDays: List<WeeklyDayTuple> = emptyList(),
    private val measurementsByDay: Map<String, List<MeasurementEntity>> = emptyMap(),
    private val availableDays: List<String> = emptyList(),
    private val rangeMeasurements: List<MeasurementEntity> = emptyList(),
    private val deleteDayCount: Int = 0
) : NearworkDao {
    val requestedLocalDays = mutableListOf<String>()
    var lastRangeStartDay: String? = null
    var lastRangeEndDay: String? = null
    var lastDeleteProfileId: Long? = null
    var lastDeleteLocalDay: String? = null

    override suspend fun upsertProfile(profile: ProfileEntity): Long = 0
    override suspend fun getProfiles(): List<ProfileEntity> = emptyList()
    override suspend fun getProfile(profileId: Long): ProfileEntity? = null
    override fun observeProfiles(): Flow<List<ProfileEntity>> = emptyFlow()
    override suspend fun insertProfile(profile: ProfileEntity): Long = 0
    override suspend fun updateProfile(profile: ProfileEntity) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun upsertImportSession(session: ImportSessionEntity): Long = 0
    override suspend fun getImportSessionsForProfile(profileId: Long): List<ImportSessionEntity> = emptyList()
    override suspend fun getImportSessionsForProfileNewest(profileId: Long): List<ImportSessionEntity> = emptyList()
    override suspend fun deleteImportSessionsForProfile(profileId: Long) = Unit
    override suspend fun insertMeasurements(measurements: List<MeasurementEntity>): List<Long> = emptyList()
    override suspend fun getExistingMeasurementTimestamps(profileId: Long, timestamps: List<Long>): List<Long> = emptyList()
    override suspend fun updateMeasurementByProfileAndTimestamp(
        profileId: Long,
        timestampEpochMillis: Long,
        sessionId: Long,
        localDay: String,
        distanceCm: Double,
        lux: Double
    ): Int = 0
    override suspend fun getMeasurementsForProfile(profileId: Long): List<MeasurementEntity> = emptyList()
    override suspend fun countMeasurements(profileId: Long): Int = 0
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = emptyFlow()
    override suspend fun deleteMeasurementsForProfile(profileId: Long) = Unit
    override suspend fun deleteMeasurementsForLocalDay(profileId: Long, localDay: String): Int {
        lastDeleteProfileId = profileId
        lastDeleteLocalDay = localDay
        return deleteDayCount
    }
    override suspend fun getLatestLocalDay(profileId: Long): String? = null
    override fun getDailySummary(profileId: Long, day: String): Flow<DailySummaryTuple?> = flowOf(null)
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDayTuple>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int): Flow<List<WeeklyDayTuple>> = flowOf(lastNDays)
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = flowOf(availableDays)
    override suspend fun getAvailableDays(profileId: Long): List<String> = availableDays

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<MeasurementEntity> {
        requestedLocalDays.add(localDay)
        return measurementsByDay[localDay].orEmpty()
    }

    override suspend fun getMeasurementsForLocalDayRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): List<MeasurementEntity> {
        lastRangeStartDay = startDay
        lastRangeEndDay = endDay
        return rangeMeasurements.filter { it.localDay in startDay..endDay }
    }

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): Flow<List<DailySummaryTuple>> = flowOf(emptyList())

    override fun observeDaySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String
    ): Flow<List<MonthDaySummaryTuple>> = emptyFlow()
}

private class FakeSettingsStore : SettingsStore {
    override fun observeLowLightThresholdLux(): Flow<Int> = flowOf(300)
    override suspend fun setLowLightThresholdLux(lux: Int) = Unit
    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = flowOf(60)
    override suspend fun setNearworkDistanceThresholdCm(value: Int) = Unit
    override fun observeBreakGapSeconds(): Flow<Int> = flowOf(60)
    override suspend fun setBreakGapSeconds(value: Int) = Unit
    override fun observeMinSessionDurationSeconds(): Flow<Int> = flowOf(60)
    override suspend fun setMinSessionDurationSeconds(value: Int) = Unit
    override fun observeCloseDistanceThresholdCm(): Flow<Int> = flowOf(30)
    override suspend fun setCloseDistanceThresholdCm(value: Int) = Unit
    override fun observeExtremeCloseThresholdCm(): Flow<Int> = flowOf(20)
    override suspend fun setExtremeCloseThresholdCm(value: Int) = Unit
    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = flowOf(true)
    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) = Unit
    override fun observeAlsSpikeThresholdLux(): Flow<Double> = flowOf(300.0)
    override suspend fun setAlsSpikeThresholdLux(value: Double) = Unit
    override fun observeShowDebugOverlay(): Flow<Boolean> = flowOf(false)
    override suspend fun setShowDebugOverlay(enabled: Boolean) = Unit
    override fun observeLastDemoProfileId(): Flow<Long?> = flowOf(null)
    override suspend fun setLastDemoProfileId(profileId: Long?) = Unit
    override fun observeDailyReminderEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setDailyReminderEnabled(enabled: Boolean) = Unit
    override fun observeDailyReminderTimeLocal(): Flow<String> = flowOf(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL)
    override suspend fun setDailyReminderTimeLocal(value: String) = Unit
    override fun observePostImportNotificationEnabled(): Flow<Boolean> = flowOf(false)
    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) = Unit
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> =
        flowOf(DuplicateResolutionPolicy.KEEP_EXISTING)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}

private fun sampleMeasurement(localDay: String, timestampEpochMillis: Long): MeasurementEntity {
    return MeasurementEntity(
        id = 0L,
        profileId = 1L,
        sessionId = 1L,
        timestampEpochMillis = timestampEpochMillis,
        localDay = localDay,
        distanceCm = 50.0,
        lux = 100.0
    )
}







