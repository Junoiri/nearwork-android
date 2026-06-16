package com.example.nearworkthesis.feature

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.analysis.DiopterHoursCalculator
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.SessionAveragedNrsCalculator
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageState
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object NoData : HomeUiState
    data class Data(
        val diopterHours: Double,
        val nrs: Double,
        val sessionCount: Int,
        val activeProfileName: String
    ) : HomeUiState
}

sealed interface HomeHowfarUiState {
    data object Disconnected : HomeHowfarUiState
    data class Ready(val displayName: String?) : HomeHowfarUiState
    data class Error(val message: String) : HomeHowfarUiState
}

class HomeViewModel(
    private val appContext: Context,
    private val measurementRepository: MeasurementRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val diopterHoursCalculator: DiopterHoursCalculator,
    private val nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
    private val howfarStorageRepository: HowfarStorageRepository
) : ViewModel() {

    // I keep home on the same session-based NRS path so its headline matches the detail screens.
    private val sessionAveragedNrsCalculator = SessionAveragedNrsCalculator(
        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator
    )

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _howfarUiState = MutableStateFlow<HomeHowfarUiState>(HomeHowfarUiState.Disconnected)
    val howfarUiState: StateFlow<HomeHowfarUiState> = _howfarUiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        observeHome()
        observeHowfarState()
    }

    private fun observeHome() {
        viewModelScope.launch {
            combine(
                activeProfileStore.observeActiveProfileId().distinctUntilChanged(),
                profileRepository.observeProfiles()
            ) { activeProfileId, profiles ->
                activeProfileId to profiles
            }.collectLatest { (activeProfileId, profiles) ->
                val activeProfile = profiles.firstOrNull { it.id == activeProfileId }
                    ?: if (activeProfileId == null) null else profiles.firstOrNull()
                if (activeProfile == null) {
                    observeJob?.cancel()
                    _uiState.value = HomeUiState.NoData
                    return@collectLatest
                }
                observeForProfile(activeProfile.id, activeProfile.name)
            }
        }
    }

    private fun observeForProfile(profileId: Long, profileName: String) {
        observeJob?.cancel()
        _uiState.value = HomeUiState.Loading
        observeJob = viewModelScope.launch {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            measurementRepository.observeCurrentAnalysisConfig(profileId)
                .collectLatest { analysisConfig ->
                    measurementRepository.getDailySummary(
                        profileId = profileId,
                        day = today,
                        config = analysisConfig
                    ).collectLatest { summary ->
                        val measurements = measurementRepository.getMeasurementsForLocalDay(profileId, today)
                        if (summary == null || measurements.isEmpty()) {
                            _uiState.value = HomeUiState.NoData
                            return@collectLatest
                        }

                        val analysisDay = measurementRepository.getDataAnalysisDay(
                            profileId = profileId,
                            day = today,
                            config = analysisConfig
                        )
                        val processedSamples = analysisDay.processedSamples
                        val diopterHours = diopterHoursCalculator.calculate(processedSamples).totalDiopterHours
                        // I reuse the resolved thresholds here so the home tile honors snapshot-vs-current mode too.
                        val sessionNrs = sessionAveragedNrsCalculator.calculateWithSessions(
                            samples = processedSamples,
                            thresholds = analysisConfig.thresholds,
                            robustness = analysisConfig.pipeline.robustness
                        )
                        // I count detector sessions here because import-session ids do not reflect separate nearwork episodes.
                        val nrs = sessionNrs.aggregatedResult.nrs
                        // I report the detected nearwork sessions so home matches the daily breakdown instead of import batches.
                        val sessionCount = sessionNrs.sessionEntries.size

                        _uiState.value = HomeUiState.Data(
                            diopterHours = diopterHours,
                            nrs = nrs,
                            sessionCount = sessionCount,
                            activeProfileName = profileName
                        )
                    }
                }
        }
    }

    private fun observeHowfarState() {
        viewModelScope.launch {
            howfarStorageRepository.state.collectLatest { state ->
                _howfarUiState.value = state.toHomeHowfarUiState()
            }
        }
    }

    fun refreshHowfarAvailability() {
        howfarStorageRepository.refresh()
    }

    private fun HowfarStorageState.toHomeHowfarUiState(): HomeHowfarUiState {
        return when (this) {
            HowfarStorageState.Disconnected -> HomeHowfarUiState.Disconnected
            is HowfarStorageState.Error -> HomeHowfarUiState.Error(message)
            is HowfarStorageState.Ready -> HomeHowfarUiState.Ready(info.displayName)
        }
    }

    companion object {
        fun factory(
            appContext: Context,
            measurementRepository: MeasurementRepository,
            profileRepository: ProfileRepository,
            activeProfileStore: ActiveProfileStore,
            diopterHoursCalculator: DiopterHoursCalculator,
            nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
            howfarStorageRepository: HowfarStorageRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        appContext = appContext,
                        measurementRepository = measurementRepository,
                        profileRepository = profileRepository,
                        activeProfileStore = activeProfileStore,
                        diopterHoursCalculator = diopterHoursCalculator,
                        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
                        howfarStorageRepository = howfarStorageRepository
                    ) as T
                }
            }
        }
    }
}
