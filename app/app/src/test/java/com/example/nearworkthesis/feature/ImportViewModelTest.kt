package com.example.nearworkthesis.feature

import android.net.Uri
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportStatusRepository
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.ImportSession
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.domain.repository.ImportSessionRepository
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.importing.ImportTransactionRunner
import com.example.nearworkthesis.importing.SampleCsvParser
import com.example.nearworkthesis.importing.howfar.HowfarDataParser
import com.example.nearworkthesis.importing.howfar.HowfarImportInteractor
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageState
import com.example.nearworkthesis.importing.howfar.HowfarUf2Archive
import com.example.nearworkthesis.importing.howfar.HowfarUf2Snapshot
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun importSample_success_updatesLastResult_andSchedulesNotification() = runTest {
        val summary = importSummary(
            filename = "sample.csv",
            firstTimestampEpochMillis = 1_780_617_600_000L,
            lastTimestampEpochMillis = 1_780_621_200_000L
        )
        val importStatusRepository = FakeImportStatusRepository(
            importSampleResult = ImportResult.Success(summary)
        )
        val notifications = FakeNotificationScheduler()
        val viewModel = buildViewModel(
            importStatusRepository = importStatusRepository,
            notificationScheduler = notifications
        )

        viewModel.importSample(SampleImportOption(label = "Sample Day", fileName = "sample.csv"))
        advanceUntilIdle()

        val last = viewModel.lastResult.value
        assertEquals(ImportOutcome.Success, last?.outcome)
        assertEquals("sample.csv", last?.summary?.filename)
        assertEquals("sample.csv", importStatusRepository.lastSampleFileName)
        assertEquals("sample.csv", notifications.lastSummary?.filename)
        assertTrue(viewModel.dialogState.value is ImportDialogState.None)
    }

    @Test
    fun importCsvBytes_error_setsDialogState() = runTest {
        val importStatusRepository = FakeImportStatusRepository(
            importCsvResult = ImportResult.Error("Broken CSV")
        )
        val viewModel = buildViewModel(importStatusRepository = importStatusRepository)

        viewModel.importCsvBytes("bad.csv", byteArrayOf(1, 2, 3), ImportSourceType.FILE)
        advanceUntilIdle()

        val dialog = viewModel.dialogState.value as ImportDialogState.Error
        assertEquals("Broken CSV", dialog.message)
    }

    @Test
    fun importSample_noNewData_setsOutcomeWithoutNotification() = runTest {
        val summary = importSummary(
            filename = "sample.csv",
            firstTimestampEpochMillis = 1_780_617_600_000L,
            lastTimestampEpochMillis = 1_780_621_200_000L
        )
        val notifications = FakeNotificationScheduler()
        val viewModel = buildViewModel(
            importStatusRepository = FakeImportStatusRepository(importSampleResult = ImportResult.NoNewData(summary)),
            notificationScheduler = notifications
        )

        viewModel.importSample(SampleImportOption(label = "Sample Day", fileName = "sample.csv"))
        advanceUntilIdle()

        assertEquals(ImportOutcome.NoNewData, viewModel.lastResult.value?.outcome)
        assertNull(notifications.lastSummary)
    }

    @Test
    fun deleteImportedDays_success_deletesAffectedDays_andClearsLastResult() = runTest {
        val summary = importSummary(
            filename = "sample.csv",
            firstTimestampEpochMillis = 1_780_697_400_000L,
            lastTimestampEpochMillis = 1_780_704_600_000L
        )
        val measurementRepository = FakeImportMeasurementRepository(deleteDayResult = 3)
        val viewModel = buildViewModel(
            importStatusRepository = FakeImportStatusRepository(importSampleResult = ImportResult.Success(summary)),
            measurementRepository = measurementRepository
        )

        viewModel.importSample(SampleImportOption(label = "Sample Day", fileName = "sample.csv"))
        advanceUntilIdle()
        viewModel.deleteImportedDays()
        advanceUntilIdle()

        assertEquals(listOf("2026-06-05", "2026-06-06"), measurementRepository.deletedDays)
        assertNull(viewModel.lastResult.value)
    }

    @Test
    fun dismissDialogAndClearLastResult_resetUiState() = runTest {
        val successSummary = importSummary(
            filename = "sample.csv",
            firstTimestampEpochMillis = 1_780_617_600_000L,
            lastTimestampEpochMillis = 1_780_621_200_000L
        )
        val viewModel = buildViewModel(
            importStatusRepository = FakeImportStatusRepository(
                importSampleResult = ImportResult.Success(successSummary),
                importCsvResult = ImportResult.Error("Broken CSV")
            )
        )

        viewModel.importCsvBytes("bad.csv", byteArrayOf(1), ImportSourceType.FILE)
        advanceUntilIdle()
        assertTrue(viewModel.dialogState.value is ImportDialogState.Error)

        viewModel.dismissDialog()
        assertTrue(viewModel.dialogState.value is ImportDialogState.None)

        viewModel.importSample(SampleImportOption(label = "Sample Day", fileName = "sample.csv"))
        advanceUntilIdle()
        assertTrue(viewModel.lastResult.value != null)

        viewModel.clearLastResult()
        assertNull(viewModel.lastResult.value)
    }

    @Test
    fun deleteImportedDays_ignoresNoNewDataResult() = runTest {
        val summary = importSummary(
            filename = "sample.csv",
            firstTimestampEpochMillis = 1_780_617_600_000L,
            lastTimestampEpochMillis = 1_780_621_200_000L
        )
        val measurementRepository = FakeImportMeasurementRepository(deleteDayResult = 3)
        val viewModel = buildViewModel(
            importStatusRepository = FakeImportStatusRepository(importSampleResult = ImportResult.NoNewData(summary)),
            measurementRepository = measurementRepository
        )

        viewModel.importSample(SampleImportOption(label = "Sample Day", fileName = "sample.csv"))
        advanceUntilIdle()
        viewModel.deleteImportedDays()
        advanceUntilIdle()

        assertTrue(measurementRepository.deletedDays.isEmpty())
        assertEquals(ImportOutcome.NoNewData, viewModel.lastResult.value?.outcome)
    }

    @Test
    fun refreshAndSetDeviceUri_forwardWhenIdle() = runTest {
        val storageRepository = FakeHowfarStorageRepositoryForVm()
        val viewModel = buildViewModel(storageRepository = storageRepository)

        viewModel.refreshHowfar()
        viewModel.setDeviceTreeUri(null)

        assertEquals(1, storageRepository.refreshCalls)
        assertEquals(1, storageRepository.setUriCalls)
    }

    @Test
    fun storageStateChanges_updateHowfarUiModel() = runTest {
        val storageRepository = FakeHowfarStorageRepositoryForVm()
        val viewModel = buildViewModel(storageRepository = storageRepository)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiModel.collect { }
        }

        val disconnected = viewModel.uiModel.value.howfar
        assertEquals("HowFar: Storage not selected", disconnected.statusTitle)
        assertEquals(ImportHowfarPrimaryAction.SelectDevice, disconnected.primaryAction)

        storageRepository.emitState(HowfarStorageState.Error("Broken"))
        advanceUntilIdle()

        val error = viewModel.uiModel.value.howfar
        assertEquals("HowFar: Not connected", error.statusTitle)
        assertEquals(ImportHowfarPrimaryAction.SelectDevice, error.primaryAction)
        collectionJob.cancel()
    }

    @Test
    fun manualAnchorAndCropModes_keepDerivedTimesInSync() = runTest {
        val viewModel = buildViewModel()
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiModel.collect { }
        }
        val anchorMillis = 1_780_704_600_000L

        viewModel.setAnchorMode(ImportAnchorMode.Manual)
        viewModel.setCustomAnchorMillis(anchorMillis)
        viewModel.setCropStartMode(ImportCropStartMode.Manual)
        viewModel.setCropEndMode(ImportCropEndMode.Manual)
        viewModel.setCropStartMillis(anchorMillis - 3_600_000L)
        viewModel.setCropEndMillis(anchorMillis - 1_800_000L)
        advanceUntilIdle()

        val model = viewModel.uiModel.value
        assertEquals(ImportAnchorMode.Manual, model.anchorMode)
        assertEquals(anchorMillis, model.customAnchorMillis)
        assertEquals(ImportCropStartMode.Manual, model.cropStartMode)
        assertEquals(anchorMillis - 3_600_000L, model.cropStartMillis)
        assertEquals(ImportCropEndMode.Manual, model.cropEndMode)
        assertEquals(anchorMillis - 1_800_000L, model.cropEndMillis)
        collectionJob.cancel()
    }

    @Test
    fun autoModes_resetCropWindowFromSelectedAnchor() = runTest {
        val viewModel = buildViewModel()
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiModel.collect { }
        }
        val anchorMillis = 1_780_704_600_000L

        viewModel.setAnchorMode(ImportAnchorMode.Manual)
        viewModel.setCustomAnchorMillis(anchorMillis)
        viewModel.setCropEndMode(ImportCropEndMode.Auto)
        viewModel.setCropStartMode(ImportCropStartMode.Disabled)
        advanceUntilIdle()

        val model = viewModel.uiModel.value
        assertEquals(anchorMillis, model.cropEndMillis)
        assertEquals(anchorMillis - 8L * 60L * 60L * 1000L, model.cropStartMillis)
        collectionJob.cancel()
    }

    @Test
    fun factory_createsImportViewModel() {
        val factory = ImportViewModel.factory(
            importStatusRepository = FakeImportStatusRepository(),
            storageRepository = FakeHowfarStorageRepositoryForVm(),
            howfarImportInteractor = HowfarImportInteractor(
                storageRepository = FakeHowfarStorageRepositoryForVm(),
                howfarUf2Archive = FakeHowfarUf2ArchiveForVm(),
                dataParser = HowfarDataParser(),
                activeProfileStore = FakeActiveProfileStoreForImport(7L),
                csvImporter = CsvBytesImportInteractor(
                    transactionRunner = FakeImportTransactionRunner(),
                    importSessionRepository = FakeImportSessionRepository(),
                    measurementRepository = FakeImportMeasurementRepository(),
                    settingsStore = FakeImportSettingsStore(),
                    profileRepository = FakeImportProfileRepository()
                )
            ),
            measurementRepository = FakeImportMeasurementRepository(),
            notificationScheduler = FakeNotificationScheduler(),
            activeProfileStore = FakeActiveProfileStoreForImport(7L)
        )

        val created = factory.create(ImportViewModel::class.java)

        assertTrue(created is ImportViewModel)
    }

    @Test
    fun privateTimeHelpers_followAnchorAndCropModes() = runTest {
        val viewModel = buildViewModel()
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiModel.collect { }
        }
        val anchorMillis = 1_780_704_600_000L

        viewModel.setAnchorMode(ImportAnchorMode.Manual)
        viewModel.setCustomAnchorMillis(anchorMillis)
        viewModel.setCropStartMode(ImportCropStartMode.Manual)
        viewModel.setCropStartMillis(anchorMillis + 60_000L)
        viewModel.setCropEndMode(ImportCropEndMode.Manual)
        viewModel.setCropEndMillis(anchorMillis + 120_000L)

        assertEquals(anchorMillis, invokeLongHelper(viewModel, "resolveAnchorMillis"))
        assertEquals(anchorMillis, invokeLongHelper(viewModel, "previewAnchorMillis"))
        assertEquals(anchorMillis, invokeNullableLongHelper(viewModel, "resolveCropStartMillis", anchorMillis))
        assertEquals(anchorMillis, invokeNullableLongHelper(viewModel, "resolveCropEndMillis", anchorMillis))

        collectionJob.cancel()
    }

    @Test
    fun buildAffectedLocalDays_andResolveZoneId_coverFallbackBranches() {
        val viewModel = buildViewModel()

        val fallbackZone = invokeZoneIdHelper(viewModel, "resolveZoneId", "Bad/Timezone")
        assertEquals(java.time.ZoneId.systemDefault(), fallbackZone)

        val reversedDays = invokeLocalDaysHelper(
            viewModel,
            first = 1_780_704_600_000L,
            last = 1_780_617_600_000L,
            zoneId = java.time.ZoneId.of("UTC")
        )
        assertEquals(listOf("2026-06-06"), reversedDays)

        val multiDay = invokeLocalDaysHelper(
            viewModel,
            first = 1_780_617_600_000L,
            last = 1_780_704_600_000L,
            zoneId = java.time.ZoneId.of("UTC")
        )
        assertEquals(listOf("2026-06-05", "2026-06-06"), multiDay)
        assertNotNull(invokeZoneIdHelper(viewModel, "resolveZoneId", null))
    }

    @Test
    fun privateHowfarHelpers_coverReadyFormatting_andAutoModes() {
        val viewModel = buildViewModel()
        val disconnectedModel = invokeAnyHelper(viewModel, "toHowfarUiModel", HowfarStorageState.Disconnected, HowfarStorageState::class.java) as ImportHowfarUiModel
        val autoViewModel = buildViewModel()

        assertEquals("HowFar: Storage not selected", disconnectedModel.statusTitle)
        assertEquals(ImportHowfarPrimaryAction.SelectDevice, disconnectedModel.primaryAction)
        assertEquals(null, invokeNullableLongHelper(autoViewModel, "resolveCropStartMillis", 1_000L))
        assertEquals(null, invokeNullableLongHelper(autoViewModel, "resolveCropEndMillis", 1_000L))
    }

}

private fun invokeLongHelper(viewModel: ImportViewModel, methodName: String): Long {
    val method = ImportViewModel::class.java.getDeclaredMethod(methodName)
    method.isAccessible = true
    return method.invoke(viewModel) as Long
}

private fun invokeAnyHelper(viewModel: ImportViewModel, methodName: String, arg: Any?, type: Class<*>): Any? {
    val method = ImportViewModel::class.java.getDeclaredMethod(methodName, type)
    method.isAccessible = true
    return method.invoke(viewModel, arg)
}

private fun invokeNullableLongHelper(viewModel: ImportViewModel, methodName: String, anchorMillis: Long): Long? {
    val method = ImportViewModel::class.java.getDeclaredMethod(methodName, Long::class.javaPrimitiveType)
    method.isAccessible = true
    return method.invoke(viewModel, anchorMillis) as Long?
}

private fun invokeZoneIdHelper(viewModel: ImportViewModel, methodName: String, timezoneId: String?): java.time.ZoneId {
    val method = ImportViewModel::class.java.getDeclaredMethod(methodName, String::class.java)
    method.isAccessible = true
    return method.invoke(viewModel, timezoneId) as java.time.ZoneId
}

@Suppress("UNCHECKED_CAST")
private fun invokeLocalDaysHelper(
    viewModel: ImportViewModel,
    first: Long,
    last: Long,
    zoneId: java.time.ZoneId
): List<String> {
    val method = ImportViewModel::class.java.getDeclaredMethod(
        "buildAffectedLocalDays",
        Long::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        java.time.ZoneId::class.java
    )
    method.isAccessible = true
    return method.invoke(viewModel, first, last, zoneId) as List<String>
}

fun buildViewModel(
    importStatusRepository: FakeImportStatusRepository = FakeImportStatusRepository(),
    storageRepository: FakeHowfarStorageRepositoryForVm = FakeHowfarStorageRepositoryForVm(),
    measurementRepository: FakeImportMeasurementRepository = FakeImportMeasurementRepository(),
    notificationScheduler: FakeNotificationScheduler = FakeNotificationScheduler(),
    activeProfileStore: FakeActiveProfileStoreForImport = FakeActiveProfileStoreForImport(7L)
): ImportViewModel {
    val csvImporter = CsvBytesImportInteractor(
        transactionRunner = FakeImportTransactionRunner(),
        importSessionRepository = FakeImportSessionRepository(),
        measurementRepository = measurementRepository,
        settingsStore = FakeImportSettingsStore(),
        profileRepository = FakeImportProfileRepository()
    )
    val howfarImportInteractor = HowfarImportInteractor(
        storageRepository = storageRepository,
        howfarUf2Archive = FakeHowfarUf2ArchiveForVm(),
        dataParser = HowfarDataParser(),
        activeProfileStore = activeProfileStore,
        csvImporter = csvImporter
    )
    return ImportViewModel(
        importStatusRepository = importStatusRepository,
        storageRepository = storageRepository,
        howfarImportInteractor = howfarImportInteractor,
        measurementRepository = measurementRepository,
        notificationScheduler = notificationScheduler,
        activeProfileStore = activeProfileStore
    )
}

fun importSummary(
    filename: String,
    firstTimestampEpochMillis: Long,
    lastTimestampEpochMillis: Long,
    timezoneId: String = "UTC"
): ImportSummary {
    return ImportSummary(
        filename = filename,
        sourceType = ImportSourceType.ASSET,
        totalRows = 10,
        insertedRows = 10,
        rejectedRows = 0,
        firstTimestampEpochMillis = firstTimestampEpochMillis,
        lastTimestampEpochMillis = lastTimestampEpochMillis,
        timezoneId = timezoneId
    )
}

class FakeImportStatusRepository(
    private val importSampleResult: ImportResult = ImportResult.Success(
        importSummary("sample.csv", 1_780_617_600_000L, 1_780_621_200_000L)
    ),
    private val importCsvResult: ImportResult = ImportResult.Success(
        importSummary("file.csv", 1_780_617_600_000L, 1_780_621_200_000L)
    )
) : ImportStatusRepository {
    override val hasImportedData: StateFlow<Boolean> = MutableStateFlow(false)
    var lastSampleFileName: String? = null

    override suspend fun importSample(fileName: String): ImportResult {
        lastSampleFileName = fileName
        return importSampleResult
    }

    override suspend fun importCsvBytes(
        filename: String,
        bytes: ByteArray,
        sourceType: ImportSourceType
    ): ImportResult = importCsvResult
}

class FakeHowfarStorageRepositoryForVm(
    private val dataUf2Bytes: ByteArray = byteArrayOf()
) : HowfarStorageRepository {
    private val mutableState = MutableStateFlow<HowfarStorageState>(HowfarStorageState.Disconnected)
    override val state: StateFlow<HowfarStorageState> = mutableState
    var refreshCalls = 0
    var lastSetUri: Uri? = null
    var setUriCalls = 0

    override fun refresh() {
        refreshCalls += 1
    }

    override fun setDeviceTreeUri(uri: Uri?) {
        setUriCalls += 1
        lastSetUri = uri
    }

    fun emitState(state: HowfarStorageState) {
        mutableState.value = state
    }

    override fun deviceTreeUri(): Uri? = (state.value as? HowfarStorageState.Ready)?.info?.treeUri
    override suspend fun readDataUf2(examIdentifier: String?): ByteArray = dataUf2Bytes
    override suspend fun readConfigUf2(): ByteArray? = null
    override suspend fun writeConfigUf2(bytes: ByteArray) = Unit
    override fun close() = Unit
}

class FakeHowfarUf2ArchiveForVm : HowfarUf2Archive {
    override suspend fun saveLatest(profileId: Long, filename: String, bytes: ByteArray) = Unit
    override suspend fun loadLatest(profileId: Long): HowfarUf2Snapshot? = null
}

class FakeImportMeasurementRepository(
    private val deleteDayResult: Int = 0
) : MeasurementRepository {
    val deletedDays = mutableListOf<String>()
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
    ): MeasurementInsertResult = MeasurementInsertResult(0, 0)
    override suspend fun getLatestDay(profileId: Long): String? = null
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
    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = emptyFlow()
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig
    override suspend fun deleteDay(profileId: Long, localDay: String): Int {
        deletedDays += localDay
        return deleteDayResult
    }
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportResultsPackCsvs(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): ResultsPackCsvs =
        ResultsPackCsvs("", "", "", "", 0)
}

class FakeNotificationScheduler : NotificationScheduler {
    var lastSummary: ImportSummary? = null
    override fun ensureChannels() = Unit
    override suspend fun rescheduleDailyReminder() = Unit
    override suspend fun cancelDailyReminder() = Unit
    override suspend fun enqueuePostImportSummary(summary: ImportSummary) {
        lastSummary = summary
    }
}

class FakeActiveProfileStoreForImport(profileId: Long?) : ActiveProfileStore {
    private val activeProfileId = MutableStateFlow(profileId)
    override fun observeActiveProfileId(): Flow<Long?> = activeProfileId
    override suspend fun setActiveProfileId(id: Long) {
        activeProfileId.value = id
    }
}

class FakeImportTransactionRunner : ImportTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}

class FakeImportSessionRepository : ImportSessionRepository {
    override suspend fun getSessionsForProfile(profileId: Long): List<ImportSession> = emptyList()
    override suspend fun upsertSession(session: ImportSession): Long = 1L
}

class FakeImportSettingsStore : SettingsStore {
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

class FakeImportProfileRepository : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? = Profile(profileId, "Profile", 0L, "UTC", null)
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 1L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}
