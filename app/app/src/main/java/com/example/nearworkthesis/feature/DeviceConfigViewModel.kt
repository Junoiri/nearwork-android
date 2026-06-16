package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.device.DeviceConnectionState
import com.example.nearworkthesis.domain.device.DeviceSettings
import com.example.nearworkthesis.domain.device.DeviceSettingsDefaults
import com.example.nearworkthesis.domain.device.DeviceSettingsField
import com.example.nearworkthesis.domain.device.DeviceSettingsValidator
import com.example.nearworkthesis.domain.device.DeviceTimeMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceConfigForm(
    val samplingIntervalSeconds: Int,
    val autoShutdownMinutes: Int,
    val lowLightLuxThreshold: Int,
    val enableLowPowerMode: Boolean,
    val deviceTimeMode: DeviceTimeMode
) {
    fun toSettings(): DeviceSettings =
        DeviceSettings(
            samplingIntervalSeconds = samplingIntervalSeconds,
            autoShutdownMinutes = autoShutdownMinutes,
            lowLightLuxThreshold = lowLightLuxThreshold,
            enableLowPowerMode = enableLowPowerMode,
            deviceTimeMode = deviceTimeMode
        )

    companion object {
        fun from(settings: DeviceSettings): DeviceConfigForm =
            DeviceConfigForm(
                samplingIntervalSeconds = settings.samplingIntervalSeconds,
                autoShutdownMinutes = settings.autoShutdownMinutes,
                lowLightLuxThreshold = settings.lowLightLuxThreshold,
                enableLowPowerMode = settings.enableLowPowerMode,
                deviceTimeMode = settings.deviceTimeMode
            )
    }
}

sealed interface DeviceConfigUiState {
    data object Loading : DeviceConfigUiState
    data class Ready(
        val form: DeviceConfigForm,
        val validationErrors: Map<DeviceSettingsField, String>,
        val isBusy: Boolean
    ) : DeviceConfigUiState

    data class Error(
        val message: String,
        val form: DeviceConfigForm?,
        val validationErrors: Map<DeviceSettingsField, String>
    ) : DeviceConfigUiState
}

sealed interface DeviceConfigEvent {
    data class ShowSnackbar(val message: String) : DeviceConfigEvent
    data class LaunchSaveConfig(val filename: String, val bytes: ByteArray) : DeviceConfigEvent
}

class DeviceConfigViewModel(
    private val repository: DeviceConfigRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    val connectionState: StateFlow<DeviceConnectionState> =
        repository.observeConnectionState()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeviceConnectionState.Disconnected)

    private val _uiState = MutableStateFlow<DeviceConfigUiState>(DeviceConfigUiState.Loading)
    val uiState: StateFlow<DeviceConfigUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DeviceConfigEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val hasStarted = AtomicBoolean(false)

    init {
        refreshFromDevice()
    }

    fun refreshFromDevice() {
        if (hasStarted.getAndSet(true)) {
            // still allow manual refresh; no-op here
        }
        setBusy(true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.readSettings() }
            result.fold(
                onSuccess = { settings ->
                    val form = DeviceConfigForm.from(settings)
                    _uiState.value = DeviceConfigUiState.Ready(
                        form = form,
                        validationErrors = DeviceSettingsValidator.validate(form.toSettings()),
                        isBusy = false
                    )
                },
                onFailure = { t ->
                    val defaults = DeviceConfigForm.from(DeviceSettingsDefaults.defaults)
                    _uiState.value = DeviceConfigUiState.Error(
                        message = t.message ?: "Unable to read device settings.",
                        form = defaults,
                        validationErrors = DeviceSettingsValidator.validate(defaults.toSettings())
                    )
                }
            )
        }
    }

    fun refreshConnection() {
        repository.refreshConnection()
    }

    fun updateSamplingIntervalSeconds(value: Int) = updateForm { it.copy(samplingIntervalSeconds = value) }
    fun updateAutoShutdownMinutes(value: Int) = updateForm { it.copy(autoShutdownMinutes = value) }
    fun updateLowLightLuxThreshold(value: Int) = updateForm { it.copy(lowLightLuxThreshold = value) }
    fun updateLowPowerMode(enabled: Boolean) = updateForm { it.copy(enableLowPowerMode = enabled) }
    fun updateTimeMode(mode: DeviceTimeMode) = updateForm { it.copy(deviceTimeMode = mode) }

    fun applySettings() {
        val current = (_uiState.value as? DeviceConfigUiState.Ready) ?: return
        if (current.isBusy) return

        val errors = DeviceSettingsValidator.validate(current.form.toSettings())
        if (errors.isNotEmpty()) {
            _uiState.value = current.copy(validationErrors = errors)
            return
        }

        _uiState.value = current.copy(isBusy = true)
        viewModelScope.launch {
            val writeResult = withContext(ioDispatcher) { repository.writeSettings(current.form.toSettings()) }
            writeResult.fold(
                onSuccess = {
                    _events.tryEmit(DeviceConfigEvent.ShowSnackbar("Settings applied"))
                    val updated = current.copy(isBusy = false)
                    _uiState.value = updated
                },
                onFailure = { t ->
                    _uiState.value = DeviceConfigUiState.Error(
                        message = t.message ?: "Unable to apply settings.",
                        form = current.form,
                        validationErrors = current.validationErrors
                    )
                }
            )
        }
    }


    // File-based path: build optoconf.uf2 and let the user save it anywhere.
    fun exportConfigUf2() {
        val current = (_uiState.value as? DeviceConfigUiState.Ready) ?: return
        if (current.isBusy) return

        val errors = DeviceSettingsValidator.validate(current.form.toSettings())
        if (errors.isNotEmpty()) {
            _uiState.value = current.copy(validationErrors = errors)
            return
        }

        _uiState.value = current.copy(isBusy = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.buildConfigUf2(current.form.toSettings()) }
            result.fold(
                onSuccess = { bytes ->
                    _uiState.value = current.copy(isBusy = false)
                    _events.tryEmit(DeviceConfigEvent.LaunchSaveConfig(filename = "optoconf.uf2", bytes = bytes))
                },
                onFailure = { t ->
                    _uiState.value = DeviceConfigUiState.Error(
                        message = t.message ?: "Unable to build config UF2.",
                        form = current.form,
                        validationErrors = current.validationErrors
                    )
                }
            )
        }
    }

    fun clearDeviceData() {
        val current = (_uiState.value as? DeviceConfigUiState.Ready) ?: return
        if (current.isBusy) return

        val errors = DeviceSettingsValidator.validate(current.form.toSettings())
        if (errors.isNotEmpty()) {
            _uiState.value = current.copy(validationErrors = errors)
            return
        }

        _uiState.value = current.copy(isBusy = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.clearDeviceData(current.form.toSettings()) }
            result.fold(
                onSuccess = {
                    _uiState.value = current.copy(isBusy = false)
                    _events.tryEmit(
                        DeviceConfigEvent.ShowSnackbar(
                            "Clear-data flag written. Disconnect the device to apply."
                        )
                    )
                },
                onFailure = { t ->
                    _uiState.value = DeviceConfigUiState.Error(
                        message = t.message ?: "Unable to write clear-data config.",
                        form = current.form,
                        validationErrors = current.validationErrors
                    )
                }
            )
        }
    }

    fun resetToDefaults() {
        val currentForm = (uiState.value as? DeviceConfigUiState.Ready)?.form
            ?: DeviceConfigForm.from(DeviceSettingsDefaults.defaults)

        setBusy(true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.resetToDefaults() }
            result.fold(
                onSuccess = { settings ->
                    val form = DeviceConfigForm.from(settings)
                    _events.tryEmit(DeviceConfigEvent.ShowSnackbar("Reset to defaults"))
                    _uiState.value = DeviceConfigUiState.Ready(
                        form = form,
                        validationErrors = DeviceSettingsValidator.validate(form.toSettings()),
                        isBusy = false
                    )
                },
                onFailure = { t ->
                    _uiState.value = DeviceConfigUiState.Error(
                        message = t.message ?: "Unable to reset settings.",
                        form = currentForm,
                        validationErrors = DeviceSettingsValidator.validate(currentForm.toSettings())
                    )
                }
            )
        }
    }

    fun retryLastError() {
        refreshFromDevice()
    }

    private fun updateForm(transform: (DeviceConfigForm) -> DeviceConfigForm) {
        val current = _uiState.value
        val ready = current as? DeviceConfigUiState.Ready
        val form = ready?.form ?: (current as? DeviceConfigUiState.Error)?.form ?: DeviceConfigForm.from(DeviceSettingsDefaults.defaults)
        val updated = transform(form)
        val errors = DeviceSettingsValidator.validate(updated.toSettings())

        val isBusy = (ready?.isBusy == true)
        _uiState.value = DeviceConfigUiState.Ready(form = updated, validationErrors = errors, isBusy = isBusy)
    }

    private fun setBusy(busy: Boolean) {
        val current = _uiState.value
        if (current is DeviceConfigUiState.Ready) {
            _uiState.value = current.copy(isBusy = busy)
            return
        }
        if (current is DeviceConfigUiState.Error) {
            val form = current.form ?: DeviceConfigForm.from(DeviceSettingsDefaults.defaults)
            _uiState.value = DeviceConfigUiState.Ready(
                form = form,
                validationErrors = current.validationErrors,
                isBusy = busy
            )
            return
        }
        if (current is DeviceConfigUiState.Loading && busy) return
        if (current is DeviceConfigUiState.Loading && !busy) return
    }

    companion object {
        fun factory(
            repository: DeviceConfigRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceConfigViewModel(repository = repository) as T
                }
            }
        }
    }
}






