package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.export.ResultsPackCsvBuilder
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.importing.howfar.HowfarUf2Archive
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class ExportType {
    RawMeasurements,
    DailySummaries,
    AnalysisReport,
    ResultsPack,
    HowfarUf2
}

enum class ExportRangePreset {
    Last1Day,
    Last7Days,
    AllAvailable,
    Custom
}

data class ExportUiModel(
    val availableDaysAsc: List<String>,
    val exportType: ExportType,
    val preset: ExportRangePreset,
    val customStartDay: String?,
    val customEndDay: String?
) {
    fun resolvedRangeDays(): Pair<String?, String?> {
        if (availableDaysAsc.isEmpty()) return null to null
        return when (preset) {
            ExportRangePreset.AllAvailable -> availableDaysAsc.first() to availableDaysAsc.last()
            ExportRangePreset.Last1Day -> availableDaysAsc.takeLast(1).first() to availableDaysAsc.last()
            ExportRangePreset.Last7Days -> {
                val last = availableDaysAsc.takeLast(7)
                last.first() to last.last()
            }
            ExportRangePreset.Custom -> customStartDay to customEndDay
        }
    }
}

sealed interface ExportUiState {
    data object Loading : ExportUiState
    data object Empty : ExportUiState
    data class Ready(val model: ExportUiModel) : ExportUiState
    data class Exporting(val model: ExportUiModel) : ExportUiState
    data class Success(val model: ExportUiModel, val filename: String) : ExportUiState
    data class Error(val model: ExportUiModel?, val message: String) : ExportUiState
}

sealed interface ExportEvent {
    data class LaunchCreateDocument(val filename: String, val csv: String) : ExportEvent
    data class LaunchCreateZip(val filename: String, val bytes: ByteArray) : ExportEvent
    data class LaunchCreateBinary(val filename: String, val bytes: ByteArray) : ExportEvent
}
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModel(
    private val measurementRepository: MeasurementRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val howfarUf2Archive: HowfarUf2Archive
) : ViewModel() {

    private val _events = MutableSharedFlow<ExportEvent>()
    val events = _events.asSharedFlow()

    private val activeProfileId: StateFlow<Long?> =
        activeProfileStore.observeActiveProfileId()
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val availableDaysAsc: StateFlow<List<String>> =
        activeProfileId
            .filterNotNull()
            .flatMapLatest { profileId ->
                measurementRepository.observeAvailableDays(profileId)
            }
            .map { days -> days.sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exportType = MutableStateFlow(ExportType.RawMeasurements)
    private val _preset = MutableStateFlow(ExportRangePreset.Last7Days)
    private val _customStartDay = MutableStateFlow<String?>(null)
    private val _customEndDay = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Loading)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            availableDaysAsc.collect { days ->
                if (days.isEmpty()) {
                    _uiState.value = ExportUiState.Empty
                } else {
                    val model = ExportUiModel(
                        availableDaysAsc = days,
                        exportType = _exportType.value,
                        preset = _preset.value,
                        customStartDay = _customStartDay.value ?: days.first(),
                        customEndDay = _customEndDay.value ?: days.last()
                    )
                    _customStartDay.value = model.customStartDay
                    _customEndDay.value = model.customEndDay
                    if (_uiState.value is ExportUiState.Exporting) return@collect
                    _uiState.value = ExportUiState.Ready(model)
                }
            }
        }
    }

    fun setExportType(type: ExportType) {
        _exportType.value = type
        updateReadyModel()
    }

    fun setPreset(preset: ExportRangePreset) {
        _preset.value = preset
        updateReadyModel()
    }

    fun setCustomStartDay(day: String) {
        _customStartDay.value = day
        updateReadyModel()
    }

    fun setCustomEndDay(day: String) {
        _customEndDay.value = day
        updateReadyModel()
    }

    fun dismissSuccess() {
        updateReadyModel()
    }

    fun retry() {
        updateReadyModel()
    }

    fun export() {
        val current = (_uiState.value as? ExportUiState.Ready)?.model
            ?: (_uiState.value as? ExportUiState.Error)?.model
            ?: return

        val profileId = activeProfileId.value
        if (profileId == null) {
            _uiState.value = ExportUiState.Error(model = current, message = "No active profile selected.")
            return
        }

        val (startDayRaw, endDayRaw) = current.resolvedRangeDays()
        if (current.preset == ExportRangePreset.Custom && (startDayRaw.isNullOrBlank() || endDayRaw.isNullOrBlank())) {
            _uiState.value = ExportUiState.Error(model = current, message = "Select both a start and end day.")
            return
        }

        val days = current.availableDaysAsc
        val startDayFallback = startDayRaw ?: days.first()
        val endDayFallback = endDayRaw ?: days.last()
        val (startDay, endDay) = if (startDayFallback <= endDayFallback) {
            startDayFallback to endDayFallback
        } else {
            endDayFallback to startDayFallback
        }
        _uiState.value = ExportUiState.Exporting(current)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (current.exportType) {
                    ExportType.RawMeasurements -> ExportPayload.Csv(
                        measurementRepository.exportRawCsv(profileId = profileId, startDay = startDay, endDay = endDay)
                    )
                    ExportType.DailySummaries -> ExportPayload.Csv(
                        measurementRepository.exportDailySummaryCsv(profileId = profileId, startDay = startDay, endDay = endDay)
                    )
                    ExportType.AnalysisReport -> {
                        val analysisConfig = currentAnalysisConfig(profileId)
                        ExportPayload.Csv(
                            measurementRepository.exportAnalysisReportCsv(
                                profileId = profileId,
                                startDay = startDay,
                                endDay = endDay,
                                config = analysisConfig
                            )
                        )
                    }
                    ExportType.ResultsPack -> {
                        val analysisConfig = currentAnalysisConfig(profileId)
                        val csvs = measurementRepository.exportResultsPackCsvs(
                            profileId = profileId,
                            startDay = startDay,
                            endDay = endDay,
                            config = analysisConfig
                        )
                        if (csvs.daysWithSamples <= 0) {
                            _uiState.value = ExportUiState.Empty
                            return@launch
                        }
                        validateResultsPackCsvs(csvs)
                        ExportPayload.Zip(buildResultsPackZip(csvs))
                    }
                    ExportType.HowfarUf2 -> {
                        val snapshot = howfarUf2Archive.loadLatest(profileId)
                            ?: throw IllegalStateException("No HowFar UF2 import found. Import from device first.")
                        ExportPayload.Binary(filename = snapshot.filename, bytes = snapshot.bytes)
                    }
                }
            }.onSuccess { csv ->
                val (finalStart, finalEnd) = resolveRangeForFilename(current)
                if (csv is ExportPayload.Zip) {
                    val filename = "nearwork_results_pack_${finalStart}_${finalEnd}.zip"
                    _events.emit(ExportEvent.LaunchCreateZip(filename = filename, bytes = csv.bytes))
                } else if (csv is ExportPayload.Binary) {
                    _events.emit(ExportEvent.LaunchCreateBinary(filename = csv.filename, bytes = csv.bytes))
                } else {
                    val typeSlug = when (current.exportType) {
                        ExportType.RawMeasurements -> "raw"
                        ExportType.DailySummaries -> "daily"
                        ExportType.AnalysisReport -> "analysis"
                        ExportType.ResultsPack -> "results_pack"
                        ExportType.HowfarUf2 -> "howfar_uf2"
                    }
                    val filename = "nearwork_${typeSlug}_${finalStart}_${finalEnd}.csv"
                    _events.emit(ExportEvent.LaunchCreateDocument(filename = filename, csv = (csv as ExportPayload.Csv).value))
                }
            }.onFailure { t ->
                _uiState.value = ExportUiState.Error(
                    model = current,
                    message = t.message ?: "Unable to generate CSV."
                )
            }
        }
    }
    fun onSaved(filename: String) {
        val model = (_uiState.value as? ExportUiState.Exporting)?.model
            ?: (_uiState.value as? ExportUiState.Ready)?.model
            ?: return
        _uiState.value = ExportUiState.Success(model = model, filename = filename)
    }

    fun onSaveFailed(message: String) {
        val model = (_uiState.value as? ExportUiState.Exporting)?.model
            ?: (_uiState.value as? ExportUiState.Ready)?.model
        _uiState.value = ExportUiState.Error(model = model, message = message)
    }

    fun onSaveCancelled() {
        updateReadyModel()
    }

    private fun updateReadyModel() {
        val days = availableDaysAsc.value
        if (days.isEmpty()) {
            _uiState.value = ExportUiState.Empty
            return
        }
        val model = ExportUiModel(
            availableDaysAsc = days,
            exportType = _exportType.value,
            preset = _preset.value,
            customStartDay = _customStartDay.value ?: days.first(),
            customEndDay = _customEndDay.value ?: days.last()
        )
        _uiState.value = ExportUiState.Ready(model)
    }

    private suspend fun currentAnalysisConfig(profileId: Long): AnalysisConfig =
        measurementRepository.getCurrentAnalysisConfig(profileId)

    private fun resolveRangeForFilename(model: ExportUiModel): Pair<String, String> {
        val days = model.availableDaysAsc
        val (startRaw, endRaw) = model.resolvedRangeDays()
        val start = startRaw ?: days.first()
        val end = endRaw ?: days.last()
        return if (start <= end) start to end else end to start
    }


    companion object {
        fun factory(
            measurementRepository: MeasurementRepository,
            activeProfileStore: ActiveProfileStore,
            howfarUf2Archive: HowfarUf2Archive
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ExportViewModel(
                        measurementRepository = measurementRepository,
                        activeProfileStore = activeProfileStore,
                        howfarUf2Archive = howfarUf2Archive
                    ) as T
                }
            }
        }
    }
}

private sealed interface ExportPayload {
    data class Csv(val value: String) : ExportPayload
    data class Zip(val bytes: ByteArray) : ExportPayload
    data class Binary(val filename: String, val bytes: ByteArray) : ExportPayload
}
private fun buildResultsPackZip(csvs: com.example.nearworkthesis.domain.export.ResultsPackCsvs): ByteArray {
    validateResultsPackCsvs(csvs)
    val output = ByteArrayOutputStream()
    try {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(ResultsPackCsvBuilder.manifestFilename))
            zip.write(csvs.manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ResultsPackCsvBuilder.dailyFilename))
            zip.write(csvs.dailyResultsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ResultsPackCsvBuilder.sessionsFilename))
            zip.write(csvs.sessionsResultsCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ResultsPackCsvBuilder.importQualityFilename))
            zip.write(csvs.importQualityCsv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    } catch (t: Throwable) {
        throw IllegalStateException("Failed to build results pack.", t)
    }
    return output.toByteArray()
}

private fun validateResultsPackCsvs(csvs: com.example.nearworkthesis.domain.export.ResultsPackCsvs) {
    require(hasHeader(csvs.dailyResultsCsv)) { "Daily results CSV is missing its header." }
        require(hasHeader(csvs.sessionsResultsCsv)) { "Sample results CSV is missing its header." }
    require(hasHeader(csvs.importQualityCsv)) { "Import quality CSV is missing its header." }
}

private fun hasHeader(csv: String): Boolean {
    return csv.lineSequence().firstOrNull()?.isNotBlank() == true
}







