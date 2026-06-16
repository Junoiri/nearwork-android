package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.core.error.AppError
import com.example.nearworkthesis.core.error.AppResult
import com.example.nearworkthesis.core.error.onFailure
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.NrsResult
import com.example.nearworkthesis.domain.analysis.SessionAveragedNrsCalculator
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

sealed interface DailyUiState {
    data object Loading : DailyUiState
    data object Empty : DailyUiState
    data class Data(
        val summary: DailySummary,
        val sampleCount: Int,
        val avgDistanceCm: Double?,
        val processedSamples: List<NearworkSample>,
        val sessionInsights: DailySessionInsights,
        val sessions: List<DailySessionUiModel>,
        val nrsResult: NrsResult,
        val analysisConfig: AnalysisConfig,
        val isCurrentDay: Boolean,
        val profiles: List<Profile>,
        val activeProfileId: Long?
    ) : DailyUiState
    data class Error(val message: String) : DailyUiState
}

sealed interface DailyUiEvent {
    data class DayDeleted(val localDay: String, val shouldNavigateBack: Boolean) : DailyUiEvent
    data class DeleteFailed(val localDay: String) : DailyUiEvent
}

class DailyViewModel(
    private val measurementRepository: MeasurementRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
    private val initialSelectedDate: String? = null
) : ViewModel() {

    // I keep one session-aware calculator here so the daily card matches the thesis scope.
    private val sessionAveragedNrsCalculator = SessionAveragedNrsCalculator(
        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator
    )

    private val _uiState = MutableStateFlow<DailyUiState>(DailyUiState.Loading)
    val uiState: StateFlow<DailyUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DailyUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DailyUiEvent> = _events.asSharedFlow()

    private val _selectedDate = MutableStateFlow(parseSelectedDate(initialSelectedDate) ?: LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _lowerBound = MutableStateFlow(defaultLowerBound())
    val canGoBack: StateFlow<Boolean> = combine(_selectedDate, _lowerBound) { selectedDate, lowerBound ->
        selectedDate.isAfter(lowerBound)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _selectedDate.value.isAfter(_lowerBound.value))

    val canGoForward: StateFlow<Boolean> = _selectedDate.map { selectedDate ->
        selectedDate.isBefore(LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _selectedDate.value.isBefore(LocalDate.now()))

    private var observeJob: Job? = null
    private val hasStarted = AtomicBoolean(false)
    private var activeProfileId: Long? = null
    private var currentDay: String? = null

    init {
        observeActiveProfile()
    }

    fun refresh() {
        val profileId = activeProfileId
        if (profileId == null) {
            _uiState.value = DailyUiState.Loading
            return
        }
        observeForProfile(profileId)
    }

    fun deleteCurrentDay() {
        val profileId = activeProfileId ?: return
        val localDay = currentDay ?: return
        viewModelScope.launch {
            val deleted = runCatching {
                measurementRepository.deleteDay(profileId, localDay)
            }.getOrElse { 0 }
            if (deleted > 0) {
                _events.tryEmit(DailyUiEvent.DayDeleted(localDay, initialSelectedDate != null))
            } else {
                _events.tryEmit(DailyUiEvent.DeleteFailed(localDay))
            }
        }
    }

    fun goToPreviousDay() {
        if (!canGoBack.value) return
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun goToNextDay() {
        if (!canGoForward.value) return
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    private fun observeActiveProfile() {
        viewModelScope.launch {
            activeProfileStore.observeActiveProfileId()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { profileId ->
                    activeProfileId = profileId
                    observeForProfile(profileId)
                }
        }
    }

    private fun observeForProfile(profileId: Long) {
        if (hasStarted.getAndSet(true)) {
            observeJob?.cancel()
        }
        _uiState.value = DailyUiState.Loading
        observeJob = viewModelScope.launch {
            try {
                combine(
                    _selectedDate,
                    measurementRepository.observeAvailableDays(profileId),
                    measurementRepository.observeCurrentAnalysisConfig(profileId),
                    profileRepository.observeProfiles()
                ) { selectedDate, availableDays, analysisConfig, profiles ->
                    DailyLoadInputs(
                        selectedDate = selectedDate,
                        availableDays = availableDays,
                        analysisConfig = analysisConfig,
                        profiles = profiles
                    )
                }.collectLatest { inputs ->
                    _lowerBound.value = calculateLowerBound(inputs.availableDays)

                    val targetDay = inputs.selectedDate.toString()
                    currentDay = targetDay
                    val isCurrentDay = inputs.selectedDate == LocalDate.now()

                    combine(
                        measurementRepository.getDailySummary(profileId, targetDay, inputs.analysisConfig),
                        measurementRepository.observeDailySessionInsights(profileId, targetDay, inputs.analysisConfig)
                    ) { summary, insights ->
                        summary to insights
                    }.collectLatest { (summary, insights) ->
                        val measurements = measurementRepository.getMeasurementsForLocalDay(profileId, targetDay)
                        val sampleCount = measurements.size
                        val avgDistance = if (measurements.isEmpty()) null else measurements.map { it.distanceCm }.average()
                        val resolvedSummary = summary ?: buildFallbackSummary(targetDay, measurements)
                        val analysisDay = measurementRepository.getDataAnalysisDay(
                            profileId = profileId,
                            day = targetDay,
                            config = inputs.analysisConfig
                        )
                        // I keep the session breakdown here because the Daily screen now needs per-session cards too.
                        val sessionNrs = sessionAveragedNrsCalculator.calculateWithSessions(
                            samples = analysisDay.processedSamples,
                            thresholds = inputs.analysisConfig.thresholds,
                            robustness = inputs.analysisConfig.pipeline.robustness
                        )
                        val nrsResult = sessionNrs.aggregatedResult
                        // I key by session bounds here because the detector output and insights list describe the same windows.
                        val sessionEntriesByBounds = sessionNrs.sessionEntries.associateBy { entry ->
                            entry.sessionWindow.startTimestampMillis to entry.sessionWindow.endTimestampMillis
                        }
                        // I join the risk reasons here so the composable gets one complete session record per row.
                        val reasonsByBounds = insights.flaggedSessions.associate { flagged ->
                            (flagged.session.startTimestampMillis to flagged.session.endTimestampMillis) to flagged.reasons
                        }
                        // I build the display payload once here so the UI does not have to recover metrics from multiple lists.
                        val sessions = insights.sessions.map { session ->
                            val entry = sessionEntriesByBounds[session.startTimestampMillis to session.endTimestampMillis]
                            DailySessionUiModel(
                                session = session,
                                nrs = entry?.nrsResult?.nrs ?: 0.0,
                                meanLux = entry?.nrsResult?.meanLuxDuringNearwork,
                                reasons = reasonsByBounds[session.startTimestampMillis to session.endTimestampMillis].orEmpty()
                            )
                        }

                        if (resolvedSummary == null || sampleCount == 0) {
                            _uiState.value = DailyUiState.Empty
                        } else {
                            _uiState.value = DailyUiState.Data(
                                summary = resolvedSummary,
                                sampleCount = sampleCount,
                                avgDistanceCm = avgDistance,
                                processedSamples = analysisDay.processedSamples,
                                sessionInsights = insights,
                                sessions = sessions,
                                nrsResult = nrsResult,
                                analysisConfig = inputs.analysisConfig,
                                isCurrentDay = isCurrentDay,
                                profiles = inputs.profiles,
                                activeProfileId = profileId
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                AppResult.Failure(
                    AppError.DatabaseError(t.message ?: "unknown")
                ).onFailure { error ->
                    _uiState.value = DailyUiState.Error(error.reason)
                }
            }
        }
    }

    private fun buildFallbackSummary(day: String, measurements: List<Measurement>): DailySummary? {
        if (measurements.isEmpty()) return null
        val distances = measurements.map { it.distanceCm }
        val luxValues = measurements.map { it.lux }
        val minTs = measurements.minOf { it.timestampEpochMillis }
        val maxTs = measurements.maxOf { it.timestampEpochMillis }
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val zoneId = ZoneId.systemDefault()
        val firstIso = LocalDateTime.ofInstant(Instant.ofEpochMilli(minTs), zoneId).format(formatter)
        val lastIso = LocalDateTime.ofInstant(Instant.ofEpochMilli(maxTs), zoneId).format(formatter)
        return DailySummary(
            day = day,
            sampleCount = measurements.size,
            avgDistanceCm = distances.average(),
            minDistanceCm = distances.minOrNull(),
            maxDistanceCm = distances.maxOrNull(),
            avgLux = luxValues.average(),
            minLux = luxValues.minOrNull(),
            maxLux = luxValues.maxOrNull(),
            diopterHoursTotal = 0.0,
            lowLightMinutes = 0,
            firstTimestampIso = firstIso,
            lastTimestampIso = lastIso
        )
    }

    companion object {
        fun factory(
            measurementRepository: MeasurementRepository,
            profileRepository: ProfileRepository,
            activeProfileStore: ActiveProfileStore,
            nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
            selectedDate: String?
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DailyViewModel(
                        measurementRepository = measurementRepository,
                        profileRepository = profileRepository,
                        activeProfileStore = activeProfileStore,
                        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
                        initialSelectedDate = selectedDate
                    ) as T
                }
            }
        }
    }
}

private data class DailyLoadInputs(
    val selectedDate: LocalDate,
    val availableDays: List<String>,
    val analysisConfig: AnalysisConfig,
    val profiles: List<Profile>
)

private fun parseSelectedDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
}

private fun defaultLowerBound(): LocalDate = LocalDate.now().minusDays(7)

private fun calculateLowerBound(availableDays: List<String>): LocalDate {
    val today = LocalDate.now()
    val sevenDaysAgo = today.minusDays(7)
    val earliestRecentMeasurementDay = availableDays
        .mapNotNull(::parseSelectedDate)
        .filter { !it.isBefore(sevenDaysAgo) && !it.isAfter(today) }
        .minOrNull()
    return earliestRecentMeasurementDay ?: sevenDaysAgo
}
