package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileListItem(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val dayCount: Int,
    val sampleCount: Int
)

sealed interface ProfilesUiState {
    data object Loading : ProfilesUiState
    data class Data(val profiles: List<ProfileListItem>) : ProfilesUiState
    data class Error(val message: String) : ProfilesUiState
}

class ProfilesViewModel(
    private val profileRepository: ProfileRepository,
    private val measurementRepository: MeasurementRepository,
    private val activeProfileStore: ActiveProfileStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfilesUiState>(ProfilesUiState.Loading)
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        observeProfiles()
    }

    fun addProfile(name: String, dateOfBirth: String?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                // I trim empty DOB input to null so we never store blank strings as fake dates.
                val newId = profileRepository.insertProfile(trimmed, System.currentTimeMillis(), dateOfBirth?.trim()?.ifBlank { null })
                activeProfileStore.setActiveProfileId(newId)
            }.onFailure { t ->
                _uiState.value = ProfilesUiState.Error(t.message ?: "Unable to add profile.")
            }
        }
    }

    fun renameProfile(profileId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                profileRepository.renameProfile(profileId, trimmed)
            }.onFailure { t ->
                _uiState.value = ProfilesUiState.Error(t.message ?: "Unable to rename profile.")
            }
        }
    }

    fun setActive(profileId: Long) {
        viewModelScope.launch {
            activeProfileStore.setActiveProfileId(profileId)
        }
    }

    fun deleteProfile(profileId: Long) {
        viewModelScope.launch {
            runCatching {
                profileRepository.deleteProfile(profileId)
            }.onFailure { t ->
                _uiState.value = ProfilesUiState.Error(t.message ?: "Unable to delete profile.")
            }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            runCatching {
                combine(
                    profileRepository.observeProfiles(),
                    activeProfileStore.observeActiveProfileId().distinctUntilChanged()
                ) { profiles, activeId ->
                    profiles to activeId
                }.collect { (profiles, activeId) ->
                    _uiState.value = ProfilesUiState.Loading
                    val listItems = withContext(Dispatchers.IO) {
                        buildListItems(profiles, activeId)
                    }
                    _uiState.value = ProfilesUiState.Data(listItems)
                }
            }.onFailure { t ->
                _uiState.value = ProfilesUiState.Error(t.message ?: "Unable to load profiles.")
            }
        }
    }

    private suspend fun buildListItems(
        profiles: List<Profile>,
        activeId: Long?
    ): List<ProfileListItem> {
        return profiles.sortedBy { it.createdAtEpochMillis }.map { profile ->
            val dayCount = measurementRepository.observeAvailableDays(profile.id).first().size
            val sampleCount = profileRepository.countMeasurements(profile.id)
            ProfileListItem(
                id = profile.id,
                name = profile.name,
                isActive = profile.id == activeId,
                dayCount = dayCount,
                sampleCount = sampleCount
            )
        }
    }

    companion object {
        fun factory(
            profileRepository: ProfileRepository,
            measurementRepository: MeasurementRepository,
            activeProfileStore: ActiveProfileStore
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfilesViewModel(
                        profileRepository = profileRepository,
                        measurementRepository = measurementRepository,
                        activeProfileStore = activeProfileStore
                    ) as T
                }
            }
        }
    }
}
