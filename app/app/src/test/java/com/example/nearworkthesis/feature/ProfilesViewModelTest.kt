package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
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
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun observeProfiles_buildsListItems_andTracksActiveProfile() = runTest {
        val profiles = MutableStateFlow(
            listOf(
                Profile(2L, "Beta", 20L, "UTC", null),
                Profile(1L, "Alpha", 10L, "UTC", null)
            )
        )
        val profileRepository = MutableProfilesRepository(profiles).apply {
            sampleCounts[1L] = 8
            sampleCounts[2L] = 3
        }
        val measurementRepository = ProfilesMeasurementRepository(
            availableDays = mapOf(
                1L to listOf("2026-06-14", "2026-06-15"),
                2L to listOf("2026-06-15")
            )
        )
        val activeStore = MutableActiveProfileStore(2L)
        val viewModel = ProfilesViewModel(profileRepository, measurementRepository, activeStore)

        waitForProfilesState { viewModel.uiState.value is ProfilesUiState.Data }

        val state = viewModel.uiState.value as ProfilesUiState.Data
        assertEquals(listOf("Alpha", "Beta"), state.profiles.map { it.name })
        assertEquals(listOf(2, 1), state.profiles.map { it.dayCount })
        assertEquals(listOf(8, 3), state.profiles.map { it.sampleCount })
        assertEquals(listOf(false, true), state.profiles.map { it.isActive })
    }

    @Test
    fun addProfile_andSetActive_forwardToStores() = runTest {
        val profiles = MutableStateFlow(emptyList<Profile>())
        val profileRepository = MutableProfilesRepository(profiles).apply {
            insertResult = 7L
        }
        val measurementRepository = ProfilesMeasurementRepository()
        val activeStore = MutableActiveProfileStore(null)
        val viewModel = ProfilesViewModel(profileRepository, measurementRepository, activeStore)

        viewModel.addProfile("  New Profile  ", " 2001-02-03 ")
        waitForProfilesStore { activeStore.activeId.value == 7L }

        assertEquals("New Profile", profileRepository.lastInsertedName)
        assertEquals("2001-02-03", profileRepository.lastInsertedDob)
        assertEquals(7L, activeStore.activeId.value)

        viewModel.setActive(3L)
        waitForProfilesStore { activeStore.activeId.value == 3L }
        assertEquals(3L, activeStore.activeId.value)
    }

    @Test
    fun blankNameIsIgnored_and_repositoryFailures_surfaceAsError() = runTest {
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = MutableProfilesRepository(profiles).apply {
            renameFailure = IllegalStateException("Rename failed")
            deleteFailure = IllegalStateException("Delete failed")
        }
        val measurementRepository = ProfilesMeasurementRepository()
        val activeStore = MutableActiveProfileStore(1L)
        val viewModel = ProfilesViewModel(profileRepository, measurementRepository, activeStore)

        viewModel.addProfile("   ", null)
        viewModel.renameProfile(1L, "   ")
        Thread.sleep(50L)
        assertEquals(null, profileRepository.lastInsertedName)
        assertEquals(null, profileRepository.lastRenamedName)

        viewModel.renameProfile(1L, " Renamed ")
        waitForProfilesState { viewModel.uiState.value is ProfilesUiState.Error }
        assertEquals("Rename failed", (viewModel.uiState.value as ProfilesUiState.Error).message)

        viewModel.deleteProfile(1L)
        waitForProfilesState { (viewModel.uiState.value as? ProfilesUiState.Error)?.message == "Delete failed" }
        assertEquals("Delete failed", (viewModel.uiState.value as ProfilesUiState.Error).message)
    }

    @Test
    fun renameAndDeleteSuccess_forwardTrimmedValues_andFactoryCreatesViewModel() = runTest {
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = MutableProfilesRepository(profiles)
        val measurementRepository = ProfilesMeasurementRepository()
        val activeStore = MutableActiveProfileStore(1L)
        val factory = ProfilesViewModel.factory(profileRepository, measurementRepository, activeStore)
        val viewModel = factory.create(ProfilesViewModel::class.java)

        viewModel.renameProfile(1L, " Renamed ")
        advanceUntilIdle()
        assertEquals("Renamed", profileRepository.lastRenamedName)

        viewModel.deleteProfile(1L)
        advanceUntilIdle()
        assertEquals(1L, profileRepository.lastDeletedProfileId)
    }

    @Test
    fun addProfileFailure_surfacesErrorMessage() = runTest {
        val profileRepository = MutableProfilesRepository(MutableStateFlow(emptyList())).apply {
            insertFailure = IllegalStateException("Insert failed")
        }
        val viewModel = ProfilesViewModel(
            profileRepository = profileRepository,
            measurementRepository = ProfilesMeasurementRepository(),
            activeProfileStore = MutableActiveProfileStore(null)
        )

        advanceUntilIdle()
        viewModel.addProfile(" Name ", " ")
        advanceUntilIdle()
        assertEquals("Insert failed", (viewModel.uiState.value as ProfilesUiState.Error).message)
    }
}

private fun waitForProfilesState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for profiles state", predicate())
}

private fun waitForProfilesStore(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for profile store update", predicate())
}

private class MutableProfilesRepository(
    private val profilesFlow: MutableStateFlow<List<Profile>>
) : ProfileRepository {
    val sampleCounts = LinkedHashMap<Long, Int>()
    var insertResult: Long = 1L
    var lastInsertedName: String? = null
    var lastInsertedDob: String? = null
    var lastRenamedName: String? = null
    var lastDeletedProfileId: Long? = null
    var insertFailure: Throwable? = null
    var renameFailure: Throwable? = null
    var deleteFailure: Throwable? = null

    override suspend fun getProfiles(): List<Profile> = profilesFlow.value
    override fun observeProfiles(): Flow<List<Profile>> = profilesFlow
    override suspend fun getProfile(profileId: Long): Profile? = profilesFlow.value.firstOrNull { it.id == profileId }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long {
        insertFailure?.let { throw it }
        lastInsertedName = name
        lastInsertedDob = dateOfBirth
        return insertResult
    }
    override suspend fun renameProfile(profileId: Long, name: String) {
        renameFailure?.let { throw it }
        lastRenamedName = name
    }
    override suspend fun deleteProfile(profileId: Long) {
        deleteFailure?.let { throw it }
        lastDeletedProfileId = profileId
    }
    override suspend fun countMeasurements(profileId: Long): Int = sampleCounts[profileId] ?: 0
}

private class MutableActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) {
        activeId.value = id
    }
}

private class ProfilesMeasurementRepository(
    private val availableDays: Map<Long, List<String>> = emptyMap()
) : MeasurementRepository {
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

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()
    override suspend fun addMeasurements(measurements: List<Measurement>, duplicateResolutionPolicy: DuplicateResolutionPolicy): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }
    override suspend fun getLatestDay(profileId: Long): String? = availableDays[profileId]?.lastOrNull()
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw UnsupportedOperationException()
    }
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = flowOf(availableDays[profileId].orEmpty())
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)
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
