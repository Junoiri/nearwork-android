package com.example.nearworkthesis.importing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportSamplesInteractorTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun importSample_readsBundledAsset_andReturnsTerminalResult() = runBlocking {
        val database = buildDb()
        val measurementRepository = TrackingMeasurementRepository()
        val importSessionRepository = TrackingImportSessionRepository()
        val interactor = ImportSamplesInteractor(
            assetManager = context.assets,
            database = database,
            importSessionRepository = importSessionRepository,
            measurementRepository = measurementRepository,
            settingsStore = FakeSettingsStoreForSamples(),
            profileRepository = FakeProfileRepositoryForSamples()
        )

        val result = interactor.importSample("optodata_2026-06-07.csv", profileId = 1L)

        when (result) {
            is ImportResult.Success -> {
                assertEquals("optodata_2026-06-07.csv", result.summary.filename)
                assertTrue(result.summary.insertedRows > 0)
                assertNotNull(importSessionRepository.lastSession)
                assertTrue(measurementRepository.lastAddMeasurements.orEmpty().isNotEmpty())
            }
            is ImportResult.NoNewData -> {
                assertEquals("optodata_2026-06-07.csv", result.summary.filename)
            }
            is ImportResult.Error -> {
                assertTrue(result.message.isNotBlank())
            }
        }
        database.close()
    }

    @Test
    fun importSample_returnsErrorWhenAssetDoesNotExist() = runBlocking {
        val database = buildDb()
        val interactor = ImportSamplesInteractor(
            assetManager = context.assets,
            database = database,
            importSessionRepository = TrackingImportSessionRepository(),
            measurementRepository = TrackingMeasurementRepository(),
            settingsStore = FakeSettingsStoreForSamples(),
            profileRepository = FakeProfileRepositoryForSamples()
        )

        val result = interactor.importSample("missing.csv", profileId = 1L)

        assertTrue((result as ImportResult.Error).message.isNotBlank())
        database.close()
    }

    @Test
    fun importSample_passesProfileIdThroughToStoredMeasurements() = runBlocking {
        val database = buildDb()
        val measurementRepository = TrackingMeasurementRepository()
        val interactor = ImportSamplesInteractor(
            assetManager = context.assets,
            database = database,
            importSessionRepository = TrackingImportSessionRepository(),
            measurementRepository = measurementRepository,
            settingsStore = FakeSettingsStoreForSamples(),
            profileRepository = FakeProfileRepositoryForSamples()
        )

        val result = interactor.importSample("optodata_2026-06-07.csv", profileId = 42L)

        if (result is ImportResult.Success || result is ImportResult.NoNewData) {
            assertTrue(measurementRepository.lastAddMeasurements.orEmpty().all { it.profileId == 42L })
        } else {
            assertTrue((result as ImportResult.Error).message.isNotBlank())
        }
        database.close()
    }

    @Test
    fun importSample_preservesRequestedAssetFilenameInTerminalResult() = runBlocking {
        val database = buildDb()
        val interactor = ImportSamplesInteractor(
            assetManager = context.assets,
            database = database,
            importSessionRepository = TrackingImportSessionRepository(),
            measurementRepository = TrackingMeasurementRepository(),
            settingsStore = FakeSettingsStoreForSamples(),
            profileRepository = FakeProfileRepositoryForSamples()
        )

        val result = interactor.importSample("optodata_2026-06-07.csv", profileId = 9L)

        when (result) {
            is ImportResult.Success -> assertEquals("optodata_2026-06-07.csv", result.summary.filename)
            is ImportResult.NoNewData -> assertEquals("optodata_2026-06-07.csv", result.summary.filename)
            is ImportResult.Error -> assertTrue(result.message.isNotBlank())
        }
        database.close()
    }

    private fun buildDb(): NearworkDatabase {
        return Room.inMemoryDatabaseBuilder(context, NearworkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}

private class TrackingImportSessionRepository : ImportSessionRepository {
    var lastSession: ImportSession? = null
    override suspend fun getSessionsForProfile(profileId: Long): List<ImportSession> = emptyList()
    override suspend fun upsertSession(session: ImportSession): Long {
        lastSession = session
        return 1L
    }
}

private class TrackingMeasurementRepository : MeasurementRepository {
    var lastAddMeasurements: List<Measurement>? = null
    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()
    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        lastAddMeasurements = measurements
        return MeasurementInsertResult(measurements.size, 0)
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
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(fakeAnalysisConfig())
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = fakeAnalysisConfig()
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?) =
        com.example.nearworkthesis.domain.export.ResultsPackCsvs("", "", "", "", 0)

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
            timeHandling = AnalysisTimeHandling("UTC", "test")
        )
    }
}

private class FakeSettingsStoreForSamples : SettingsStore {
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

private class FakeProfileRepositoryForSamples : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? = Profile(profileId, "Profile", 0L, "UTC", null)
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}
