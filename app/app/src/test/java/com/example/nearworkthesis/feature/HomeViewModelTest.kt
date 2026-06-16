package com.example.nearworkthesis.feature

import android.net.Uri
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DiopterHoursCalculator
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.analysis.NearworkSession
import com.example.nearworkthesis.domain.analysis.ValidationSummary
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.importing.howfar.HowfarDeviceInfo
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageState
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun noActiveProfile_orNoSummary_yieldsNoData() = runTest {
        val profileRepository = HomeProfileRepository(emptyList())
        val measurementRepository = HomeMeasurementRepository()
        val activeStore = HomeActiveProfileStore(null)
        val storageRepository = HomeHowfarStorageRepository()
        val viewModel = HomeViewModel(
            appContext = RuntimeEnvironment.getApplication(),
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            diopterHoursCalculator = DiopterHoursCalculator(),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            howfarStorageRepository = storageRepository
        )

        advanceUntilIdle()
        assertEquals(HomeUiState.NoData, viewModel.uiState.value)

        profileRepository.profiles.value = listOf(Profile(1L, "Alpha", 10L, "UTC", null))
        activeStore.activeId.value = 1L
        advanceUntilIdle()
        assertEquals(HomeUiState.NoData, viewModel.uiState.value)
    }

    @Test
    fun readyProfile_andHowfarState_updatesUi() = runTest {
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        val profileRepository = HomeProfileRepository(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val measurementRepository = HomeMeasurementRepository().apply {
            summary = DailySummary(
                day = today,
                sampleCount = 2,
                avgDistanceCm = 35.0,
                minDistanceCm = 30.0,
                maxDistanceCm = 40.0,
                avgLux = 150.0,
                minLux = 100.0,
                maxLux = 200.0,
                diopterHoursTotal = 0.0,
                lowLightMinutes = 0,
                firstTimestampIso = null,
                lastTimestampIso = null
            )
            measurements = listOf(
                Measurement(1, 1L, 10L, 1_000L, today, 35.0, 100.0),
                Measurement(2, 1L, 10L, 2_000L, today, 30.0, 150.0)
            )
            analysisDay = DataAnalysisDay(
                day = today,
                rawSamples = listOf(
                    NearworkSample(1_000L, 35.0, 100.0),
                    NearworkSample(2_000L, 30.0, 150.0)
                ),
                processedSamples = listOf(
                    NearworkSample(1_000L, 35.0, 100.0),
                    NearworkSample(2_000L, 30.0, 150.0)
                ),
                summary = ValidationSummary(2, 2, 0, 0, 0, 0, 32.5, 32.5, 0.0, 0)
            )
        }
        val activeStore = HomeActiveProfileStore(1L)
        val storageRepository = HomeHowfarStorageRepository()
        val viewModel = HomeViewModel(
            appContext = RuntimeEnvironment.getApplication(),
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            diopterHoursCalculator = DiopterHoursCalculator(),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            howfarStorageRepository = storageRepository
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForHomeState { viewModel.uiState.value is HomeUiState.Data }
        val data = viewModel.uiState.value as HomeUiState.Data
        assertEquals("Alpha", data.activeProfileName)
        assertTrue(data.diopterHours > 0.0)

        storageRepository.emit(
            HowfarStorageState.Ready(
                HowfarDeviceInfo(Uri.parse("content://howfar/optodata"), "OPTODATA")
            )
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()
        assertEquals(HomeHowfarUiState.Ready("OPTODATA"), viewModel.howfarUiState.value)

        storageRepository.emit(HowfarStorageState.Error("Disconnected"))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()
        assertEquals(HomeHowfarUiState.Error("Disconnected"), viewModel.howfarUiState.value)

        viewModel.refreshHowfarAvailability()
        assertEquals(1, storageRepository.refreshCalls)
        collectionJob.cancel()
    }

    @Test
    fun missingActiveProfile_fallsBackToFirstProfile_andFactoryCreatesViewModel() = runTest {
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        val profileRepository = HomeProfileRepository(
            listOf(
                Profile(10L, "Fallback", 10L, "UTC", null),
                Profile(11L, "Other", 11L, "UTC", null)
            )
        )
        val measurementRepository = HomeMeasurementRepository().apply {
            summary = DailySummary(
                day = today,
                sampleCount = 1,
                avgDistanceCm = 40.0,
                minDistanceCm = 40.0,
                maxDistanceCm = 40.0,
                avgLux = 200.0,
                minLux = 200.0,
                maxLux = 200.0,
                diopterHoursTotal = 0.0,
                lowLightMinutes = 0,
                firstTimestampIso = null,
                lastTimestampIso = null
            )
            measurements = listOf(Measurement(1, 10L, 10L, 1_000L, today, 40.0, 200.0))
            analysisDay = DataAnalysisDay(
                day = today,
                rawSamples = listOf(NearworkSample(1_000L, 40.0, 200.0)),
                processedSamples = listOf(NearworkSample(1_000L, 40.0, 200.0)),
                summary = ValidationSummary(1, 1, 0, 0, 0, 0, 40.0, 40.0, 0.0, 0)
            )
        }
        val activeStore = HomeActiveProfileStore(999L)
        val storageRepository = HomeHowfarStorageRepository()
        val factory = HomeViewModel.factory(
            appContext = RuntimeEnvironment.getApplication(),
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            diopterHoursCalculator = DiopterHoursCalculator(),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            howfarStorageRepository = storageRepository
        )
        val viewModel = factory.create(HomeViewModel::class.java)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForHomeState { viewModel.uiState.value is HomeUiState.Data }
        val data = viewModel.uiState.value as HomeUiState.Data
        assertEquals("Fallback", data.activeProfileName)
        collectionJob.cancel()
    }

    @Test
    fun summaryWithoutMeasurements_keepsNoDataState() = runTest {
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        val profileRepository = HomeProfileRepository(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val measurementRepository = HomeMeasurementRepository().apply {
            summary = DailySummary(
                day = today,
                sampleCount = 1,
                avgDistanceCm = 35.0,
                minDistanceCm = 35.0,
                maxDistanceCm = 35.0,
                avgLux = 100.0,
                minLux = 100.0,
                maxLux = 100.0,
                diopterHoursTotal = 0.0,
                lowLightMinutes = 0,
                firstTimestampIso = null,
                lastTimestampIso = null
            )
        }
        val viewModel = HomeViewModel(
            appContext = RuntimeEnvironment.getApplication(),
            measurementRepository = measurementRepository,
            profileRepository = profileRepository,
            activeProfileStore = HomeActiveProfileStore(1L),
            diopterHoursCalculator = DiopterHoursCalculator(),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            howfarStorageRepository = HomeHowfarStorageRepository()
        )

        advanceUntilIdle()
        assertEquals(HomeUiState.NoData, viewModel.uiState.value)
    }
}

private fun waitForHomeState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for home state", predicate())
}

private class HomeProfileRepository(initialProfiles: List<Profile>) : ProfileRepository {
    val profiles = MutableStateFlow(initialProfiles)
    override suspend fun getProfiles(): List<Profile> = profiles.value
    override fun observeProfiles(): Flow<List<Profile>> = profiles
    override suspend fun getProfile(profileId: Long): Profile? = profiles.value.firstOrNull { it.id == profileId }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private class HomeActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) { activeId.value = id }
}

private class HomeHowfarStorageRepository : HowfarStorageRepository {
    private val mutableState = MutableStateFlow<HowfarStorageState>(HowfarStorageState.Disconnected)
    override val state: MutableStateFlow<HowfarStorageState> = mutableState
    var refreshCalls = 0
    override fun refresh() { refreshCalls += 1 }
    override fun setDeviceTreeUri(uri: Uri?) = Unit
    override fun deviceTreeUri(): Uri? = null
    override suspend fun readDataUf2(examIdentifier: String?): ByteArray = byteArrayOf()
    override suspend fun readConfigUf2(): ByteArray? = null
    override suspend fun writeConfigUf2(bytes: ByteArray) = Unit
    override fun close() = Unit
    fun emit(state: HowfarStorageState) { mutableState.value = state }
}

private class HomeMeasurementRepository : MeasurementRepository {
    private val analysisConfig = AnalysisConfig(
        thresholds = AnalysisThresholds(300, 60, 60, 60, 30, 20),
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
    var summary: DailySummary? = null
    var measurements: List<Measurement> = emptyList()
    var analysisDay: DataAnalysisDay = DataAnalysisDay(
        day = "1970-01-01",
        rawSamples = emptyList(),
        processedSamples = emptyList(),
        summary = ValidationSummary(0, 0, 0, 0, 0, 0, null, null, null, 0)
    )

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = measurements
    override suspend fun addMeasurements(measurements: List<Measurement>, duplicateResolutionPolicy: DuplicateResolutionPolicy): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }
    override suspend fun getLatestDay(profileId: Long): String? = measurements.lastOrNull()?.localDay
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = measurements
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(summary)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(
            DailySessionInsights(
                listOf(NearworkSession(1_000L, 2_000L, 1L, 32.5, 30.0, 0.01, 0L)),
                null,
                emptyList()
            )
        )
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay = analysisDay
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = flowOf(measurements.map { it.localDay }.distinct())
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(measurements.size)
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): ResultsPackCsvs =
        ResultsPackCsvs("", "", "", "", 0)
}
