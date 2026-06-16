package com.example.nearworkthesis.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsStore(
    private val context: Context
) : SettingsStore {

    override fun observeLowLightThresholdLux(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.lowLightThresholdLux] ?: Defaults.lowLightThresholdLux
        }
    }

    override suspend fun setLowLightThresholdLux(lux: Int) {
        val clamped = lux.coerceIn(0, 50_000)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lowLightThresholdLux] = clamped
        }
    }

    override fun observeNearworkDistanceThresholdCm(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.nearworkDistanceThresholdCm] ?: Defaults.nearworkDistanceThresholdCm
        }
    }

    override suspend fun setNearworkDistanceThresholdCm(value: Int) {
        val clamped = value.coerceIn(10, 200)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.nearworkDistanceThresholdCm] = clamped
        }
    }

    override fun observeBreakGapSeconds(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.breakGapSeconds] ?: Defaults.breakGapSeconds
        }
    }

    override suspend fun setBreakGapSeconds(value: Int) {
        val clamped = value.coerceIn(1, 60 * 60)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.breakGapSeconds] = clamped
        }
    }

    override fun observeMinSessionDurationSeconds(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.minSessionDurationSeconds] ?: Defaults.minSessionDurationSeconds
        }
    }

    override suspend fun setMinSessionDurationSeconds(value: Int) {
        val clamped = value.coerceIn(1, 24 * 60 * 60)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.minSessionDurationSeconds] = clamped
        }
    }

    override fun observeCloseDistanceThresholdCm(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.closeDistanceThresholdCm] ?: Defaults.closeDistanceThresholdCm
        }
    }

    override suspend fun setCloseDistanceThresholdCm(value: Int) {
        val clamped = value.coerceIn(10, 200)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.closeDistanceThresholdCm] = clamped
            val currentExtreme = prefs[Keys.extremeCloseThresholdCm] ?: Defaults.extremeCloseThresholdCm
            if (currentExtreme >= clamped) {
                prefs[Keys.extremeCloseThresholdCm] = clamped - 1
            }
        }
    }

    override fun observeExtremeCloseThresholdCm(): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.extremeCloseThresholdCm] ?: Defaults.extremeCloseThresholdCm
        }
    }

    override suspend fun setExtremeCloseThresholdCm(value: Int) {
        val clamped = value.coerceIn(10, 200)
        context.settingsDataStore.edit { prefs ->
            val currentClose = prefs[Keys.closeDistanceThresholdCm] ?: Defaults.closeDistanceThresholdCm
            prefs[Keys.extremeCloseThresholdCm] =
                if (clamped >= currentClose) currentClose - 1 else clamped
        }
    }

    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.replaceAlsSingleSampleSpikes] ?: Defaults.replaceAlsSingleSampleSpikes
        }
    }

    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.replaceAlsSingleSampleSpikes] = enabled
        }
    }

    override fun observeAlsSpikeThresholdLux(): Flow<Double> {
        return context.settingsDataStore.data.map { prefs ->
            (prefs[Keys.alsSpikeThresholdLux] ?: Defaults.alsSpikeThresholdLux).toDouble()
        }
    }

    override suspend fun setAlsSpikeThresholdLux(value: Double) {
        val clamped = value.coerceIn(0.0, 50_000.0).toFloat()
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.alsSpikeThresholdLux] = clamped
        }
    }

    override fun observeShowDebugOverlay(): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.showDebugOverlay] ?: Defaults.showDebugOverlay
        }
    }

    override suspend fun setShowDebugOverlay(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.showDebugOverlay] = enabled
        }
    }

    override fun observeLastDemoProfileId(): Flow<Long?> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.lastDemoProfileId]
        }
    }

    override suspend fun setLastDemoProfileId(profileId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(Keys.lastDemoProfileId)
            } else {
                prefs[Keys.lastDemoProfileId] = profileId
            }
        }
    }

    override fun observeDailyReminderEnabled(): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.dailyReminderEnabled] ?: Defaults.dailyReminderEnabled
        }
    }

    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.dailyReminderEnabled] = enabled
        }
    }

    override fun observeDailyReminderTimeLocal(): Flow<String> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.dailyReminderTimeLocal] ?: Defaults.dailyReminderTimeLocal
        }
    }

    override suspend fun setDailyReminderTimeLocal(value: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.dailyReminderTimeLocal] = normalizeTime(value)
        }
    }

    override fun observePostImportNotificationEnabled(): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[Keys.postImportNotificationEnabled] ?: Defaults.postImportNotificationEnabled
        }
    }

    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.postImportNotificationEnabled] = enabled
        }
    }

    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> {
        return context.settingsDataStore.data.map { prefs ->
            val raw = prefs[Keys.duplicateResolutionPolicy] ?: Defaults.duplicateResolutionPolicy
            DuplicateResolutionPolicy.fromStorage(raw)
        }
    }

    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.duplicateResolutionPolicy] = policy.storageValue
        }
    }

    private fun normalizeTime(value: String): String {
        val parsed = runCatching { LocalTime.parse(value, TIME_FORMAT) }.getOrNull()
        return parsed?.format(TIME_FORMAT) ?: Defaults.dailyReminderTimeLocal
    }

    private object Keys {
        val lowLightThresholdLux = intPreferencesKey("low_light_threshold_lux")
        val nearworkDistanceThresholdCm = intPreferencesKey("nearwork_distance_threshold_cm")
        val breakGapSeconds = intPreferencesKey("break_gap_seconds")
        val minSessionDurationSeconds = intPreferencesKey("min_session_duration_seconds")
        val closeDistanceThresholdCm = intPreferencesKey("close_distance_threshold_cm")
        val extremeCloseThresholdCm = intPreferencesKey("extreme_close_threshold_cm")
        val replaceAlsSingleSampleSpikes = booleanPreferencesKey("replace_als_single_sample_spikes")
        val alsSpikeThresholdLux = floatPreferencesKey("als_spike_threshold_lux")
        val showDebugOverlay = booleanPreferencesKey("show_debug_overlay")
        val lastDemoProfileId = longPreferencesKey("last_demo_profile_id")
        val dailyReminderEnabled = booleanPreferencesKey("daily_reminder_enabled")
        val dailyReminderTimeLocal = stringPreferencesKey("daily_reminder_time_local")
        val postImportNotificationEnabled = booleanPreferencesKey("post_import_notification_enabled")
        val duplicateResolutionPolicy = stringPreferencesKey("duplicate_resolution_policy")
    }

    object Defaults {
        val lowLightThresholdLux: Int = SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX
        val nearworkDistanceThresholdCm: Int = SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM
        val breakGapSeconds: Int = SettingsDefaults.BREAK_GAP_SECONDS
        val minSessionDurationSeconds: Int = SettingsDefaults.MIN_SESSION_DURATION_SECONDS
        val closeDistanceThresholdCm: Int = SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM
        val extremeCloseThresholdCm: Int = SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM
        val replaceAlsSingleSampleSpikes: Boolean = SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES
        val alsSpikeThresholdLux: Float = SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX.toFloat()
        val showDebugOverlay: Boolean = SettingsDefaults.SHOW_DEBUG_OVERLAY
        val dailyReminderEnabled: Boolean = SettingsDefaults.DAILY_REMINDER_ENABLED
        val dailyReminderTimeLocal: String = SettingsDefaults.DAILY_REMINDER_TIME_LOCAL
        val postImportNotificationEnabled: Boolean = SettingsDefaults.POST_IMPORT_NOTIFICATION_ENABLED
        val duplicateResolutionPolicy: String = SettingsDefaults.DUPLICATE_RESOLUTION_POLICY.storageValue
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")


