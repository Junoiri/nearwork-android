package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val lowLightThresholdLux: Int = SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX,
    val nearworkDistanceThresholdCm: Int = SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM,
    val breakGapSeconds: Int = SettingsDefaults.BREAK_GAP_SECONDS,
    val minSessionDurationSeconds: Int = SettingsDefaults.MIN_SESSION_DURATION_SECONDS,
    val closeDistanceThresholdCm: Int = SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM,
    val extremeCloseThresholdCm: Int = SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM,
    val replaceAlsSingleSampleSpikes: Boolean = SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES,
    val alsSpikeThresholdLux: Double = SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX,
    val showDebugOverlay: Boolean = SettingsDefaults.SHOW_DEBUG_OVERLAY,
    val lastDemoProfileId: Long? = null,
    val dailyReminderEnabled: Boolean = SettingsDefaults.DAILY_REMINDER_ENABLED,
    val dailyReminderTimeLocal: String = SettingsDefaults.DAILY_REMINDER_TIME_LOCAL,
    val postImportNotificationEnabled: Boolean = SettingsDefaults.POST_IMPORT_NOTIFICATION_ENABLED,
    val duplicateResolutionPolicy: DuplicateResolutionPolicy = SettingsDefaults.DUPLICATE_RESOLUTION_POLICY
)

class SettingsViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {

    private data class SettingsUiThresholdState(
        val lowLightThresholdLux: Int,
        val nearworkDistanceThresholdCm: Int,
        val breakGapSeconds: Int,
        val minSessionDurationSeconds: Int,
        val closeDistanceThresholdCm: Int,
        val extremeCloseThresholdCm: Int,
        val replaceAlsSingleSampleSpikes: Boolean,
        val alsSpikeThresholdLux: Double
    )

    private data class SettingsUiPreferenceState(
        val showDebugOverlay: Boolean,
        val lastDemoProfileId: Long?,
        val dailyReminderEnabled: Boolean,
        val dailyReminderTimeLocal: String,
        val postImportNotificationEnabled: Boolean,
        val duplicateResolutionPolicy: DuplicateResolutionPolicy
    )

    private val thresholdState =
        combine(
            settingsStore.observeLowLightThresholdLux(),
            settingsStore.observeNearworkDistanceThresholdCm(),
            settingsStore.observeBreakGapSeconds()
        ) { lowLight, nearworkThreshold, breakGap ->
            arrayOf(lowLight, nearworkThreshold, breakGap)
        }.combine(
            combine(
                settingsStore.observeMinSessionDurationSeconds(),
                settingsStore.observeCloseDistanceThresholdCm(),
                settingsStore.observeExtremeCloseThresholdCm(),
                settingsStore.observeReplaceAlsSingleSampleSpikes(),
                settingsStore.observeAlsSpikeThresholdLux()
            ) { minSessionDuration, closeDistance, extremeClose, replaceAlsSpikes, alsSpikeThreshold ->
                arrayOf(
                    minSessionDuration,
                    closeDistance,
                    extremeClose,
                    replaceAlsSpikes,
                    alsSpikeThreshold
                )
            }
        ) { thresholdCore, thresholdExtra ->
            SettingsUiThresholdState(
                lowLightThresholdLux = thresholdCore[0] as Int,
                nearworkDistanceThresholdCm = thresholdCore[1] as Int,
                breakGapSeconds = thresholdCore[2] as Int,
                minSessionDurationSeconds = thresholdExtra[0] as Int,
                closeDistanceThresholdCm = thresholdExtra[1] as Int,
                extremeCloseThresholdCm = thresholdExtra[2] as Int,
                replaceAlsSingleSampleSpikes = thresholdExtra[3] as Boolean,
                alsSpikeThresholdLux = thresholdExtra[4] as Double
            )
        }

    private val preferenceState =
        combine(
            settingsStore.observeShowDebugOverlay(),
            settingsStore.observeLastDemoProfileId(),
            settingsStore.observeDailyReminderEnabled(),
            settingsStore.observeDailyReminderTimeLocal()
        ) { showDebugOverlay, lastDemoProfileId, dailyReminderEnabled, dailyReminderTimeLocal ->
            arrayOf(showDebugOverlay, lastDemoProfileId, dailyReminderEnabled, dailyReminderTimeLocal)
        }.combine(
            combine(
                settingsStore.observePostImportNotificationEnabled(),
                settingsStore.observeDuplicateResolutionPolicy()
            ) { postImportNotificationEnabled, duplicateResolutionPolicy ->
                postImportNotificationEnabled to duplicateResolutionPolicy
            }
        ) { preferenceCore, preferenceExtra ->
            SettingsUiPreferenceState(
                showDebugOverlay = preferenceCore[0] as Boolean,
                lastDemoProfileId = preferenceCore[1] as Long?,
                dailyReminderEnabled = preferenceCore[2] as Boolean,
                dailyReminderTimeLocal = preferenceCore[3] as String,
                postImportNotificationEnabled = preferenceExtra.first,
                duplicateResolutionPolicy = preferenceExtra.second
            )
        }

    val uiState: StateFlow<SettingsUiState> =
        thresholdState.combine(preferenceState) { thresholds, preferences ->
            SettingsUiState(
                lowLightThresholdLux = thresholds.lowLightThresholdLux,
                nearworkDistanceThresholdCm = thresholds.nearworkDistanceThresholdCm,
                breakGapSeconds = thresholds.breakGapSeconds,
                minSessionDurationSeconds = thresholds.minSessionDurationSeconds,
                closeDistanceThresholdCm = thresholds.closeDistanceThresholdCm,
                extremeCloseThresholdCm = thresholds.extremeCloseThresholdCm,
                replaceAlsSingleSampleSpikes = thresholds.replaceAlsSingleSampleSpikes,
                alsSpikeThresholdLux = thresholds.alsSpikeThresholdLux,
                showDebugOverlay = preferences.showDebugOverlay,
                lastDemoProfileId = preferences.lastDemoProfileId,
                dailyReminderEnabled = preferences.dailyReminderEnabled,
                dailyReminderTimeLocal = preferences.dailyReminderTimeLocal,
                postImportNotificationEnabled = preferences.postImportNotificationEnabled,
                duplicateResolutionPolicy = preferences.duplicateResolutionPolicy
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setLowLightThresholdLux(lux: Int) {
        viewModelScope.launch { settingsStore.setLowLightThresholdLux(lux) }
    }

    fun setNearworkDistanceThresholdCm(value: Int) {
        viewModelScope.launch { settingsStore.setNearworkDistanceThresholdCm(value) }
    }

    fun setBreakGapSeconds(value: Int) {
        viewModelScope.launch { settingsStore.setBreakGapSeconds(value) }
    }

    fun setMinSessionDurationSeconds(value: Int) {
        viewModelScope.launch { settingsStore.setMinSessionDurationSeconds(value) }
    }

    fun setCloseDistanceThresholdCm(value: Int) {
        viewModelScope.launch { settingsStore.setCloseDistanceThresholdCm(value) }
    }

    fun setExtremeCloseThresholdCm(value: Int) {
        viewModelScope.launch { settingsStore.setExtremeCloseThresholdCm(value) }
    }

    fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setReplaceAlsSingleSampleSpikes(enabled) }
    }

    fun setAlsSpikeThresholdLux(value: Double) {
        viewModelScope.launch { settingsStore.setAlsSpikeThresholdLux(value) }
    }

    fun setShowDebugOverlay(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowDebugOverlay(enabled) }
    }

    fun setLastDemoProfileId(profileId: Long?) {
        viewModelScope.launch { settingsStore.setLastDemoProfileId(profileId) }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDailyReminderEnabled(enabled) }
    }

    fun setDailyReminderTimeLocal(value: String) {
        viewModelScope.launch { settingsStore.setDailyReminderTimeLocal(value) }
    }

    fun setPostImportNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setPostImportNotificationEnabled(enabled) }
    }

    fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) {
        viewModelScope.launch { settingsStore.setDuplicateResolutionPolicy(policy) }
    }

    companion object {
        const val THRESHOLD_ORDERING_ERROR =
            "Extreme close threshold must be strictly less than close distance threshold."

        fun validateThresholdOrdering(
            closeDistanceThresholdCm: Int,
            extremeCloseThresholdCm: Int
        ): String? {
            return if (extremeCloseThresholdCm >= closeDistanceThresholdCm) {
                THRESHOLD_ORDERING_ERROR
            } else {
                null
            }
        }

        fun factory(settingsStore: SettingsStore): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsStore) as T
                }
            }
        }
    }
}
