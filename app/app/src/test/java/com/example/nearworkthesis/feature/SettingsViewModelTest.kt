package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun uiState_reflectsCombinedSettingsFlows() = runTest {
        val settingsStore = MutableSettingsStore()
        val viewModel = SettingsViewModel(settingsStore)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        val initial = viewModel.uiState.value
        assertEquals(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX, initial.lowLightThresholdLux)
        assertEquals(SettingsDefaults.DUPLICATE_RESOLUTION_POLICY, initial.duplicateResolutionPolicy)

        settingsStore.lowLight.value = 420
        settingsStore.nearworkDistance.value = 55
        settingsStore.breakGap.value = 90
        settingsStore.minSession.value = 120
        settingsStore.closeDistance.value = 28
        settingsStore.extremeClose.value = 18
        settingsStore.replaceAlsSpikes.value = false
        settingsStore.alsSpikeThreshold.value = 123.5
        settingsStore.showDebugOverlay.value = true
        settingsStore.lastDemoProfileId.value = 9L
        settingsStore.dailyReminderEnabled.value = true
        settingsStore.dailyReminderTimeLocal.value = "08:30"
        settingsStore.postImportNotificationEnabled.value = true
        settingsStore.duplicateResolutionPolicy.value = DuplicateResolutionPolicy.REPLACE_WITH_NEW
        advanceUntilIdle()

        val updated = viewModel.uiState.value
        assertEquals(420, updated.lowLightThresholdLux)
        assertEquals(55, updated.nearworkDistanceThresholdCm)
        assertEquals(90, updated.breakGapSeconds)
        assertEquals(120, updated.minSessionDurationSeconds)
        assertEquals(28, updated.closeDistanceThresholdCm)
        assertEquals(18, updated.extremeCloseThresholdCm)
        assertEquals(false, updated.replaceAlsSingleSampleSpikes)
        assertEquals(123.5, updated.alsSpikeThresholdLux, 0.0)
        assertEquals(true, updated.showDebugOverlay)
        assertEquals(9L, updated.lastDemoProfileId)
        assertEquals(true, updated.dailyReminderEnabled)
        assertEquals("08:30", updated.dailyReminderTimeLocal)
        assertEquals(true, updated.postImportNotificationEnabled)
        assertEquals(DuplicateResolutionPolicy.REPLACE_WITH_NEW, updated.duplicateResolutionPolicy)
        collectionJob.cancel()
    }

    @Test
    fun setterMethods_forwardToSettingsStore() = runTest {
        val settingsStore = MutableSettingsStore()
        val viewModel = SettingsViewModel(settingsStore)

        viewModel.setLowLightThresholdLux(333)
        viewModel.setNearworkDistanceThresholdCm(44)
        viewModel.setBreakGapSeconds(77)
        viewModel.setMinSessionDurationSeconds(88)
        viewModel.setCloseDistanceThresholdCm(25)
        viewModel.setExtremeCloseThresholdCm(12)
        viewModel.setReplaceAlsSingleSampleSpikes(false)
        viewModel.setAlsSpikeThresholdLux(444.0)
        viewModel.setShowDebugOverlay(true)
        viewModel.setLastDemoProfileId(4L)
        viewModel.setDailyReminderEnabled(true)
        viewModel.setDailyReminderTimeLocal("09:15")
        viewModel.setPostImportNotificationEnabled(true)
        viewModel.setDuplicateResolutionPolicy(DuplicateResolutionPolicy.REPLACE_WITH_NEW)
        advanceUntilIdle()

        assertEquals(333, settingsStore.lowLight.value)
        assertEquals(44, settingsStore.nearworkDistance.value)
        assertEquals(77, settingsStore.breakGap.value)
        assertEquals(88, settingsStore.minSession.value)
        assertEquals(25, settingsStore.closeDistance.value)
        assertEquals(12, settingsStore.extremeClose.value)
        assertEquals(false, settingsStore.replaceAlsSpikes.value)
        assertEquals(444.0, settingsStore.alsSpikeThreshold.value, 0.0)
        assertEquals(true, settingsStore.showDebugOverlay.value)
        assertEquals(4L, settingsStore.lastDemoProfileId.value)
        assertEquals(true, settingsStore.dailyReminderEnabled.value)
        assertEquals("09:15", settingsStore.dailyReminderTimeLocal.value)
        assertEquals(true, settingsStore.postImportNotificationEnabled.value)
        assertEquals(DuplicateResolutionPolicy.REPLACE_WITH_NEW, settingsStore.duplicateResolutionPolicy.value)
    }

    @Test
    fun thresholdOrderingValidation_andFactory_behaveAsExpected() {
        assertEquals(
            SettingsViewModel.THRESHOLD_ORDERING_ERROR,
            SettingsViewModel.validateThresholdOrdering(closeDistanceThresholdCm = 20, extremeCloseThresholdCm = 20)
        )
        assertNull(SettingsViewModel.validateThresholdOrdering(closeDistanceThresholdCm = 20, extremeCloseThresholdCm = 19))

        val factory = SettingsViewModel.factory(MutableSettingsStore())
        val created = factory.create(SettingsViewModel::class.java)
        assertEquals(SettingsViewModel::class.java, created::class.java)
    }
}

private class MutableSettingsStore : SettingsStore {
    val lowLight = MutableStateFlow(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX)
    val nearworkDistance = MutableStateFlow(SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM)
    val breakGap = MutableStateFlow(SettingsDefaults.BREAK_GAP_SECONDS)
    val minSession = MutableStateFlow(SettingsDefaults.MIN_SESSION_DURATION_SECONDS)
    val closeDistance = MutableStateFlow(SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM)
    val extremeClose = MutableStateFlow(SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM)
    val replaceAlsSpikes = MutableStateFlow(SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES)
    val alsSpikeThreshold = MutableStateFlow(SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX)
    val showDebugOverlay = MutableStateFlow(SettingsDefaults.SHOW_DEBUG_OVERLAY)
    val lastDemoProfileId = MutableStateFlow<Long?>(null)
    val dailyReminderEnabled = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_ENABLED)
    val dailyReminderTimeLocal = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL)
    val postImportNotificationEnabled = MutableStateFlow(SettingsDefaults.POST_IMPORT_NOTIFICATION_ENABLED)
    val duplicateResolutionPolicy = MutableStateFlow(SettingsDefaults.DUPLICATE_RESOLUTION_POLICY)

    override fun observeLowLightThresholdLux(): Flow<Int> = lowLight
    override suspend fun setLowLightThresholdLux(lux: Int) { lowLight.value = lux }
    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = nearworkDistance
    override suspend fun setNearworkDistanceThresholdCm(value: Int) { nearworkDistance.value = value }
    override fun observeBreakGapSeconds(): Flow<Int> = breakGap
    override suspend fun setBreakGapSeconds(value: Int) { breakGap.value = value }
    override fun observeMinSessionDurationSeconds(): Flow<Int> = minSession
    override suspend fun setMinSessionDurationSeconds(value: Int) { minSession.value = value }
    override fun observeCloseDistanceThresholdCm(): Flow<Int> = closeDistance
    override suspend fun setCloseDistanceThresholdCm(value: Int) { closeDistance.value = value }
    override fun observeExtremeCloseThresholdCm(): Flow<Int> = extremeClose
    override suspend fun setExtremeCloseThresholdCm(value: Int) { extremeClose.value = value }
    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = replaceAlsSpikes
    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) { replaceAlsSpikes.value = enabled }
    override fun observeAlsSpikeThresholdLux(): Flow<Double> = alsSpikeThreshold
    override suspend fun setAlsSpikeThresholdLux(value: Double) { alsSpikeThreshold.value = value }
    override fun observeShowDebugOverlay(): Flow<Boolean> = showDebugOverlay
    override suspend fun setShowDebugOverlay(enabled: Boolean) { showDebugOverlay.value = enabled }
    override fun observeLastDemoProfileId(): Flow<Long?> = lastDemoProfileId
    override suspend fun setLastDemoProfileId(profileId: Long?) { lastDemoProfileId.value = profileId }
    override fun observeDailyReminderEnabled(): Flow<Boolean> = dailyReminderEnabled
    override suspend fun setDailyReminderEnabled(enabled: Boolean) { dailyReminderEnabled.value = enabled }
    override fun observeDailyReminderTimeLocal(): Flow<String> = dailyReminderTimeLocal
    override suspend fun setDailyReminderTimeLocal(value: String) { dailyReminderTimeLocal.value = value }
    override fun observePostImportNotificationEnabled(): Flow<Boolean> = postImportNotificationEnabled
    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) { postImportNotificationEnabled.value = enabled }
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = duplicateResolutionPolicy
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) { duplicateResolutionPolicy.value = policy }
}
