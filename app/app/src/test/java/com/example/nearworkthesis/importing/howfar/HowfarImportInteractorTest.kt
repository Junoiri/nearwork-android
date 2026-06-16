package com.example.nearworkthesis.importing.howfar

import android.net.Uri
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
import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.importing.ImportTransactionRunner
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HowfarImportInteractorTest {

    @Test
    fun importFromUf2File_returnsErrorWhenNoActiveProfileIsSelected() = runBlocking {
        val interactor = buildInteractor(activeProfileId = null)

        val result = interactor.importFromUf2File(
            filename = "manual.uf2",
            uf2Bytes = readResource("howfar/OPTODATA.UF2")
        )

        assertEquals("No active profile selected.", (result as ImportResult.Error).message)
    }

    @Test
    fun importFromDevice_returnsErrorWhenNoActiveProfileIsSelected() = runBlocking {
        val interactor = buildInteractor(activeProfileId = null)

        val result = interactor.importFromDevice()

        assertEquals("No active profile selected.", (result as ImportResult.Error).message)
    }

    @Test
    fun importFromUf2File_returnsParseError_andSkipsArchiving() = runBlocking {
        val archive = FakeHowfarUf2Archive()
        val interactor = buildInteractor(
            activeProfileId = 1L,
            archive = archive
        )

        val result = interactor.importFromUf2File(
            filename = "broken.uf2",
            uf2Bytes = ByteArray(10)
        )

        assertTrue((result as ImportResult.Error).message.isNotBlank())
        assertNull(archive.lastSaved)
    }

    @Test
    fun importFromDevice_returnsReadError_whenStorageAccessFails() = runBlocking {
        val interactor = buildInteractor(
            activeProfileId = 3L,
            storageRepository = FakeHowfarStorageRepository(
                readDataError = IllegalStateException("storage offline")
            )
        )

        val result = interactor.importFromDevice()

        assertEquals("storage offline", (result as ImportResult.Error).message)
    }

    @Test
    fun importFromDevice_passesExamIdFromConfigIntoDataRead() = runBlocking {
        val configSettings = HowfarSettings.defaults().copy(examinationIdentifier = "EXAM1234")
        val configUf2 = HowfarUf2.convertToUf2(HowfarSettingsCodec.toByteArray(configSettings))
        val storage = FakeHowfarStorageRepository(
            configUf2 = configUf2,
            dataUf2 = ByteArray(10)
        )
        val interactor = buildInteractor(
            activeProfileId = 3L,
            storageRepository = storage
        )

        val result = interactor.importFromDevice(anchorMillis = 1_700_000_000_000L)

        result as ImportResult.Error
        assertEquals("EXAM1234", storage.lastRequestedExamIdentifier)
    }

    @Test
    fun importFromUf2File_success_archivesBytesAndUsesManualFilename() = runBlocking {
        val archive = FakeHowfarUf2Archive()
        val importSessionRepository = FakeImportSessionRepository()
        val interactor = buildInteractor(
            activeProfileId = 7L,
            archive = archive,
            importSessionRepository = importSessionRepository
        )
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")

        val result = interactor.importFromUf2File(
            filename = "manual.uf2",
            uf2Bytes = uf2Bytes,
            anchorMillis = 1_700_000_000_000L
        )

        assertTrue(result !is ImportResult.Error || (result as ImportResult.Error).message.isNotBlank())
        assertNotNull(archive.lastSaved)
        assertEquals(7L, archive.lastSaved?.profileId)
        assertEquals("manual.uf2", archive.lastSaved?.filename)
        assertArrayEquals(uf2Bytes, archive.lastSaved?.bytes)
    }

    @Test
    fun importFromDevice_success_usesReadyDisplayNameForArchivedFilename() = runBlocking {
        val uf2Bytes = readResource("howfar/OPTODATA.UF2")
        val archive = FakeHowfarUf2Archive()
        val storage = FakeHowfarStorageRepository(
            initialState = HowfarStorageState.Ready(
                HowfarDeviceInfo(Uri.parse("content://howfar/device"), "Drive E")
            ),
            dataUf2 = uf2Bytes
        )
        val interactor = buildInteractor(
            activeProfileId = 5L,
            storageRepository = storage,
            archive = archive
        )

        val result = interactor.importFromDevice(anchorMillis = 1_700_000_000_000L)

        assertTrue(result !is ImportResult.Error || (result as ImportResult.Error).message.isNotBlank())
        assertEquals("Drive E:optodata.uf2", archive.lastSaved?.filename)
        assertArrayEquals(uf2Bytes, archive.lastSaved?.bytes)
        assertEquals(null, storage.lastRequestedExamIdentifier)
    }

    private fun buildInteractor(
        activeProfileId: Long?,
        storageRepository: FakeHowfarStorageRepository = FakeHowfarStorageRepository(),
        archive: FakeHowfarUf2Archive = FakeHowfarUf2Archive(),
        measurementRepository: FakeMeasurementRepository = FakeMeasurementRepository(),
        importSessionRepository: FakeImportSessionRepository = FakeImportSessionRepository()
    ): HowfarImportInteractor {
        val csvImporter = CsvBytesImportInteractor(
            transactionRunner = ImmediateTransactionRunner(),
            importSessionRepository = importSessionRepository,
            measurementRepository = measurementRepository,
            settingsStore = FakeSettingsStore(),
            profileRepository = FakeProfileRepository()
        )
        return HowfarImportInteractor(
            storageRepository = storageRepository,
            howfarUf2Archive = archive,
            dataParser = HowfarDataParser(),
            activeProfileStore = FakeActiveProfileStore(activeProfileId),
            csvImporter = csvImporter
        )
    }

    private fun readResource(path: String): ByteArray {
        val stream: InputStream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Test resource not found: $path")
        return stream.use { it.readBytes() }
    }

}

private class FakeHowfarStorageRepository(
    initialState: HowfarStorageState = HowfarStorageState.Disconnected,
    private val configUf2: ByteArray? = null,
    private val dataUf2: ByteArray = ByteArray(0),
    private val readDataError: Throwable? = null
) : HowfarStorageRepository {
    private val backingState = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    override val state: StateFlow<HowfarStorageState> = backingState
    var lastRequestedExamIdentifier: String? = null

    override fun refresh() = Unit
    override fun setDeviceTreeUri(uri: Uri?) = Unit
    override fun deviceTreeUri(): Uri? = null

    override suspend fun readDataUf2(examIdentifier: String?): ByteArray {
        lastRequestedExamIdentifier = examIdentifier
        readDataError?.let { throw it }
        return dataUf2
    }

    override suspend fun readConfigUf2(): ByteArray? = configUf2
    override suspend fun writeConfigUf2(bytes: ByteArray) = Unit
    override fun close() = Unit
}

private class FakeHowfarUf2Archive : HowfarUf2Archive {
    data class Saved(val profileId: Long, val filename: String, val bytes: ByteArray)
    var lastSaved: Saved? = null

    override suspend fun saveLatest(profileId: Long, filename: String, bytes: ByteArray) {
        lastSaved = Saved(profileId, filename, bytes)
    }

    override suspend fun loadLatest(profileId: Long): HowfarUf2Snapshot? = null
}

private class FakeActiveProfileStore(
    private val activeProfileId: Long?
) : ActiveProfileStore {
    override fun observeActiveProfileId(): Flow<Long?> = flowOf(activeProfileId)
    override suspend fun setActiveProfileId(id: Long) = Unit
}

private class ImmediateTransactionRunner : ImportTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}

private class FakeImportSessionRepository : ImportSessionRepository {
    var lastSession: ImportSession? = null

    override suspend fun getSessionsForProfile(profileId: Long): List<ImportSession> = emptyList()

    override suspend fun upsertSession(session: ImportSession): Long {
        lastSession = session
        return 1L
    }
}

private class FakeMeasurementRepository : MeasurementRepository {
    var lastAddMeasurements: List<Measurement>? = null

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        lastAddMeasurements = measurements
        return MeasurementInsertResult(
            insertedCount = measurements.size,
            replacedCount = 0
        )
    }

    override suspend fun getLatestDay(profileId: Long): String? = null
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?) =
        throw NotImplementedError()
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
    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ) = com.example.nearworkthesis.domain.export.ResultsPackCsvs("", "", "", "", 0)

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
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = flowOf(DuplicateResolutionPolicy.KEEP_EXISTING)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}

private class FakeProfileRepository : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? =
        Profile(profileId, "Profile", 0L, "UTC", null)
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}
