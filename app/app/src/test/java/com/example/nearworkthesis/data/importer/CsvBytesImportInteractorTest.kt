// Tests the CSV import pipeline (dedupe, gaps, and basic stats) without a database.
package com.example.nearworkthesis.data.importer

import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.importing.ImportTransactionRunner

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.ImportSessionRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.SettingsStore
import com.example.nearworkthesis.settings.SettingsDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class CsvBytesImportInteractorTest {

    @Test
    fun duplicateTimestamps_areCountedAndNotInsertedTwice() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T07:00:00,55,110") // duplicate timestamp
            appendLine("2025-12-16T11:00:00,60,120")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "dup.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(3, summary.totalRows)
        assertEquals(2, summary.insertedRows)
        assertEquals(0, summary.rejectedRows)
        assertEquals(1, summary.duplicatesEncounteredCount)
        assertEquals(1, summary.duplicatesKeptCount)
        assertNotNull(measurementRepo.lastAddMeasurements)
        assertEquals(2, measurementRepo.lastAddMeasurements!!.size)
        assertEquals(2, measurementRepo.lastAddMeasurements!!.map { it.timestampEpochMillis }.distinct().size)
    }

    @Test
    fun duplicatePolicy_keepExisting_keepsStoredMeasurement() = runBlocking {
        val duplicateTimestamp = epochMillis("2025-12-16T07:00:00")
        val measurementRepo = FakeMeasurementRepository().apply {
            seedMeasurement(
                Measurement(
                    id = 1L,
                    profileId = 1L,
                    sessionId = 10L,
                    timestampEpochMillis = duplicateTimestamp,
                    localDay = "2025-12-16",
                    distanceCm = 45.0,
                    lux = 90.0
                )
            )
        }
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(policy = DuplicateResolutionPolicy.KEEP_EXISTING),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,55,110") // duplicate timestamp
            appendLine("2025-12-16T11:00:00,60,120")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "keep.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(1, summary.insertedRows)
        assertEquals(1, summary.duplicatesEncounteredCount)
        assertEquals(1, summary.duplicatesKeptCount)
        assertEquals(0, summary.duplicatesReplacedCount)
        val stored = measurementRepo.measurementForTimestamp(duplicateTimestamp)
        assertNotNull(stored)
        assertEquals(45.0, stored?.distanceCm)
    }

    @Test
    fun duplicatePolicy_replaceWithNew_updatesStoredMeasurement() = runBlocking {
        val duplicateTimestamp = epochMillis("2025-12-16T07:00:00")
        val measurementRepo = FakeMeasurementRepository().apply {
            seedMeasurement(
                Measurement(
                    id = 1L,
                    profileId = 1L,
                    sessionId = 10L,
                    timestampEpochMillis = duplicateTimestamp,
                    localDay = "2025-12-16",
                    distanceCm = 45.0,
                    lux = 90.0
                )
            )
        }
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(policy = DuplicateResolutionPolicy.REPLACE_WITH_NEW),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,55,110") // duplicate timestamp
            appendLine("2025-12-16T11:00:00,60,120")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "replace.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(1, summary.insertedRows)
        assertEquals(1, summary.duplicatesEncounteredCount)
        assertEquals(0, summary.duplicatesKeptCount)
        assertEquals(1, summary.duplicatesReplacedCount)
        val stored = measurementRepo.measurementForTimestamp(duplicateTimestamp)
        assertNotNull(stored)
        assertEquals(55.0, stored?.distanceCm)
    }

    @Test
    fun importSession_persistsCoreMetadata() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(policy = DuplicateResolutionPolicy.REPLACE_WITH_NEW),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T11:00:00,50,100")
        }

        interactor.importCsvBytes(
            profileId = 1L,
            filename = "snapshot.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val storedSession = importSessionRepo.lastSession
        assertNotNull(storedSession)
        assertEquals("snapshot.csv", storedSession?.filename)
        assertEquals("ASSET", storedSession?.source)
        assertTrue(storedSession?.note.orEmpty().contains("preprocessing_output_count="))
    }

    @Test
    fun gapDetection_countsGapAndMaxGapDuration_forQualifiedImport() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T07:00:01,50,100")
            appendLine("2025-12-16T07:00:21,50,100") // 20s gap
            appendLine("2025-12-16T11:00:21,50,100")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "gap.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(4, summary.totalRows)
        assertEquals(4, summary.insertedRows)
        assertEquals(1, summary.gapCount)
        assertNotNull(summary.largestGapDurationMillis)
        assertTrue(summary.largestGapDurationMillis!! in 14_399_000L..14_401_000L)
    }

    @Test
    fun importRejectsRecordingsShorterThanMinimumDuration() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T10:59:59,50,100")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "short.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val error = result as ImportResult.Error
        assertTrue(error.message.contains("at least 4 hours"))
        assertNull(measurementRepo.lastAddMeasurements)
        assertNull(importSessionRepo.lastSession)
    }

    @Test
    fun importAcceptsRecordingsAtMinimumDurationBoundary() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T07:00:00,50,100")
            appendLine("2025-12-16T11:00:00,50,100")
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "boundary.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(2, summary.insertedRows)
    }

    @Test
    fun rejectionReasonCounts_areReportedDeterministically() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("invalid-ts,50,100") // invalid timestamp
            appendLine("2025-12-16T07:00:00,999,100") // invalid distance
            appendLine("2025-12-16T07:00:01,50,-1") // invalid lux
            appendLine("2025-12-16T07:00:02,50,100") // valid
            appendLine("2025-12-16T11:00:02,50,100") // valid
        }

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "rejections.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val summary = (result as ImportResult.Success).summary
        assertEquals(5, summary.totalRows)
        assertEquals(3, summary.rejectedRows)
        assertEquals(1, summary.invalidTimestampCount)
        assertEquals(1, summary.invalidDistanceCount)
        assertEquals(1, summary.invalidLuxCount)
        assertEquals(2, summary.insertedRows)
    }

    @Test
    fun importComputesLocalDay_usingProfileTimezone() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val importSessionRepo = FakeImportSessionRepository()
        val profileRepository = FakeProfileRepository(timezoneId = "Europe/Warsaw")
        val interactor = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepo,
            measurementRepository = measurementRepo,
            settingsStore = FakeSettingsStore(),
            profileRepository = profileRepository
        )

        val csv = buildString {
            appendLine("timestamp,distance_cm,illumination_lux")
            appendLine("2025-12-16T23:30:00,50,100")
            appendLine("2025-12-17T03:30:00,50,100")
        }

        interactor.importCsvBytes(
            profileId = 1L,
            filename = "tz.csv",
            sourceType = ImportSourceType.ASSET,
            bytes = csv.toByteArray(Charsets.UTF_8)
        )

        val measurement = measurementRepo.lastAddMeasurements?.firstOrNull()
        assertNotNull(measurement)
        assertEquals("2025-12-17", measurement?.localDay)
    }
}

private class ImmediateTransactionRunner : ImportTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}

private class FakeImportSessionRepository : ImportSessionRepository {
    var lastSession: com.example.nearworkthesis.domain.model.ImportSession? = null
    override suspend fun getSessionsForProfile(profileId: Long) = emptyList<com.example.nearworkthesis.domain.model.ImportSession>()
    override suspend fun upsertSession(session: com.example.nearworkthesis.domain.model.ImportSession): Long {
        lastSession = session
        return 1L
    }
}

private class FakeMeasurementRepository : MeasurementRepository {
    private val stored = mutableMapOf<Long, Measurement>()
    var lastAddMeasurements: List<Measurement>? = null

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        lastAddMeasurements = measurements
        var inserted = 0
        var replaced = 0
        for (m in measurements) {
            val existing = stored[m.timestampEpochMillis]
            if (existing == null) {
                stored[m.timestampEpochMillis] = m
                inserted += 1
            } else if (duplicateResolutionPolicy == DuplicateResolutionPolicy.REPLACE_WITH_NEW) {
                stored[m.timestampEpochMillis] = m
                replaced += 1
            }
        }
        return MeasurementInsertResult(insertedCount = inserted, replacedCount = replaced)
    }

    override suspend fun getLatestDay(profileId: Long): String? = null

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()

    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)

    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(sessions = emptyList(), longestSession = null, flaggedSessions = emptyList()))

    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw NotImplementedError("Not needed for tests")
    }

    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()

    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<WeeklyDaySummary>> = emptyFlow()

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = emptyFlow()

    override fun observeMeasurementCount(profileId: Long): Flow<Int> = emptyFlow()

    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(fakeAnalysisConfig())

    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = fakeAnalysisConfig()

    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0

    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""

    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""

    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""

    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""

    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): com.example.nearworkthesis.domain.export.ResultsPackCsvs {
        return com.example.nearworkthesis.domain.export.ResultsPackCsvs(
            dailyResultsCsv = "",
            sessionsResultsCsv = "",
            importQualityCsv = "",
            manifestJson = "",
            daysWithSamples = 0
        )
    }

    fun seedMeasurement(measurement: Measurement) {
        stored[measurement.timestampEpochMillis] = measurement
    }

    fun measurementForTimestamp(timestampEpochMillis: Long): Measurement? {
        return stored[timestampEpochMillis]
    }

    private fun fakeAnalysisConfig(): AnalysisConfig {
        return AnalysisConfig(
            thresholds = AnalysisThresholds(
                lowLightThresholdLux = SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX,
                nearworkDistanceThresholdCm = SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM,
                breakGapSeconds = SettingsDefaults.BREAK_GAP_SECONDS,
                minSessionDurationSeconds = SettingsDefaults.MIN_SESSION_DURATION_SECONDS,
                closeDistanceThresholdCm = SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM,
                extremeCloseThresholdCm = SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM
            ),
            pipeline = AnalysisPipelineConfig(
                smoothingWindowSize = 5,
                dedupeRule = "same timestamp keep last",
                distanceRangeMinCm = 10.0,
                distanceRangeMaxCm = 200.0,
                luxRangeMin = 0.0,
                luxRangeMax = 50_000.0,
                gapThresholdSeconds = 60
            ),
            timeHandling = AnalysisTimeHandling(
                timezoneId = "UTC",
                statement = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
            )
        )
    }
}

private class FakeSettingsStore(
    private val policy: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING
) : SettingsStore {
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
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = flowOf(policy)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}

private class FakeProfileRepository(
    private val timezoneId: String = "UTC"
) : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? {
        return Profile(id = profileId, name = "Profile", createdAtEpochMillis = 0L, timezoneId = timezoneId, dateOfBirth = null)
    }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private fun epochMillis(isoLocal: String): Long {
    return java.time.LocalDateTime.parse(isoLocal)
        .toInstant(java.time.ZoneOffset.UTC)
        .toEpochMilli()
}







