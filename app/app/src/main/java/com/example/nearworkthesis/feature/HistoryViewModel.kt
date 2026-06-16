package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class HistoryViewMode { Calendar, List }

data class HistoryCalendarState(
    val month: YearMonth,
    val monthLabel: String,
    val daySummaries: Map<String, MonthDaySummary>
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Empty : HistoryUiState
    data class Data(
        val mode: HistoryViewMode,
        val days: List<HistoryDaySummary>,
        val calendar: HistoryCalendarState,
        val analysisConfig: AnalysisConfig,
        val profiles: List<Profile>,
        val activeProfileId: Long?
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

sealed interface HistoryUiEvent {
    data class DayDeleted(val localDay: String) : HistoryUiEvent
    data class DeleteFailed(val localDay: String) : HistoryUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val measurementRepository: MeasurementRepository,
    private val profileRepository: ProfileRepository,
    private val activeProfileStore: ActiveProfileStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HistoryUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HistoryUiEvent> = _events.asSharedFlow()

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val mode = MutableStateFlow(HistoryViewMode.Calendar)
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    private var observeJob: Job? = null
    private var activeProfileId: Long? = null

    init {
        observeActiveProfile()
    }

    fun retry() {
        _uiState.value = HistoryUiState.Loading
        observeActiveProfile(forceRefresh = true)
    }

    fun setMode(newMode: HistoryViewMode) {
        mode.value = newMode
    }

    fun goToPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun goToLatestMonth() {
        val profileId = activeProfileId ?: return
        viewModelScope.launch {
            val latest = measurementRepository.getLatestDay(profileId) ?: return@launch
            val latestMonth = runCatching { YearMonth.from(LocalDate.parse(latest)) }.getOrNull() ?: return@launch
            selectedMonth.value = latestMonth
        }
    }

    fun deleteDay(localDay: String) {
        val profileId = activeProfileId ?: return
        viewModelScope.launch {
            val deleted = runCatching {
                measurementRepository.deleteDay(profileId, localDay)
            }.getOrElse { 0 }
            if (deleted > 0) {
                _events.tryEmit(HistoryUiEvent.DayDeleted(localDay))
            } else {
                _events.tryEmit(HistoryUiEvent.DeleteFailed(localDay))
            }
        }
    }

    private fun observeActiveProfile(forceRefresh: Boolean = false) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            try {
                val activeProfileFlow = activeProfileStore.observeActiveProfileId()
                    .filterNotNull()
                    .distinctUntilChanged()

                activeProfileFlow
                    .flatMapLatest { profileId ->
                        activeProfileId = profileId
                        if (forceRefresh) _uiState.value = HistoryUiState.Loading

                        val availableDaysFlow = measurementRepository.observeAvailableDays(profileId)

                        val calendarFlow = selectedMonth.flatMapLatest { month ->
                            val startDay = month.atDay(1).toString()
                            val endDay = month.atEndOfMonth().toString()
                            measurementRepository.observeCurrentAnalysisConfig(profileId)
                                .flatMapLatest { analysisConfig ->
                                    measurementRepository.observeDaySummariesInRange(
                                        profileId = profileId,
                                        startDay = startDay,
                                        endDay = endDay,
                                        config = analysisConfig
                                    ).map { summaries ->
                                        Triple(analysisConfig, summaries, month)
                                    }
                                }
                        }

                        combine(availableDaysFlow, calendarFlow, mode, profileRepository.observeProfiles()) { availableDays, calendarTriplet, viewMode, profiles ->
                            val (analysisConfig, summaries, month) = calendarTriplet
                            // I reuse the same per-day analysis flow here so History rows can show D·h and NRS too.
                            val days = loadHistoryDays(
                                profileId = profileId,
                                availableDays = availableDays,
                                config = analysisConfig
                            )
                            val calendarState = HistoryCalendarState(
                                month = month,
                                monthLabel = month.format(monthFormatter),
                                daySummaries = summaries.associateBy { it.day }
                            )
                            if (days.isEmpty()) {
                                HistoryUiState.Empty
                            } else {
                                HistoryUiState.Data(
                                    mode = viewMode,
                                    days = days,
                                    calendar = calendarState,
                                    analysisConfig = analysisConfig,
                                    profiles = profiles,
                                    activeProfileId = profileId
                                )
                            }
                        }
                    }
                    .collect { state ->
                        _uiState.value = state
                    }
            } catch (throwable: Throwable) {
                _uiState.value = HistoryUiState.Error(throwable.message ?: "Unable to load history.")
            }
        }
    }

    private suspend fun loadHistoryDays(
        profileId: Long,
        availableDays: List<String>,
        config: AnalysisConfig
    ): List<HistoryDaySummary> {
        val firstDay = availableDays.firstOrNull() ?: return emptyList()
        val lastDay = availableDays.lastOrNull() ?: return emptyList()
        // I ask the repository for the full analysed range so list mode stays in sync with the weekly/day cards.
        val summaries = measurementRepository.getDailySummariesInRange(
            profileId = profileId,
            startDay = firstDay,
            endDay = lastDay,
            config = config
        ).first()
        return summaries
            .map { it.toHistorySummary() }
            .sortedByDescending { it.day }
    }

    companion object {
        fun factory(
            measurementRepository: MeasurementRepository,
            profileRepository: ProfileRepository,
            activeProfileStore: ActiveProfileStore
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        measurementRepository = measurementRepository,
                        profileRepository = profileRepository,
                        activeProfileStore = activeProfileStore
                    ) as T
                }
            }
        }
    }
}

// I keep the mapping local so History can reuse the richer daily summary payload without a new repository model.
private fun WeeklyDaySummary.toHistorySummary(): HistoryDaySummary {
    return HistoryDaySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistanceCm,
        avgLux = avgLux,
        diopterHoursTotal = diopterHoursTotal,
        nrs = nrs,
        firstTimestampIso = firstTimestampIso,
        lastTimestampIso = lastTimestampIso
    )
}

