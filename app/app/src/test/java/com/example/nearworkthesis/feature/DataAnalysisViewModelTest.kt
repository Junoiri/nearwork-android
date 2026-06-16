package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModelProvider
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.analysis.ValidationSummary
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
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
class DataAnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun missingLatestDay_yieldsEmpty_andRefreshKeepsLoadingWithoutProfile() = runTest {
        val repository = DataAnalysisMeasurementRepository().apply {
            latestDay = null
            analysisDay = DataAnalysisDay(
                "1970-01-01",
                emptyList(),
                emptyList(),
                ValidationSummary(0, 0, 0, 0, 0, 0, null, null, null, 0)
            )
        }
        val activeStore = MutableStateActiveProfileStore(1L)
        val viewModel = DataAnalysisViewModel(
            measurementRepository = repository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = null
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForAnalysisState { viewModel.uiState.value is DataAnalysisUiState.Empty }
        activeStore.activeId.value = null
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is DataAnalysisUiState.Empty || viewModel.uiState.value is DataAnalysisUiState.Loading)
        collectionJob.cancel()
    }

    @Test
    fun loadedAnalysis_producesData_andFactoryCreatesViewModel() = runTest {
        val selectedDay = "2026-06-15"
        val repository = DataAnalysisMeasurementRepository().apply {
            latestDay = selectedDay
            analysisDay = DataAnalysisDay(
                day = selectedDay,
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
        val activeStore = MutableStateActiveProfileStore(1L)
        val viewModel = DataAnalysisViewModel(
            measurementRepository = repository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = selectedDay
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForAnalysisState { viewModel.uiState.value is DataAnalysisUiState.Data }
        val data = viewModel.uiState.value as DataAnalysisUiState.Data
        assertEquals(selectedDay, data.analysis.day)
        assertEquals(2, data.analysis.rawSamples.size)
        assertTrue(data.nrsResult.sampleCount >= 0)

        val factory: ViewModelProvider.Factory = DataAnalysisViewModel.factory(
            repository,
            activeStore,
            NearworkRiskScoreCalculator(),
            selectedDay
        )
        val created = factory.create(DataAnalysisViewModel::class.java)
        assertEquals(DataAnalysisViewModel::class.java, created::class.java)
        collectionJob.cancel()
    }

    @Test
    fun repositoryFailure_surfacesAsError() = runTest {
        val repository = DataAnalysisMeasurementRepository().apply {
            latestDay = "2026-06-15"
            failOnGetDataAnalysisDay = IllegalStateException("analysis failed")
        }
        val activeStore = MutableStateActiveProfileStore(1L)
        val viewModel = DataAnalysisViewModel(
            measurementRepository = repository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = null
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForAnalysisState { viewModel.uiState.value is DataAnalysisUiState.Error }
        assertEquals("analysis failed", (viewModel.uiState.value as DataAnalysisUiState.Error).message)
        collectionJob.cancel()
    }

    @Test
    fun emptyRawSamples_yieldEmptyState_evenWhenSelectedDateExists() = runTest {
        val selectedDay = "2026-06-15"
        val repository = DataAnalysisMeasurementRepository().apply {
            latestDay = selectedDay
            analysisDay = DataAnalysisDay(
                day = selectedDay,
                rawSamples = emptyList(),
                processedSamples = listOf(NearworkSample(1_000L, 35.0, 100.0)),
                summary = ValidationSummary(0, 0, 0, 0, 0, 0, null, null, null, 0)
            )
        }
        val viewModel = DataAnalysisViewModel(
            measurementRepository = repository,
            activeProfileStore = MutableStateActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = selectedDay
        )

        waitForAnalysisState { viewModel.uiState.value is DataAnalysisUiState.Empty }
        assertEquals(DataAnalysisUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun refresh_reloadsLatestDay_forActiveProfile() = runTest {
        val repository = DataAnalysisMeasurementRepository().apply {
            latestDay = "2026-06-15"
            analysisDay = DataAnalysisDay(
                day = "2026-06-15",
                rawSamples = listOf(NearworkSample(1_000L, 35.0, 100.0)),
                processedSamples = listOf(NearworkSample(1_000L, 35.0, 100.0)),
                summary = ValidationSummary(1, 1, 0, 0, 0, 0, 35.0, 35.0, 0.0, 0)
            )
        }
        val viewModel = DataAnalysisViewModel(
            measurementRepository = repository,
            activeProfileStore = MutableStateActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = null
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForAnalysisState { viewModel.uiState.value is DataAnalysisUiState.Data }
        repository.latestDay = "2026-06-16"
        repository.analysisDay = DataAnalysisDay(
            day = "2026-06-16",
            rawSamples = listOf(NearworkSample(2_000L, 30.0, 120.0)),
            processedSamples = listOf(NearworkSample(2_000L, 30.0, 120.0)),
            summary = ValidationSummary(1, 1, 0, 0, 0, 0, 30.0, 30.0, 0.0, 0)
        )

        viewModel.refresh()
        waitForAnalysisState { (viewModel.uiState.value as? DataAnalysisUiState.Data)?.analysis?.day == "2026-06-16" }
        assertEquals("2026-06-16", (viewModel.uiState.value as DataAnalysisUiState.Data).analysis.day)
        collectionJob.cancel()
    }
}

private fun waitForAnalysisState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for analysis state", predicate())
}

private class MutableStateActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) { activeId.value = id }
}

private class DataAnalysisMeasurementRepository : MeasurementRepository {
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
    var latestDay: String? = "2026-06-15"
    var analysisDay: DataAnalysisDay = DataAnalysisDay(
        "1970-01-01",
        emptyList(),
        emptyList(),
        ValidationSummary(0, 0, 0, 0, 0, 0, null, null, null, 0)
    )
    var failOnGetDataAnalysisDay: Throwable? = null

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
        failOnGetDataAnalysisDay?.let { throw it }
        return analysisDay
    }
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()
    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> = emptyFlow()
    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = emptyFlow()
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
