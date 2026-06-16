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
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
class WeeklyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun loadsData_selectsRanges_andReturnsToCurrentRange() = runTest {
        val repository = WeeklyMeasurementRepository().apply {
            availableDays.value = listOf("2026-06-01", "2026-06-03", "2026-06-08", "2026-06-14")
            weeklySummaries.value = listOf(
                weeklyDay("2026-06-01", 0.4),
                weeklyDay("2026-06-03", 0.6),
                weeklyDay("2026-06-08", 0.8),
                weeklyDay("2026-06-14", 1.2)
            )
        }
        val activeStore = WeeklyActiveProfileStore(1L)
        val viewModel = WeeklyViewModel(
            measurementRepository = repository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForWeeklyState { viewModel.uiState.value is WeeklyUiState.Data }
        advanceUntilIdle()
        val initial = viewModel.uiState.value as WeeklyUiState.Data
        assertEquals(2.0, initial.totalNrs, 0.0)
        assertEquals(WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")), initial.selectedRange)
        assertEquals(
            listOf(
                WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")),
                WeekRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"))
            ),
            initial.availableRanges
        )

        viewModel.selectRange(WeekRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07")))
        waitForWeeklyState {
            (viewModel.uiState.value as? WeeklyUiState.Data)?.selectedRange?.end == LocalDate.parse("2026-06-07")
        }
        advanceUntilIdle()
        val historical = viewModel.uiState.value as WeeklyUiState.Data
        assertEquals(WeekRange(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07")), historical.selectedRange)
        assertEquals(1.0, historical.totalNrs, 0.0)

        viewModel.goToCurrentRange()
        waitForWeeklyState {
            (viewModel.uiState.value as? WeeklyUiState.Data)?.selectedRange?.end == LocalDate.parse("2026-06-14")
        }
        advanceUntilIdle()
        assertEquals(
            WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")),
            (viewModel.uiState.value as WeeklyUiState.Data).selectedRange
        )

        collectionJob.cancel()
    }

    @Test
    fun selectedRangesClampToAvailableBounds() = runTest {
        val repository = WeeklyMeasurementRepository().apply {
            availableDays.value = listOf("2026-06-01", "2026-06-08", "2026-06-14")
            weeklySummaries.value = listOf(
                weeklyDay("2026-06-01", 0.5),
                weeklyDay("2026-06-08", 1.0),
                weeklyDay("2026-06-14", 1.5)
            )
        }
        val viewModel = WeeklyViewModel(
            measurementRepository = repository,
            activeProfileStore = WeeklyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForWeeklyState { viewModel.uiState.value is WeeklyUiState.Data }
        viewModel.selectRange(WeekRange(LocalDate.parse("2026-05-20"), LocalDate.parse("2026-05-25")))
        waitForWeeklyState {
            (viewModel.uiState.value as? WeeklyUiState.Data)?.selectedRange?.end == LocalDate.parse("2026-06-01")
        }
        assertEquals(
            WeekRange(LocalDate.parse("2026-05-26"), LocalDate.parse("2026-06-01")),
            (viewModel.uiState.value as WeeklyUiState.Data).selectedRange
        )

        viewModel.selectRange(WeekRange(LocalDate.parse("2026-06-20"), LocalDate.parse("2026-06-25")))
        waitForWeeklyState {
            (viewModel.uiState.value as? WeeklyUiState.Data)?.selectedRange?.end == LocalDate.parse("2026-06-14")
        }
        assertEquals(
            WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")),
            (viewModel.uiState.value as WeeklyUiState.Data).selectedRange
        )

        collectionJob.cancel()
    }

    @Test
    fun retry_andFactory_coverEmptyState() = runTest {
        val repository = WeeklyMeasurementRepository()
        val viewModel = WeeklyViewModel(
            measurementRepository = repository,
            activeProfileStore = WeeklyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForWeeklyState { viewModel.uiState.value is WeeklyUiState.Empty }
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is WeeklyUiState.Empty)

        val factory: ViewModelProvider.Factory = WeeklyViewModel.factory(
            measurementRepository = repository,
            activeProfileStore = WeeklyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val created = factory.create(WeeklyViewModel::class.java)
        assertEquals(WeeklyViewModel::class.java, created::class.java)

        collectionJob.cancel()
    }

    @Test
    fun repositoryFailure_surfacesErrorState() = runTest {
        val repository = WeeklyMeasurementRepository().apply {
            availableDays.value = listOf("2026-06-14")
            weeklySummaries.value = listOf(weeklyDay("2026-06-14", 1.0))
            failOnSummaries = "weekly boom"
        }
        val viewModel = WeeklyViewModel(
            measurementRepository = repository,
            activeProfileStore = WeeklyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForWeeklyState { viewModel.uiState.value is WeeklyUiState.Error }
        assertEquals("weekly boom", (viewModel.uiState.value as WeeklyUiState.Error).message)
        collectionJob.cancel()
    }

    @Test
    fun customDayWindow_andInvalidAvailableDays_areHandled() = runTest {
        val repository = WeeklyMeasurementRepository().apply {
            availableDays.value = listOf("bad-day", "2026-06-10", "2026-06-12")
            weeklySummaries.value = listOf(
                weeklyDay("2026-06-10", 0.5),
                weeklyDay("2026-06-12", 0.7)
            )
        }
        val viewModel = WeeklyViewModel(
            measurementRepository = repository,
            activeProfileStore = WeeklyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            days = 3
        )
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForWeeklyState { viewModel.uiState.value is WeeklyUiState.Data }
        val data = viewModel.uiState.value as WeeklyUiState.Data
        assertEquals(WeekRange(LocalDate.parse("2026-06-10"), LocalDate.parse("2026-06-12")), data.selectedRange)
        assertEquals(listOf(WeekRange(LocalDate.parse("2026-06-06"), LocalDate.parse("2026-06-12"))), data.availableRanges)
        assertEquals(1.2, data.totalNrs, 0.0)
        collectionJob.cancel()
    }
}

private fun waitForWeeklyState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for weekly state", predicate())
}

private fun weeklyDay(day: String, nrs: Double): WeeklyDaySummary =
    WeeklyDaySummary(
        day = day,
        sampleCount = 2,
        avgDistanceCm = 35.0,
        avgLux = 150.0,
        diopterHoursTotal = 0.1,
        nrs = nrs,
        lowLightMinutes = 1,
        firstTimestampIso = null,
        lastTimestampIso = null
    )

private class WeeklyActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    private val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) {
        activeId.value = id
    }
}

private class WeeklyMeasurementRepository : MeasurementRepository {
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

    val availableDays = MutableStateFlow<List<String>>(emptyList())
    val weeklySummaries = MutableStateFlow<List<WeeklyDaySummary>>(emptyList())
    var failOnSummaries: String? = null

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }

    override suspend fun getLatestDay(profileId: Long): String? = availableDays.value.lastOrNull()

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()

    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)

    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))

    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw UnsupportedOperationException()
    }

    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()

    override fun observeDaySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<MonthDaySummary>> = emptyFlow()

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<WeeklyDaySummary>> {
        val failure = failOnSummaries
        if (failure != null) {
            return flow { error(failure) }
        }
        return weeklySummaries
            .let { summaries ->
                flowOf(
                    summaries.value.filter { summary ->
                        summary.day >= startDay && summary.day <= endDay
                    }
                )
            }
    }

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = availableDays

    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)

    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)

    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig

    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0

    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""

    override suspend fun exportProcessedCsv(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): String = ""

    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""

    override suspend fun exportAnalysisReportCsv(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): String = ""

    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): ResultsPackCsvs = ResultsPackCsvs("", "", "", "", 0)
}
