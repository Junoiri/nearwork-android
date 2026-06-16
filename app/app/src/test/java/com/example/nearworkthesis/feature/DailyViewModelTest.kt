package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModelProvider
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkRiskReason
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.analysis.NearworkSession
import com.example.nearworkthesis.domain.analysis.NearworkSessionRisk
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
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import java.time.LocalDate
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun loadsData_navigatesDays_andBuildsFallbackSummary() = runTest {
        val today = LocalDate.now()
        val previousDay = today.minusDays(1)
        val repository = DailyMeasurementRepository().apply {
            availableDays.value = listOf(previousDay.toString(), today.toString())
            summariesByDay[today.toString()] = summary(day = today.toString(), sampleCount = 2, avgDistance = 35.0)
            summariesByDay[previousDay.toString()] = null
            measurementsByDay[today.toString()] = listOf(
                measurement(id = 1, day = today.toString(), timestamp = 1_000L, distance = 35.0, lux = 120.0),
                measurement(id = 2, day = today.toString(), timestamp = 2_000L, distance = 45.0, lux = 220.0)
            )
            measurementsByDay[previousDay.toString()] = listOf(
                measurement(id = 3, day = previousDay.toString(), timestamp = 10_000L, distance = 32.0, lux = 80.0),
                measurement(id = 4, day = previousDay.toString(), timestamp = 70_000L, distance = 28.0, lux = 60.0)
            )
            analysisDaysByDay[today.toString()] = analysisDay(day = today.toString())
            analysisDaysByDay[previousDay.toString()] = analysisDay(day = previousDay.toString())
            insightsByDay[today.toString()] = insights()
            insightsByDay[previousDay.toString()] = insights()
        }
        val profileRepository = DailyProfileRepository(
            MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        )
        val activeStore = DailyActiveProfileStore(1L)
        val viewModel = DailyViewModel(
            measurementRepository = repository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val uiCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        val navCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.canGoBack.collect { }
        }
        val forwardCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.canGoForward.collect { }
        }

        waitForDailyState { viewModel.uiState.value is DailyUiState.Data }
        advanceUntilIdle()
        val todayData = viewModel.uiState.value as DailyUiState.Data
        assertEquals(today.toString(), todayData.summary.day)
        assertEquals(2, todayData.sampleCount)
        assertNotNull(todayData.avgDistanceCm)
        assertEquals(40.0, todayData.avgDistanceCm!!, 0.0)
        assertEquals(1, todayData.sessions.size)
        assertTrue(NearworkRiskReason.LowLight in todayData.sessions.first().reasons)
        assertTrue(todayData.isCurrentDay)
        assertTrue(viewModel.canGoBack.value)
        assertFalse(viewModel.canGoForward.value)

        viewModel.goToPreviousDay()
        waitForDailyState {
            (viewModel.uiState.value as? DailyUiState.Data)?.summary?.day == previousDay.toString()
        }
        advanceUntilIdle()
        val previousData = viewModel.uiState.value as DailyUiState.Data
        assertEquals(previousDay.toString(), previousData.summary.day)
        assertEquals(2, previousData.summary.sampleCount)
        assertNotNull(previousData.summary.firstTimestampIso)
        assertNotNull(previousData.summary.lastTimestampIso)
        assertFalse(previousData.isCurrentDay)
        assertTrue(viewModel.canGoForward.value)

        viewModel.goToToday()
        waitForDailyState {
            (viewModel.uiState.value as? DailyUiState.Data)?.summary?.day == today.toString()
        }
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as DailyUiState.Data).isCurrentDay)

        uiCollection.cancel()
        navCollection.cancel()
        forwardCollection.cancel()
    }

    @Test
    fun deleteCurrentDay_emitsSuccessAndFailureEvents() = runTest {
        val day = LocalDate.now().minusDays(1).toString()
        val repository = DailyMeasurementRepository().apply {
            availableDays.value = listOf(day)
            summariesByDay[day] = summary(day = day, sampleCount = 1, avgDistance = 30.0)
            measurementsByDay[day] = listOf(
                measurement(id = 1, day = day, timestamp = 1_000L, distance = 30.0, lux = 100.0)
            )
            analysisDaysByDay[day] = analysisDay(day = day)
            insightsByDay[day] = insights()
        }
        val profileRepository = DailyProfileRepository(
            MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        )
        val activeStore = DailyActiveProfileStore(1L)
        val viewModel = DailyViewModel(
            measurementRepository = repository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            initialSelectedDate = day
        )
        var event: DailyUiEvent? = null
        val uiCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        val eventCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { event = it }
        }

        waitForDailyState { viewModel.uiState.value is DailyUiState.Data }
        repository.deleteResult = 1
        viewModel.deleteCurrentDay()
        waitForDailyEvent { event is DailyUiEvent.DayDeleted }
        assertEquals(
            DailyUiEvent.DayDeleted(day, true),
            event
        )

        event = null
        repository.deleteResult = 0
        viewModel.deleteCurrentDay()
        waitForDailyEvent { event is DailyUiEvent.DeleteFailed }
        assertEquals(DailyUiEvent.DeleteFailed(day), event)

        uiCollection.cancel()
        eventCollection.cancel()
    }

    @Test
    fun refreshWithoutActiveProfile_keepsLoading_andFactoryCreatesViewModel() = runTest {
        val repository = DailyMeasurementRepository()
        val profileRepository = DailyProfileRepository(MutableStateFlow(emptyList()))
        val activeStore = DailyActiveProfileStore(null)
        val viewModel = DailyViewModel(
            measurementRepository = repository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val uiCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(DailyUiState.Loading, viewModel.uiState.value)

        val factory: ViewModelProvider.Factory = DailyViewModel.factory(
            measurementRepository = repository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            selectedDate = null
        )
        val created = factory.create(DailyViewModel::class.java)
        assertEquals(DailyViewModel::class.java, created::class.java)
        uiCollection.cancel()
    }

    @Test
    fun repositoryFailure_surfacesErrorState() = runTest {
        val day = LocalDate.now().toString()
        val repository = DailyMeasurementRepository().apply {
            availableDays.value = listOf(day)
            summariesByDay[day] = summary(day = day, sampleCount = 1, avgDistance = 35.0)
            insightsByDay[day] = insights()
            failureMessage = "boom"
        }
        val profileRepository = DailyProfileRepository(
            MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))
        )
        val activeStore = DailyActiveProfileStore(1L)
        val viewModel = DailyViewModel(
            measurementRepository = repository,
            profileRepository = profileRepository,
            activeProfileStore = activeStore,
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
        )
        val uiCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForDailyState { viewModel.uiState.value is DailyUiState.Error }
        assertEquals("boom", (viewModel.uiState.value as DailyUiState.Error).message)
        uiCollection.cancel()
    }

    @Test
    fun navigationAndDeleteNoOps_leaveStateUntouched_atBoundsOrWithoutContext() = runTest {
        val today = LocalDate.now()
        val repository = DailyMeasurementRepository().apply {
            availableDays.value = listOf(today.toString())
            summariesByDay[today.toString()] = summary(day = today.toString(), sampleCount = 1, avgDistance = 35.0)
            measurementsByDay[today.toString()] = listOf(
                measurement(id = 1, day = today.toString(), timestamp = 1_000L, distance = 35.0, lux = 120.0)
            )
            analysisDaysByDay[today.toString()] = analysisDay(day = today.toString())
            insightsByDay[today.toString()] = insights()
        }
        val viewModel = DailyViewModel(
            measurementRepository = repository,
            profileRepository = DailyProfileRepository(MutableStateFlow(listOf(Profile(1L, "Alpha", 10L, "UTC", null)))),
            activeProfileStore = DailyActiveProfileStore(1L),
            nearworkRiskScoreCalculator = NearworkRiskScoreCalculator(),
            initialSelectedDate = today.toString()
        )
        val uiCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        waitForDailyState { viewModel.uiState.value is DailyUiState.Data }
        val before = viewModel.uiState.value
        assertFalse(viewModel.canGoForward.value)

        viewModel.goToNextDay()
        viewModel.deleteCurrentDay()
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value)
        uiCollection.cancel()
    }
}

private fun waitForDailyState(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for daily state", predicate())
}

private fun waitForDailyEvent(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for daily event", predicate())
}

private fun summary(day: String, sampleCount: Int, avgDistance: Double): DailySummary =
    DailySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistance,
        minDistanceCm = avgDistance - 5.0,
        maxDistanceCm = avgDistance + 5.0,
        avgLux = 150.0,
        minLux = 100.0,
        maxLux = 200.0,
        diopterHoursTotal = 0.1,
        lowLightMinutes = 1,
        firstTimestampIso = "2026-01-01T00:00:01",
        lastTimestampIso = "2026-01-01T00:01:00"
    )

private fun measurement(
    id: Long,
    day: String,
    timestamp: Long,
    distance: Double,
    lux: Double
): Measurement = Measurement(id, 1L, 10L, timestamp, day, distance, lux)

private fun analysisDay(day: String): DataAnalysisDay =
    DataAnalysisDay(
        day = day,
        rawSamples = listOf(
            NearworkSample(1_000L, 32.0, 80.0),
            NearworkSample(61_000L, 28.0, 60.0)
        ),
        processedSamples = listOf(
            NearworkSample(1_000L, 32.0, 80.0),
            NearworkSample(61_000L, 28.0, 60.0)
        ),
        summary = ValidationSummary(2, 2, 0, 0, 0, 0, 30.0, 30.0, 0.0, 0)
    )

private fun insights(): DailySessionInsights {
    val session = NearworkSession(
        startTimestampMillis = 1_000L,
        endTimestampMillis = 61_000L,
        durationSeconds = 60L,
        avgDistanceCm = 30.0,
        minDistanceCm = 28.0,
        diopterHoursInSession = 0.1,
        lowLightSecondsInSession = 60L
    )
    return DailySessionInsights(
        sessions = listOf(session),
        longestSession = session,
        flaggedSessions = listOf(
            NearworkSessionRisk(session, setOf(NearworkRiskReason.LowLight, NearworkRiskReason.CloseDistance))
        )
    )
}

private class DailyProfileRepository(
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

private class DailyActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    private val activeId = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = activeId
    override suspend fun setActiveProfileId(id: Long) {
        activeId.value = id
    }
}

private class DailyMeasurementRepository : MeasurementRepository {
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
    val summariesByDay = linkedMapOf<String, DailySummary?>()
    val measurementsByDay = linkedMapOf<String, List<Measurement>>()
    val insightsByDay = linkedMapOf<String, DailySessionInsights>()
    val analysisDaysByDay = linkedMapOf<String, DataAnalysisDay>()
    var deleteResult = 0
    var failureMessage: String? = null

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> =
        measurementsByDay.values.flatten()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }

    override suspend fun getLatestDay(profileId: Long): String? = availableDays.value.lastOrNull()

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> {
        failureMessage?.let { error(it) }
        return measurementsByDay[localDay].orEmpty()
    }

    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> =
        flowOf(summariesByDay[day])

    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(insightsByDay[day] ?: DailySessionInsights(emptyList(), null, emptyList()))

    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        failureMessage?.let { error(it) }
        return analysisDaysByDay[day] ?: analysisDay(day)
    }

    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()

    override fun observeDaySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<MonthDaySummary>> = emptyFlow()

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> =
        emptyFlow()

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<WeeklyDaySummary>> = emptyFlow()

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = availableDays

    override fun observeMeasurementCount(profileId: Long): Flow<Int> =
        flowOf(measurementsByDay.values.sumOf { it.size })

    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)

    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig

    override suspend fun deleteDay(profileId: Long, localDay: String): Int = deleteResult

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
