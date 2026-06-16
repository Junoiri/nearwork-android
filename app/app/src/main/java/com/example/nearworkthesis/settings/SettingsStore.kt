package com.example.nearworkthesis.settings

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import kotlinx.coroutines.flow.Flow

interface SettingsStore {
    fun observeLowLightThresholdLux(): Flow<Int>
    suspend fun setLowLightThresholdLux(lux: Int)

    fun observeNearworkDistanceThresholdCm(): Flow<Int>
    suspend fun setNearworkDistanceThresholdCm(value: Int)

    fun observeBreakGapSeconds(): Flow<Int>
    suspend fun setBreakGapSeconds(value: Int)

    fun observeMinSessionDurationSeconds(): Flow<Int>
    suspend fun setMinSessionDurationSeconds(value: Int)

    fun observeCloseDistanceThresholdCm(): Flow<Int>
    suspend fun setCloseDistanceThresholdCm(value: Int)

    fun observeExtremeCloseThresholdCm(): Flow<Int>
    suspend fun setExtremeCloseThresholdCm(value: Int)

    fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean>
    suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean)

    fun observeAlsSpikeThresholdLux(): Flow<Double>
    suspend fun setAlsSpikeThresholdLux(value: Double)

    fun observeShowDebugOverlay(): Flow<Boolean>
    suspend fun setShowDebugOverlay(enabled: Boolean)

    fun observeLastDemoProfileId(): Flow<Long?>
    suspend fun setLastDemoProfileId(profileId: Long?)

    fun observeDailyReminderEnabled(): Flow<Boolean>
    suspend fun setDailyReminderEnabled(enabled: Boolean)

    fun observeDailyReminderTimeLocal(): Flow<String>
    suspend fun setDailyReminderTimeLocal(value: String)

    fun observePostImportNotificationEnabled(): Flow<Boolean>
    suspend fun setPostImportNotificationEnabled(enabled: Boolean)

    fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy>
    suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy)
}

