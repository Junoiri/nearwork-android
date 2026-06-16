package com.example.nearworkthesis.feature

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportStatusRepository
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.importing.howfar.HowfarImportInteractor
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageState
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportHowfarUiModel(
    val statusTitle: String,
    val statusSubtitle: String,
    val primaryAction: ImportHowfarPrimaryAction?,
    val primaryActionEnabled: Boolean
)

enum class ImportHowfarPrimaryAction {
    SelectDevice,
    ImportFromDevice,
    RetryDetection
}

enum class ImportAnchorMode {
    Auto,
    Manual
}

enum class ImportCropStartMode {
    Disabled,
    Manual
}

enum class ImportCropEndMode {
    Auto,
    Manual
}

data class ImportUiModel(
    val howfar: ImportHowfarUiModel,
    val isImporting: Boolean,
    val importingLabel: String?,
    val lastResult: ImportLastResult?,
    val anchorMode: ImportAnchorMode,
    val customAnchorMillis: Long,
    val cropStartMode: ImportCropStartMode,
    val cropStartMillis: Long,
    val cropEndMode: ImportCropEndMode,
    val cropEndMillis: Long
)

sealed interface ImportDialogState {
    data object None : ImportDialogState
    data class Error(val message: String) : ImportDialogState
}

enum class ImportOutcome {
    Success,
    NoNewData
}

data class ImportLastResult(
    val outcome: ImportOutcome,
    val summary: ImportSummary
)

class ImportViewModel(
    private val importStatusRepository: ImportStatusRepository,
    private val storageRepository: HowfarStorageRepository,
    private val howfarImportInteractor: HowfarImportInteractor,
    private val measurementRepository: MeasurementRepository,
    private val notificationScheduler: NotificationScheduler,
    private val activeProfileStore: ActiveProfileStore
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    private val _importingLabel = MutableStateFlow<String?>(null)
    private val _lastResult = MutableStateFlow<ImportLastResult?>(null)
    private val _anchorMode = MutableStateFlow(ImportAnchorMode.Auto)
    private val _customAnchorMillis = MutableStateFlow(System.currentTimeMillis())
    private val _cropStartMode = MutableStateFlow(ImportCropStartMode.Disabled)
    private val _cropStartMillis = MutableStateFlow(defaultCropStartMillis(System.currentTimeMillis()))
    private val _cropEndMode = MutableStateFlow(ImportCropEndMode.Auto)
    private val _cropEndMillis = MutableStateFlow(System.currentTimeMillis())
    val lastResult: StateFlow<ImportLastResult?> = _lastResult.asStateFlow()

    private val _dialogState = MutableStateFlow<ImportDialogState>(ImportDialogState.None)
    val dialogState: StateFlow<ImportDialogState> = _dialogState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var importJob: Job? = null

    private val howfarUi: StateFlow<ImportHowfarUiModel> = storageRepository.state
        .map { toHowfarUiModel(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toHowfarUiModel(HowfarStorageState.Disconnected))

    val uiModel: StateFlow<ImportUiModel> = combine(
        howfarUi,
        _isImporting,
        _importingLabel,
        _lastResult,
        _anchorMode,
        _customAnchorMillis,
        _cropStartMode,
        _cropStartMillis,
        _cropEndMode,
        _cropEndMillis
    ) { values ->
        val howfar = values[0] as ImportHowfarUiModel
        val importing = values[1] as Boolean
        val label = values[2] as String?
        val lastResult = values[3] as ImportLastResult?
        val anchorMode = values[4] as ImportAnchorMode
        val customAnchorMillis = values[5] as Long
        val cropStartMode = values[6] as ImportCropStartMode
        val cropStartMillis = values[7] as Long
        val cropEndMode = values[8] as ImportCropEndMode
        val cropEndMillis = values[9] as Long
        ImportUiModel(
            howfar = howfar.copy(primaryActionEnabled = howfar.primaryActionEnabled && !importing),
            isImporting = importing,
            importingLabel = label,
            lastResult = lastResult,
            anchorMode = anchorMode,
            customAnchorMillis = customAnchorMillis,
            cropStartMode = cropStartMode,
            cropStartMillis = cropStartMillis,
            cropEndMode = cropEndMode,
            cropEndMillis = cropEndMillis
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ImportUiModel(
            howfar = toHowfarUiModel(HowfarStorageState.Disconnected),
            isImporting = false,
            importingLabel = null,
            lastResult = null,
            anchorMode = ImportAnchorMode.Auto,
            customAnchorMillis = System.currentTimeMillis(),
            cropStartMode = ImportCropStartMode.Disabled,
            cropStartMillis = defaultCropStartMillis(System.currentTimeMillis()),
            cropEndMode = ImportCropEndMode.Auto,
            cropEndMillis = System.currentTimeMillis()
        )
    )

    init {
        viewModelScope.launch {
            storageRepository.state.collectLatest { state ->
                if (_isImporting.value && state is HowfarStorageState.Disconnected) {
                    importJob?.cancel()
                    _isImporting.value = false
                    _importingLabel.value = null
                    _snackbarEvents.tryEmit("HowFar storage disconnected during import.")
                    _dialogState.value = ImportDialogState.Error("HowFar storage disconnected during import.")
                }
            }
        }
    }

    fun refreshHowfar() {
        if (_isImporting.value) return
        storageRepository.refresh()
    }

    // Optional shortcut: pick the device storage once so we can auto-import UF2 files later.
    fun setDeviceTreeUri(uri: Uri?) {
        if (_isImporting.value) return
        storageRepository.setDeviceTreeUri(uri)
    }

    // Shortcut import: same file-based flow as howfar-read, but the app reads from the stored device folder.
    fun importFromHowfar() {
        if (_isImporting.value) return
        importJob?.cancel()
        val anchorMillis = resolveAnchorMillis()
        val cropStartMillis = resolveCropStartMillis(anchorMillis)
        val cropEndMillis = resolveCropEndMillis(anchorMillis)
        val forceAnchorShift = _anchorMode.value == ImportAnchorMode.Manual

        _isImporting.value = true
        _importingLabel.value = "HowFar device"
        importJob = viewModelScope.launch {
            val result = howfarImportInteractor.importFromDevice(
                anchorMillis = anchorMillis,
                cropStartMillis = cropStartMillis,
                cropEndMillis = cropEndMillis,
                forceAnchorShift = forceAnchorShift
            )
            _isImporting.value = false
            _importingLabel.value = null
            handleImportResult(result)
        }
    }


    // Manual import: user picked a UF2 file directly (explicit file path, like the CLI).
    fun importHowfarUf2File(filename: String, bytes: ByteArray) {
        if (_isImporting.value) return
        importJob?.cancel()
        val anchorMillis = resolveAnchorMillis()
        val cropStartMillis = resolveCropStartMillis(anchorMillis)
        val cropEndMillis = resolveCropEndMillis(anchorMillis)
        val forceAnchorShift = _anchorMode.value == ImportAnchorMode.Manual

        _isImporting.value = true
        _importingLabel.value = filename
        importJob = viewModelScope.launch {
            val result = runCatching {
                howfarImportInteractor.importFromUf2File(
                    filename,
                    bytes,
                    anchorMillis = anchorMillis,
                    cropStartMillis = cropStartMillis,
                    cropEndMillis = cropEndMillis,
                    forceAnchorShift = forceAnchorShift
                )
            }
                .getOrElse { ImportResult.Error(it.message ?: "Unexpected error") }
            _isImporting.value = false
            _importingLabel.value = null
            handleImportResult(result)
        }
    }

    fun importSample(option: SampleImportOption) {
        if (_isImporting.value) return
        importJob?.cancel()

        _isImporting.value = true
        _importingLabel.value = option.label
        importJob = viewModelScope.launch {
            val result = runCatching { importStatusRepository.importSample(option.fileName) }
                .getOrElse { ImportResult.Error(it.message ?: "Unexpected error") }
            _isImporting.value = false
            _importingLabel.value = null
            handleImportResult(result)
        }
    }

    fun importCsvBytes(filename: String, bytes: ByteArray, sourceType: ImportSourceType) {
        if (_isImporting.value) return
        importJob?.cancel()

        _isImporting.value = true
        _importingLabel.value = filename
        importJob = viewModelScope.launch {
            val result = runCatching { importStatusRepository.importCsvBytes(filename, bytes, sourceType) }
                .getOrElse { ImportResult.Error(it.message ?: "Unexpected error") }
            _isImporting.value = false
            _importingLabel.value = null
            handleImportResult(result)
        }
    }

    fun dismissDialog() {
        _dialogState.value = ImportDialogState.None
    }

    fun clearLastResult() {
        _lastResult.value = null
    }

    fun setAnchorMode(mode: ImportAnchorMode) {
        _anchorMode.value = mode
        if (_cropStartMode.value == ImportCropStartMode.Disabled) {
            _cropStartMillis.value = defaultCropStartMillis(previewAnchorMillis())
        }
    }

    fun setCustomAnchorMillis(anchorMillis: Long) {
        _customAnchorMillis.value = anchorMillis
        if (_cropEndMode.value == ImportCropEndMode.Auto) {
            _cropEndMillis.value = anchorMillis
        }
        if (_cropStartMode.value == ImportCropStartMode.Disabled) {
            _cropStartMillis.value = defaultCropStartMillis(anchorMillis)
        }
    }

    fun setCropStartMode(mode: ImportCropStartMode) {
        if (mode == ImportCropStartMode.Manual && _cropStartMode.value == ImportCropStartMode.Disabled) {
            _cropStartMillis.value = defaultCropStartMillis(previewAnchorMillis())
        }
        _cropStartMode.value = mode
    }

    fun setCropStartMillis(cropStartMillis: Long) {
        _cropStartMillis.value = cropStartMillis
    }

    fun setCropEndMode(mode: ImportCropEndMode) {
        if (mode == ImportCropEndMode.Auto) {
            _cropEndMillis.value = previewAnchorMillis()
        }
        _cropEndMode.value = mode
    }

    fun setCropEndMillis(cropEndMillis: Long) {
        _cropEndMillis.value = cropEndMillis
    }

    fun deleteImportedDays() {
        val last = _lastResult.value ?: return
        if (last.outcome != ImportOutcome.Success) return

        val firstTimestamp = last.summary.firstTimestampEpochMillis ?: return
        val lastTimestamp = last.summary.lastTimestampEpochMillis ?: return

        viewModelScope.launch {
            val profileId = activeProfileStore.observeActiveProfileId().firstOrNull()
            if (profileId == null) {
                _snackbarEvents.tryEmit("No active profile selected.")
                return@launch
            }

            val zoneId = resolveZoneId(last.summary.timezoneId)
            val affectedDays = buildAffectedLocalDays(firstTimestamp, lastTimestamp, zoneId)
            val deletedRows = affectedDays.sumOf { localDay ->
                runCatching { measurementRepository.deleteDay(profileId, localDay) }.getOrDefault(0)
            }
            if (deletedRows > 0) {
                _lastResult.value = null
                _snackbarEvents.tryEmit("Deleted imported data for ${affectedDays.size} day(s).")
            } else {
                _snackbarEvents.tryEmit("No imported data was deleted.")
            }
        }
    }

    private fun handleImportResult(result: ImportResult) {
        when (result) {
            is ImportResult.Success -> {
                _lastResult.value = ImportLastResult(outcome = ImportOutcome.Success, summary = result.summary)
                _dialogState.value = ImportDialogState.None
                viewModelScope.launch { notificationScheduler.enqueuePostImportSummary(result.summary) }
            }
            is ImportResult.NoNewData -> {
                _lastResult.value = ImportLastResult(outcome = ImportOutcome.NoNewData, summary = result.summary)
                _dialogState.value = ImportDialogState.None
            }
            is ImportResult.Error -> {
                _dialogState.value = ImportDialogState.Error(result.message)
            }
        }
    }

    private fun toHowfarUiModel(state: HowfarStorageState): ImportHowfarUiModel {
        return when (state) {
            HowfarStorageState.Disconnected -> ImportHowfarUiModel(
                statusTitle = "HowFar: Storage not selected",
                statusSubtitle = "Select the HowFar device storage (usually named OPTODATA).",
                primaryAction = ImportHowfarPrimaryAction.SelectDevice,
                primaryActionEnabled = true
            )
            is HowfarStorageState.Ready -> ImportHowfarUiModel(
                statusTitle = "HowFar: Ready",
                statusSubtitle = formatDevice(state.info),
                primaryAction = ImportHowfarPrimaryAction.ImportFromDevice,
                primaryActionEnabled = true
            )
            is HowfarStorageState.Error -> ImportHowfarUiModel(
                statusTitle = "HowFar: Not connected",
                statusSubtitle = "Storage not accessible. Connect HowFar and reselect OPTODATA.",
                primaryAction = ImportHowfarPrimaryAction.SelectDevice,
                primaryActionEnabled = true
            )
        }
    }

    private fun formatDevice(info: com.example.nearworkthesis.importing.howfar.HowfarDeviceInfo): String {
        val name = info.displayName ?: "OPTODATA"
        val hint = if (name.equals("OPTODATA", ignoreCase = true)) "" else " (expected OPTODATA)"
        return "Storage: ${name}${hint}"
    }

    private fun resolveAnchorMillis(): Long {
        return when (_anchorMode.value) {
            ImportAnchorMode.Auto -> System.currentTimeMillis()
            ImportAnchorMode.Manual -> _customAnchorMillis.value
        }
    }

    private fun resolveCropStartMillis(anchorMillis: Long): Long? {
        return when (_cropStartMode.value) {
            ImportCropStartMode.Disabled -> null
            ImportCropStartMode.Manual -> _cropStartMillis.value.coerceAtMost(anchorMillis)
        }
    }

    private fun resolveCropEndMillis(anchorMillis: Long): Long? {
        return when (_cropEndMode.value) {
            ImportCropEndMode.Auto -> null
            ImportCropEndMode.Manual -> _cropEndMillis.value.coerceAtMost(anchorMillis)
        }
    }

    private fun previewAnchorMillis(): Long {
        return when (_anchorMode.value) {
            ImportAnchorMode.Auto -> System.currentTimeMillis()
            ImportAnchorMode.Manual -> _customAnchorMillis.value
        }
    }

    private fun resolveZoneId(timezoneId: String?): ZoneId {
        if (timezoneId.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(timezoneId) }.getOrElse { ZoneId.systemDefault() }
    }

    private fun buildAffectedLocalDays(
        firstTimestampEpochMillis: Long,
        lastTimestampEpochMillis: Long,
        zoneId: ZoneId
    ): List<String> {
        val startDate = Instant.ofEpochMilli(firstTimestampEpochMillis).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(lastTimestampEpochMillis).atZone(zoneId).toLocalDate()
        if (endDate.isBefore(startDate)) return listOf(startDate.toString())

        val days = ArrayList<String>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            days.add(current.toString())
            current = current.plusDays(1)
        }
        return days
    }

    companion object {
        private const val DEFAULT_CROP_WINDOW_MILLIS = 8L * 60L * 60L * 1000L

        private fun defaultCropStartMillis(anchorMillis: Long): Long {
            return anchorMillis - DEFAULT_CROP_WINDOW_MILLIS
        }

        fun factory(
            importStatusRepository: ImportStatusRepository,
            storageRepository: HowfarStorageRepository,
            howfarImportInteractor: HowfarImportInteractor,
            measurementRepository: MeasurementRepository,
            notificationScheduler: NotificationScheduler,
            activeProfileStore: ActiveProfileStore
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImportViewModel(
                        importStatusRepository = importStatusRepository,
                        storageRepository = storageRepository,
                        howfarImportInteractor = howfarImportInteractor,
                        measurementRepository = measurementRepository,
                        notificationScheduler = notificationScheduler,
                        activeProfileStore = activeProfileStore
                    ) as T
                }
            }
        }
    }
}










