package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.importing.howfar.HowfarUf2Archive
import com.example.nearworkthesis.importing.howfar.HowfarUf2Snapshot
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
class ExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun availableDays_populateReadyState_andSettersUpdateModel() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()

        val initial = viewModel.uiState.value as ExportUiState.Ready
        assertEquals(ExportType.RawMeasurements, initial.model.exportType)
        assertEquals(ExportRangePreset.Last7Days, initial.model.preset)
        assertEquals("2026-06-05", initial.model.customStartDay)
        assertEquals("2026-06-07", initial.model.customEndDay)

        viewModel.setExportType(ExportType.AnalysisReport)
        viewModel.setPreset(ExportRangePreset.Custom)
        viewModel.setCustomStartDay("2026-06-07")
        viewModel.setCustomEndDay("2026-06-05")

        val updated = viewModel.uiState.value as ExportUiState.Ready
        assertEquals(ExportType.AnalysisReport, updated.model.exportType)
        assertEquals(ExportRangePreset.Custom, updated.model.preset)
        assertEquals("2026-06-07", updated.model.customStartDay)
        assertEquals("2026-06-05", updated.model.customEndDay)
    }

    @Test
    fun export_rawMeasurements_emitsDocumentEvent_andSuccessAfterSave() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07"),
            rawCsv = "timestamp,distance_cm\n2026-06-07,30"
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        val eventDeferred = async { viewModel.events.first() }

        viewModel.export()
        advanceUntilIdle()

        val event = eventDeferred.await() as ExportEvent.LaunchCreateDocument
        assertEquals("nearwork_raw_2026-06-05_2026-06-07.csv", event.filename)
        assertEquals("timestamp,distance_cm\n2026-06-07,30", event.csv)

        viewModel.onSaved(event.filename)

        val success = viewModel.uiState.value as ExportUiState.Success
        assertEquals(event.filename, success.filename)

        viewModel.dismissSuccess()
        assertTrue(viewModel.uiState.value is ExportUiState.Ready)
    }

    @Test
    fun export_dailySummaries_swapsCustomRangeBeforeNamingFile() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07"),
            dailyCsv = "day,total\n2026-06-06,2"
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        var observedEvent: ExportEvent? = null
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { event ->
                observedEvent = event
            }
        }

        viewModel.setExportType(ExportType.DailySummaries)
        viewModel.setPreset(ExportRangePreset.Custom)
        viewModel.setCustomStartDay("2026-06-07")
        viewModel.setCustomEndDay("2026-06-05")
        viewModel.export()
        waitForEvent { observedEvent != null }

        val event = observedEvent as ExportEvent.LaunchCreateDocument
        assertEquals("nearwork_daily_2026-06-05_2026-06-07.csv", event.filename)
        assertEquals("day,total\n2026-06-06,2", event.csv)
        collectionJob.cancel()
    }

    @Test
    fun export_resultsPackWithoutSampleDays_transitionsToEmpty() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07"),
            resultsPackCsvs = ResultsPackCsvs(
                manifestJson = "{}",
                dailyResultsCsv = "day,total\n",
                sessionsResultsCsv = "session,total\n",
                importQualityCsv = "filename,rows\n",
                daysWithSamples = 0
            )
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        viewModel.setExportType(ExportType.ResultsPack)
        viewModel.export()
        waitForExportToFinish(viewModel)

        assertTrue(viewModel.uiState.value is ExportUiState.Empty)
    }

    @Test
    fun export_howfarUf2WithoutSnapshot_emitsError() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive(snapshot = null)
        )

        advanceUntilIdle()
        viewModel.setExportType(ExportType.HowfarUf2)
        viewModel.export()
        waitForExportToFinish(viewModel)

        val error = viewModel.uiState.value as ExportUiState.Error
        assertEquals("No HowFar UF2 import found. Import from device first.", error.message)
    }

    @Test
    fun export_customPresetWithoutBothDays_emitsValidationError() = runTest {
        val repository = FakeExportMeasurementRepository(
            availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
        )
        val viewModel = ExportViewModel(
            measurementRepository = repository,
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        viewModel.setPreset(ExportRangePreset.Custom)
        viewModel.setCustomStartDay("2026-06-05")
        viewModel.setCustomEndDay("")
        viewModel.export()

        val error = viewModel.uiState.value as ExportUiState.Error
        assertEquals("Select both a start and end day.", error.message)

        viewModel.retry()
        assertTrue(viewModel.uiState.value is ExportUiState.Ready)
    }

    @Test
    fun noActiveProfile_keepsExportStateEmpty() = runTest {
        val viewModel = ExportViewModel(
            measurementRepository = FakeExportMeasurementRepository(
                availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
            ),
            activeProfileStore = FakeExportActiveProfileStore(null),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        viewModel.export()

        assertTrue(viewModel.uiState.value is ExportUiState.Empty)
    }

    @Test
    fun onSaveCancelled_fromReady_keepsReadyState() = runTest {
        val viewModel = ExportViewModel(
            measurementRepository = FakeExportMeasurementRepository(
                availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
            ),
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive(
                snapshot = HowfarUf2Snapshot("device.uf2", byteArrayOf(7, 8, 9), 1L)
            )
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ExportUiState.Ready)

        viewModel.onSaveCancelled()
        assertTrue(viewModel.uiState.value is ExportUiState.Ready)
    }

    @Test
    fun export_howfarUf2_emitsBinaryEvent_andOnSavedWorksFromReady() = runTest {
        val viewModel = ExportViewModel(
            measurementRepository = FakeExportMeasurementRepository(
                availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
            ),
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive(
                snapshot = HowfarUf2Snapshot("device.uf2", byteArrayOf(7, 8, 9), 1L)
            )
        )

        advanceUntilIdle()
        val eventDeferred = async { viewModel.events.first() }

        viewModel.setExportType(ExportType.HowfarUf2)
        viewModel.export()
        advanceUntilIdle()

        val event = eventDeferred.await() as ExportEvent.LaunchCreateBinary
        assertEquals("device.uf2", event.filename)
        assertTrue(event.bytes.contentEquals(byteArrayOf(7, 8, 9)))

        viewModel.onSaved(event.filename)
        val success = viewModel.uiState.value as ExportUiState.Success
        assertEquals("device.uf2", success.filename)
    }

    @Test
    fun onSaveFailed_fromReady_setsErrorAndRetryReturnsReady() = runTest {
        val viewModel = ExportViewModel(
            measurementRepository = FakeExportMeasurementRepository(
                availableDays = listOf("2026-06-05", "2026-06-06", "2026-06-07")
            ),
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        viewModel.onSaveFailed("Write failed")

        val error = viewModel.uiState.value as ExportUiState.Error
        assertEquals("Write failed", error.message)

        viewModel.retry()
        assertTrue(viewModel.uiState.value is ExportUiState.Ready)
    }

    @Test
    fun updateMethods_keepEmptyState_whenNoAvailableDays() = runTest {
        val viewModel = ExportViewModel(
            measurementRepository = FakeExportMeasurementRepository(availableDays = emptyList()),
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        advanceUntilIdle()
        viewModel.setExportType(ExportType.AnalysisReport)
        viewModel.setPreset(ExportRangePreset.AllAvailable)
        viewModel.dismissSuccess()

        assertTrue(viewModel.uiState.value is ExportUiState.Empty)
    }

    @Test
    fun factory_createsExportViewModel() {
        val factory = ExportViewModel.factory(
            measurementRepository = FakeExportMeasurementRepository(availableDays = emptyList()),
            activeProfileStore = FakeExportActiveProfileStore(1L),
            howfarUf2Archive = FakeHowfarUf2Archive()
        )

        val created = factory.create(ExportViewModel::class.java)

        assertTrue(created is ExportViewModel)
    }

    private fun waitForExportToFinish(viewModel: ExportViewModel) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (viewModel.uiState.value is ExportUiState.Exporting && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L)
        }
    }

    private fun waitForEvent(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (!predicate() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L)
        }
        assertTrue("Timed out waiting for export event", predicate())
    }
}

private class FakeExportMeasurementRepository(
    private val availableDays: List<String>,
    private val rawCsv: String = "timestamp,distance_cm\n",
    private val dailyCsv: String = "day,total\n",
    private val analysisCsv: String = "day,nrs\n",
    private val resultsPackCsvs: ResultsPackCsvs = ResultsPackCsvs(
        manifestJson = "{}",
        dailyResultsCsv = "day,total\n2026-06-05,1",
        sessionsResultsCsv = "session,total\n1,1",
        importQualityCsv = "filename,rows\nsample.csv,1",
        daysWithSamples = 1
    )
) : MeasurementRepository {

    private val analysisConfig = AnalysisConfig(
        thresholds = AnalysisThresholds(
            lowLightThresholdLux = 300,
            nearworkDistanceThresholdCm = 60,
            breakGapSeconds = 60,
            minSessionDurationSeconds = 60,
            closeDistanceThresholdCm = 30,
            extremeCloseThresholdCm = 20
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

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        throw UnsupportedOperationException()
    }

    override suspend fun getLatestDay(profileId: Long): String? = availableDays.lastOrNull()

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()

    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)

    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> =
        flowOf(DailySessionInsights(emptyList(), null, emptyList()))

    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw UnsupportedOperationException()
    }

    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> = emptyFlow()

    override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> =
        emptyFlow()

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> = emptyFlow()

    override fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> =
        emptyFlow()

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = flowOf(availableDays)

    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)

    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)

    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig

    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0

    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = rawCsv

    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""

    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = dailyCsv

    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = analysisCsv

    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): ResultsPackCsvs = resultsPackCsvs
}

private class FakeExportActiveProfileStore(profileId: Long?) : ActiveProfileStore {
    private val activeProfileId = MutableStateFlow(profileId)

    override fun observeActiveProfileId(): Flow<Long?> = activeProfileId

    override suspend fun setActiveProfileId(id: Long) {
        activeProfileId.value = id
    }
}

private class FakeHowfarUf2Archive(
    private val snapshot: HowfarUf2Snapshot? = HowfarUf2Snapshot(
        filename = "device.uf2",
        bytes = byteArrayOf(1, 2, 3),
        savedAtEpochMillis = 1L
    )
) : HowfarUf2Archive {
    override suspend fun saveLatest(profileId: Long, filename: String, bytes: ByteArray) = Unit
    override suspend fun loadLatest(profileId: Long): HowfarUf2Snapshot? = snapshot
}
