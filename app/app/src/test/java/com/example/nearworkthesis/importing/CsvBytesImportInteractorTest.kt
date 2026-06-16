package com.example.nearworkthesis.importing

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.ImportSession
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.ImportSessionRepository
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvBytesImportInteractorTest {

    @Test
    fun importCsvBytes_returnsErrorWhenCsvCannotBeParsed() = runBlocking {
        val interactor = buildInteractor()

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "broken.csv",
            sourceType = ImportSourceType.FILE,
            bytes = "timestamp,distance_cm\nbad".toByteArray()
        )

        assertEquals("Missing required CSV columns.", (result as ImportResult.Error).message)
    }

    @Test
    fun importCsvBytes_rejectsRecordingShorterThanMinimumDuration() = runBlocking {
        val interactor = buildInteractor()
        val bytes = """
            timestamp,distance_cm,illumination_lux
            2026-01-01 00:00:00,30,100
            2026-01-01 00:10:00,31,110
        """.trimIndent().toByteArray()

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "short.csv",
            sourceType = ImportSourceType.FILE,
            bytes = bytes
        )

        assertTrue(result is ImportResult.Error)
        assertTrue((result as ImportResult.Error).message.startsWith("Import rejected: recordings must span at least 4 hours."))
    }

    @Test
    fun importCsvBytes_returnsNoNewDataWhenRepositoryInsertsNothing() = runBlocking {
        val measurementRepository = CsvTrackingMeasurementRepository(insertedCount = 0, replacedCount = 0)
        val interactor = buildInteractor(measurementRepository = measurementRepository)
        val bytes = """
            timestamp,distance_cm,illumination_lux
            2026-01-01 00:00:00,30,100
            2026-01-01 04:00:00,31,110
        """.trimIndent().toByteArray()

        val result = interactor.importCsvBytes(
            profileId = 1L,
            filename = "valid.csv",
            sourceType = ImportSourceType.FILE,
            bytes = bytes
        )

        result as ImportResult.NoNewData
        assertEquals("valid.csv", result.summary.filename)
        assertEquals(0, result.summary.insertedRows)
    }

    @Test
    fun importParsedMeasurements_returnsNoNewDataForEmptyMeasurementSet() = runBlocking {
        val importSessionRepository = CsvTrackingImportSessionRepository()
        val interactor = buildInteractor(importSessionRepository = importSessionRepository)

        val result = interactor.importParsedMeasurements(
            profileId = 1L,
            filename = "empty.csv",
            sourceType = ImportSourceType.FILE,
            parseResult = buildParseResultFromMeasurements(emptyList())
        )

        result as ImportResult.NoNewData
        assertEquals("empty.csv", result.summary.filename)
        assertEquals(0, result.summary.insertedRows)
        assertNull(result.summary.firstTimestampEpochMillis)
        assertNull(result.summary.lastTimestampEpochMillis)
        assertEquals("UTC", result.summary.timezoneId)
        assertNull(importSessionRepository.lastSession?.firstTimestampEpochMillis)
        assertNull(importSessionRepository.lastSession?.lastTimestampEpochMillis)
    }

    @Test
    fun importParsedMeasurements_fallsBackToSystemTimezoneWhenProfileIsMissing() = runBlocking {
        val interactor = buildInteractor(
            profileRepository = CsvMissingProfileRepository()
        )

        val result = interactor.importParsedMeasurements(
            profileId = 1L,
            filename = "missing-profile.csv",
            sourceType = ImportSourceType.FILE,
            parseResult = buildParseResultFromMeasurements(emptyList())
        )

        result as ImportResult.NoNewData
        assertEquals(ZoneId.systemDefault().id, result.summary.timezoneId)
    }

    @Test
    fun importParsedMeasurements_fallsBackToSystemTimezoneWhenProfileTimezoneIsInvalid() = runBlocking {
        val interactor = buildInteractor(
            profileRepository = CsvFakeProfileRepository(timezoneId = "not/a-real-zone")
        )

        val result = interactor.importParsedMeasurements(
            profileId = 1L,
            filename = "timezone.csv",
            sourceType = ImportSourceType.FILE,
            parseResult = buildParseResultFromMeasurements(
                measurements = listOf(
                    ParsedMeasurement(1_767_225_600_000L, 30.0, 100.0),
                    ParsedMeasurement(1_767_240_000_000L, 31.0, 110.0)
                )
            )
        )

        result as ImportResult.Success
        assertEquals(ZoneId.systemDefault().id, result.summary.timezoneId)
    }

    @Test
    fun importParsedMeasurements_returnsFallbackErrorWhenRepositoryThrowsWithoutMessage() = runBlocking {
        val interactor = buildInteractor(
            measurementRepository = CsvThrowingMeasurementRepository()
        )

        val result = interactor.importParsedMeasurements(
            profileId = 1L,
            filename = "explode.csv",
            sourceType = ImportSourceType.FILE,
            parseResult = buildParseResultFromMeasurements(
                measurements = listOf(
                    ParsedMeasurement(1_767_225_600_000L, 30.0, 100.0),
                    ParsedMeasurement(1_767_240_000_000L, 31.0, 110.0)
                )
            )
        )

        result as ImportResult.Error
        assertEquals("Unable to import CSV.", result.message)
    }

    private fun buildInteractor(
        importSessionRepository: CsvTrackingImportSessionRepository = CsvTrackingImportSessionRepository(),
        measurementRepository: MeasurementRepository = CsvTrackingMeasurementRepository(insertedCount = 1, replacedCount = 0),
        settingsStore: SettingsStore = CsvFakeSettingsStore(),
        profileRepository: ProfileRepository = CsvFakeProfileRepository()
    ): CsvBytesImportInteractor {
        return CsvBytesImportInteractor(
            transactionRunner = CsvImmediateTransactionRunner(),
            importSessionRepository = importSessionRepository,
            measurementRepository = measurementRepository,
            settingsStore = settingsStore,
            profileRepository = profileRepository
        )
    }
}

private class CsvImmediateTransactionRunner : ImportTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}

private class CsvTrackingImportSessionRepository : ImportSessionRepository {
    var lastSession: ImportSession? = null
    override suspend fun getSessionsForProfile(profileId: Long): List<ImportSession> = emptyList()
    override suspend fun upsertSession(session: ImportSession): Long {
        lastSession = session
        return 1L
    }
}

private class CsvTrackingMeasurementRepository(
    private val insertedCount: Int,
    private val replacedCount: Int
) : MeasurementRepository {
    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()
    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult = MeasurementInsertResult(insertedCount, replacedCount)
    override suspend fun getLatestDay(profileId: Long): String? = null
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?) = throw NotImplementedError()
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = emptyFlow()
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = emptyFlow()
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(csvFakeAnalysisConfig())
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = csvFakeAnalysisConfig()
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?) =
        com.example.nearworkthesis.domain.export.ResultsPackCsvs("", "", "", "", 0)
}

private class CsvThrowingMeasurementRepository : MeasurementRepository {
    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()
    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        throw RuntimeException()
    }
    override suspend fun getLatestDay(profileId: Long): String? = null
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?) = throw NotImplementedError()
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = emptyFlow()
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = emptyFlow()
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(csvFakeAnalysisConfig())
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = csvFakeAnalysisConfig()
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?) =
        com.example.nearworkthesis.domain.export.ResultsPackCsvs("", "", "", "", 0)
}

private class CsvFakeSettingsStore : SettingsStore {
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
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = flowOf(DuplicateResolutionPolicy.KEEP_EXISTING)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}

private class CsvFakeProfileRepository(
    private val timezoneId: String = "UTC"
) : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? = Profile(profileId, "Profile", 0L, timezoneId, null)
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private class CsvMissingProfileRepository : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? = null
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private fun csvFakeAnalysisConfig(): AnalysisConfig {
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
        timeHandling = AnalysisTimeHandling("UTC", "test")
    )
}
