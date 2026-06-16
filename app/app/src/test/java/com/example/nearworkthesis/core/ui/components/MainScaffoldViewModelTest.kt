package com.example.nearworkthesis.core.ui.components

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScaffoldViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun uiState_combinesProfilesActiveProfileAndSettings() = runTest {
        val profiles = MutableStateFlow(
            listOf(
                profile(id = 1L, name = "Alpha"),
                profile(id = 2L, name = "Beta")
            )
        )
        val activeProfile = MutableActiveProfileStore(2L)
        val settingsStore = MutableMainScaffoldSettingsStore()
        val viewModel = MainScaffoldViewModel(
            profileRepository = MutableProfileRepository(profiles),
            settingsStore = settingsStore,
            activeProfileStore = activeProfile
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        val initial = viewModel.uiState.value
        assertEquals(2L, initial.activeProfileId)
        assertEquals("Beta", initial.activeProfile?.name)
        assertEquals(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX, initial.lowLightThresholdLux)

        settingsStore.showDebugOverlay.value = true
        settingsStore.lowLightThresholdLux.value = 222
        settingsStore.nearworkDistanceThresholdCm.value = 48
        activeProfile.id.value = 1L
        profiles.value = listOf(profile(id = 1L, name = "Alpha"))
        advanceUntilIdle()

        val updated = viewModel.uiState.value
        assertTrue(updated.showDebugOverlay)
        assertEquals(222, updated.lowLightThresholdLux)
        assertEquals(48, updated.nearworkDistanceThresholdCm)
        assertEquals("Alpha", updated.activeProfile?.name)
        collector.cancel()
    }

    @Test
    fun setActiveProfileAndDeleteProfile_forwardToStoresAndRepositories_andFactoryCreatesViewModel() = runTest {
        val repository = MutableProfileRepository(MutableStateFlow(listOf(profile(id = 9L, name = "Gamma"))))
        val activeProfile = MutableActiveProfileStore(null)
        val settingsStore = MutableMainScaffoldSettingsStore()
        val viewModel = MainScaffoldViewModel(
            profileRepository = repository,
            settingsStore = settingsStore,
            activeProfileStore = activeProfile
        )

        viewModel.setActiveProfile(9L)
        viewModel.deleteProfile(9L)
        advanceUntilIdle()

        assertEquals(9L, activeProfile.id.value)
        assertEquals(listOf(9L), repository.deletedIds)

        val factory = MainScaffoldViewModel.factory(repository, settingsStore, activeProfile)
        val created = factory.create(MainScaffoldViewModel::class.java)
        assertEquals(MainScaffoldViewModel::class.java, created::class.java)
    }

    private fun profile(id: Long, name: String) = Profile(
        id = id,
        name = name,
        createdAtEpochMillis = 0L,
        timezoneId = "UTC",
        dateOfBirth = null
    )
}

private class MutableProfileRepository(
    private val profiles: MutableStateFlow<List<Profile>>
) : ProfileRepository {
    val deletedIds = mutableListOf<Long>()

    override suspend fun getProfiles(): List<Profile> = profiles.value
    override fun observeProfiles(): Flow<List<Profile>> = profiles
    override suspend fun getProfile(profileId: Long): Profile? = profiles.value.firstOrNull { it.id == profileId }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 0L
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) {
        deletedIds += profileId
    }
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private class MutableActiveProfileStore(initialId: Long?) : ActiveProfileStore {
    val id = MutableStateFlow(initialId)
    override fun observeActiveProfileId(): Flow<Long?> = id
    override suspend fun setActiveProfileId(id: Long) {
        this.id.value = id
    }
}

private class MutableMainScaffoldSettingsStore : SettingsStore {
    val showDebugOverlay = MutableStateFlow(SettingsDefaults.SHOW_DEBUG_OVERLAY)
    val lowLightThresholdLux = MutableStateFlow(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX)
    val nearworkDistanceThresholdCm = MutableStateFlow(SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM)

    override fun observeLowLightThresholdLux(): Flow<Int> = lowLightThresholdLux
    override suspend fun setLowLightThresholdLux(lux: Int) = Unit
    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = nearworkDistanceThresholdCm
    override suspend fun setNearworkDistanceThresholdCm(value: Int) = Unit
    override fun observeBreakGapSeconds(): Flow<Int> = MutableStateFlow(SettingsDefaults.BREAK_GAP_SECONDS)
    override suspend fun setBreakGapSeconds(value: Int) = Unit
    override fun observeMinSessionDurationSeconds(): Flow<Int> = MutableStateFlow(SettingsDefaults.MIN_SESSION_DURATION_SECONDS)
    override suspend fun setMinSessionDurationSeconds(value: Int) = Unit
    override fun observeCloseDistanceThresholdCm(): Flow<Int> = MutableStateFlow(SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM)
    override suspend fun setCloseDistanceThresholdCm(value: Int) = Unit
    override fun observeExtremeCloseThresholdCm(): Flow<Int> = MutableStateFlow(SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM)
    override suspend fun setExtremeCloseThresholdCm(value: Int) = Unit
    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = MutableStateFlow(SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES)
    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) = Unit
    override fun observeAlsSpikeThresholdLux(): Flow<Double> = MutableStateFlow(SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX)
    override suspend fun setAlsSpikeThresholdLux(value: Double) = Unit
    override fun observeShowDebugOverlay(): Flow<Boolean> = showDebugOverlay
    override suspend fun setShowDebugOverlay(enabled: Boolean) {
        showDebugOverlay.value = enabled
    }
    override fun observeLastDemoProfileId(): Flow<Long?> = MutableStateFlow(null)
    override suspend fun setLastDemoProfileId(profileId: Long?) = Unit
    override fun observeDailyReminderEnabled(): Flow<Boolean> = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_ENABLED)
    override suspend fun setDailyReminderEnabled(enabled: Boolean) = Unit
    override fun observeDailyReminderTimeLocal(): Flow<String> = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL)
    override suspend fun setDailyReminderTimeLocal(value: String) = Unit
    override fun observePostImportNotificationEnabled(): Flow<Boolean> = MutableStateFlow(SettingsDefaults.POST_IMPORT_NOTIFICATION_ENABLED)
    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) = Unit
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = MutableStateFlow(SettingsDefaults.DUPLICATE_RESOLUTION_POLICY)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}
