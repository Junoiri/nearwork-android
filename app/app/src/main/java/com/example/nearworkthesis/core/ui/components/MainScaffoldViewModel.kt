package com.example.nearworkthesis.core.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainScaffoldUiState(
    val profiles: List<Profile>,
    val activeProfileId: Long?,
    val showDebugOverlay: Boolean,
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int
) {
    val activeProfile: Profile? = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
}

class MainScaffoldViewModel(
    private val profileRepository: ProfileRepository,
    settingsStore: SettingsStore,
    private val activeProfileStore: ActiveProfileStore
) : ViewModel() {

    private data class MainScaffoldSettingsState(
        val showDebugOverlay: Boolean,
        val lowLightThresholdLux: Int,
        val nearworkDistanceThresholdCm: Int
    )

    val uiState: StateFlow<MainScaffoldUiState> =
        combine(
            profileRepository.observeProfiles(),
            activeProfileStore.observeActiveProfileId()
        ) { profiles, activeId ->
            profiles to activeId
        }.combine(
            combine(
                settingsStore.observeShowDebugOverlay(),
                settingsStore.observeLowLightThresholdLux(),
                settingsStore.observeNearworkDistanceThresholdCm()
            ) { showDebugOverlay, lowLightThresholdLux, nearworkDistanceThresholdCm ->
                MainScaffoldSettingsState(
                    showDebugOverlay = showDebugOverlay,
                    lowLightThresholdLux = lowLightThresholdLux,
                    nearworkDistanceThresholdCm = nearworkDistanceThresholdCm
                )
            }
        ) { (profiles, activeId), settings ->
            MainScaffoldUiState(
                profiles = profiles,
                activeProfileId = activeId,
                showDebugOverlay = settings.showDebugOverlay,
                lowLightThresholdLux = settings.lowLightThresholdLux,
                nearworkDistanceThresholdCm = settings.nearworkDistanceThresholdCm
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MainScaffoldUiState(
                profiles = emptyList(),
                activeProfileId = null,
                showDebugOverlay = SettingsDefaults.SHOW_DEBUG_OVERLAY,
                lowLightThresholdLux = SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX,
                nearworkDistanceThresholdCm = SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM
            )
        )

    fun setActiveProfile(profileId: Long) {
        viewModelScope.launch {
            activeProfileStore.setActiveProfileId(profileId)
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
        }
    }

    companion object {
        fun factory(
            profileRepository: ProfileRepository,
            settingsStore: SettingsStore,
            activeProfileStore: ActiveProfileStore
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainScaffoldViewModel(
                        profileRepository = profileRepository,
                        settingsStore = settingsStore,
                        activeProfileStore = activeProfileStore
                    ) as T
                }
            }
        }
    }
}
