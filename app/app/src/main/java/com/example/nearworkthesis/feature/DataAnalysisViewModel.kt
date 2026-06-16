package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.NrsResult
import com.example.nearworkthesis.domain.analysis.SessionAveragedNrsCalculator
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

sealed interface DataAnalysisUiState {
    data object Loading : DataAnalysisUiState
    data object Empty : DataAnalysisUiState
    data class Data(
        val analysis: DataAnalysisDay,
        val sessionInsights: DailySessionInsights,
        val nrsResult: NrsResult,
        val analysisConfig: AnalysisConfig
    ) : DataAnalysisUiState
    data class Error(val message: String) : DataAnalysisUiState
}

class DataAnalysisViewModel(
    private val measurementRepository: MeasurementRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
    private val selectedDate: String?
) : ViewModel() {

    // I mirror the daily screen here so the analysis panel cannot disagree on the same dataset.
    private val sessionAveragedNrsCalculator = SessionAveragedNrsCalculator(
        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator
    )

    private val _uiState = MutableStateFlow<DataAnalysisUiState>(DataAnalysisUiState.Loading)
    val uiState: StateFlow<DataAnalysisUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private val hasStarted = AtomicBoolean(false)
    private var activeProfileId: Long? = null

    init {
        observeActiveProfile()
    }

    fun refresh() {
        val profileId = activeProfileId
        if (profileId == null) {
            _uiState.value = DataAnalysisUiState.Loading
            return
        }
        loadForProfile(profileId)
    }

    private fun observeActiveProfile() {
        viewModelScope.launch {
            activeProfileStore.observeActiveProfileId()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { profileId ->
                    activeProfileId = profileId
                    loadForProfile(profileId)
                }
        }
    }

    private fun loadForProfile(profileId: Long) {
        if (hasStarted.getAndSet(true)) {
            observeJob?.cancel()
        }
        _uiState.value = DataAnalysisUiState.Loading
        observeJob = viewModelScope.launch {
            try {
                val targetDay = selectedDate ?: measurementRepository.getLatestDay(profileId)
                if (targetDay.isNullOrBlank()) {
                    _uiState.value = DataAnalysisUiState.Empty
                    return@launch
                }

                measurementRepository.observeCurrentAnalysisConfig(profileId)
                    .collectLatest { analysisConfig ->
                        combine(
                            flow { emit(measurementRepository.getDataAnalysisDay(profileId, targetDay, analysisConfig)) },
                            measurementRepository.observeDailySessionInsights(profileId, targetDay, analysisConfig)
                        ) { analysis, insights ->
                            Triple(analysis, insights, analysisConfig)
                        }.collect { (analysis, insights, resolvedConfig) ->
                            if (analysis.rawSamples.isEmpty()) {
                                _uiState.value = DataAnalysisUiState.Empty
                            } else {
                                _uiState.value = DataAnalysisUiState.Data(
                                    analysis = analysis,
                                    sessionInsights = insights,
                                    // I use the same session-first NRS here so this inspection view stays consistent with Daily.
                                    nrsResult = sessionAveragedNrsCalculator.calculate(
                                        samples = analysis.processedSamples,
                                        thresholds = resolvedConfig.thresholds,
                                        robustness = resolvedConfig.pipeline.robustness
                                    ),
                                    analysisConfig = resolvedConfig
                                )
                            }
                        }
                    }
            } catch (t: Throwable) {
                _uiState.value = DataAnalysisUiState.Error(t.message ?: "Unable to load analysis.")
            }
        }
    }

    companion object {
        fun factory(
            measurementRepository: MeasurementRepository,
            activeProfileStore: ActiveProfileStore,
            nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
            selectedDate: String?
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DataAnalysisViewModel(
                        measurementRepository = measurementRepository,
                        activeProfileStore = activeProfileStore,
                        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
                        selectedDate = selectedDate
                    ) as T
                }
            }
        }
    }
}
