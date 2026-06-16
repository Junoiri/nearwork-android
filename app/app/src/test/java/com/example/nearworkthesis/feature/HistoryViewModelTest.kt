package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModelProvider
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun loadsData_andSupportsModeAndMonthNavigation() = runTest {
        val repository = HistoryMeasurementRepository()
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = HistoryProfileRepository(profiles)
        val activeStore = HistoryActiveProfileStore(1L)
        val viewModel = HistoryViewModel(repository, profileRepository, activeStore)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForHistoryState { viewModel.uiState.value is HistoryUiState.Data }
        val data = viewModel.uiState.value as HistoryUiState.Data
        assertEquals(HistoryViewMode.Calendar, data.mode)
        assertEquals(listOf("2026-06-15", "2026-06-14"), data.days.map { it.day })
        assertEquals(1L, data.activeProfileId)

        viewModel.setMode(HistoryViewMode.List)
        advanceUntilIdle()
        assertEquals(HistoryViewMode.List, (viewModel.uiState.value as HistoryUiState.Data).mode)

        val initialMonth = (viewModel.uiState.value as HistoryUiState.Data).calendar.month
        viewModel.goToPreviousMonth()
        advanceUntilIdle()
        assertEquals(initialMonth.minusMonths(1), (viewModel.uiState.value as HistoryUiState.Data).calendar.month)

        viewModel.goToNextMonth()
        advanceUntilIdle()
        assertEquals(initialMonth, (viewModel.uiState.value as HistoryUiState.Data).calendar.month)

        viewModel.goToLatestMonth()
        advanceUntilIdle()
        assertEquals(java.time.YearMonth.of(2026, 6), (viewModel.uiState.value as HistoryUiState.Data).calendar.month)
        collectionJob.cancel()
    }

    @Test
    fun deleteDay_emitsSuccessAndFailureEvents() = runTest {
        val repository = HistoryMeasurementRepository()
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = HistoryProfileRepository(profiles)
        val activeStore = HistoryActiveProfileStore(1L)
        val viewModel = HistoryViewModel(repository, profileRepository, activeStore)
        var event: HistoryUiEvent? = null
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { event = it }
        }

        advanceUntilIdle()
        repository.deleteResult = 1
        viewModel.deleteDay("2026-06-15")
        waitForHistoryEvent { event is HistoryUiEvent.DayDeleted }
        assertEquals("2026-06-15", (event as HistoryUiEvent.DayDeleted).localDay)

        event = null
        repository.deleteResult = 0
        viewModel.deleteDay("2026-06-14")
        waitForHistoryEvent { event is HistoryUiEvent.DeleteFailed }
        assertEquals("2026-06-14", (event as HistoryUiEvent.DeleteFailed).localDay)
        collectionJob.cancel()
    }

    @Test
    fun retry_andFactory_coverEmptyAndRefreshPaths() = runTest {
        val repository = HistoryMeasurementRepository().apply {
            availableDays.value = emptyList()
            dailySummaries.value = emptyList()
            monthSummaries.value = emptyList()
        }
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = HistoryProfileRepository(profiles)
        val activeStore = HistoryActiveProfileStore(1L)
        val viewModel = HistoryViewModel(repository, profileRepository, activeStore)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForHistoryState { viewModel.uiState.value is HistoryUiState.Empty }
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is HistoryUiState.Empty)

        val factory: ViewModelProvider.Factory = HistoryViewModel.factory(repository, profileRepository, activeStore)
        val created = factory.create(HistoryViewModel::class.java)
        assertEquals(HistoryViewModel::class.java, created::class.java)
        collectionJob.cancel()
    }

    @Test
    fun goToLatestMonth_withoutActiveProfile_orInvalidLatestDay_isNoOp() = runTest {
        val repository = HistoryMeasurementRepository().apply {
            latestDay = "not-a-day"
        }
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val profileRepository = HistoryProfileRepository(profiles)
        val activeStore = HistoryActiveProfileStore(null)
        val viewModel = HistoryViewModel(repository, profileRepository, activeStore)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        val before = viewModel.uiState.value
        viewModel.goToLatestMonth()
        advanceUntilIdle()
        assertEquals(before, viewModel.uiState.value)

        activeStore.setActiveProfileId(1L)
        waitForHistoryState { viewModel.uiState.value is HistoryUiState.Data }
        val afterActive = viewModel.uiState.value as HistoryUiState.Data
        val monthBefore = afterActive.calendar.month
        viewModel.goToLatestMonth()
        advanceUntilIdle()
        assertEquals(monthBefore, (viewModel.uiState.value as HistoryUiState.Data).calendar.month)
        collectionJob.cancel()
    }

    @Test
    fun repositoryFailure_surfacesErrorState_and_mapsWeeklySummaryToHistorySummary() = runTest {
        val repository = HistoryMeasurementRepository().apply {
            historyFailureMessage = "history boom"
        }
        val profiles = MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        val viewModel = HistoryViewModel(repository, HistoryProfileRepository(profiles), HistoryActiveProfileStore(1L))
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForHistoryState { viewModel.uiState.value is HistoryUiState.Error }
        assertEquals("history boom", (viewModel.uiState.value as HistoryUiState.Error).message)

        val method = Class.forName("com.example.nearworkthesis.feature.HistoryViewModelKt")
            .getDeclaredMethod("toHistorySummary", WeeklyDaySummary::class.java)
        method.isAccessible = true
        val mapped = method.invoke(null, WeeklyDaySummary("2026-06-15", 3, 30.0, 140.0, 0.5, 0.6, 2, "a", "b")) as HistoryDaySummary
        assertEquals("2026-06-15", mapped.day)
        assertEquals(3, mapped.sampleCount)
        assertEquals(0.6, mapped.nrs, 0.0)
        collectionJob.cancel()
    }
}

private fun waitForHistoryState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for history state", predicate())
}

private fun waitForHistoryEvent(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for history event", predicate())
}

private class HistoryProfileRepository(
    private val profilesFlow: MutableStateFlow<List<Profile>>
) : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = profilesFlow.value
    override fun observeProfiles(): Flow<List<Profile>> = profilesFlow
    override suspend fun getProfile(profileId: Long): Profile? = profilesFlow.value.firstOrNull { it.id == profileId }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private class HistoryActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    private val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) { activeId.value = id }
}

private class HistoryMeasurementRepository : MeasurementRepository {
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
    val availableDays = MutableStateFlow(listOf("2026-06-14", "2026-06-15"))
    val monthSummaries = MutableStateFlow(
        listOf(
            MonthDaySummary("2026-06-14", 2, 0.3, 0.4, 1),
            MonthDaySummary("2026-06-15", 3, 0.5, 0.6, 2)
        )
    )
    val dailySummaries = MutableStateFlow(
        listOf(
            WeeklyDaySummary("2026-06-14", 2, 35.0, 120.0, 0.3, 0.4, 1, null, null),
            WeeklyDaySummary("2026-06-15", 3, 30.0, 140.0, 0.5, 0.6, 2, null, null)
        )
    )
    var deleteResult = 0
    var latestDay: String? = "2026-06-15"
    var historyFailureMessage: String? = null

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()
    override suspend fun addMeasurements(measurements: List<Measurement>, duplicateResolutionPolicy: DuplicateResolutionPolicy): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }
    override suspend fun getLatestDay(profileId: Long): String? = latestDay
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))
    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw UnsupportedOperationException()
    }
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> {
        historyFailureMessage?.let { error(it) }
        return monthSummaries
    }
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> {
        historyFailureMessage?.let { error(it) }
        return dailySummaries
    }
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = availableDays
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = deleteResult
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): ResultsPackCsvs =
        ResultsPackCsvs("", "", "", "", 0)
}
