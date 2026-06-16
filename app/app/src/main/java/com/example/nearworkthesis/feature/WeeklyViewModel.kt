package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.analysis.SessionAveragedNrsCalculator
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException

data class WeekRange(
    val start: LocalDate,
    val end: LocalDate
)

sealed interface WeeklyUiState {
    data object Loading : WeeklyUiState
    data object Empty : WeeklyUiState
    data class Data(
        val days: List<WeeklyDaySummary>,
        val totalNrs: Double,
        val selectedRange: WeekRange,
        val availableRanges: List<WeekRange>,
        val analysisConfig: AnalysisConfig
    ) : WeeklyUiState
    data class Error(val message: String) : WeeklyUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyViewModel(
    private val measurementRepository: MeasurementRepository,
    private val activeProfileStore: ActiveProfileStore,
    private val nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
    private val days: Int = 7
) : ViewModel() {

    // I reuse the session-aware calculator here so the weekly card is built from session NRS values too.
    private val sessionAveragedNrsCalculator = SessionAveragedNrsCalculator(
        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator
    )

    private val _uiState = MutableStateFlow<WeeklyUiState>(WeeklyUiState.Loading)
    val uiState: StateFlow<WeeklyUiState> = _uiState.asStateFlow()
    private val selectedEndDay = MutableStateFlow<LocalDate?>(null)

    private var observeJob: Job? = null
    private val hasStarted = AtomicBoolean(false)

    init {
        observeWeekly(showLoading = true)
    }

    fun retry() {
        observeWeekly(showLoading = true)
    }

    fun goToCurrentRange() {
        selectedEndDay.value = null
    }

    fun selectRange(range: WeekRange) {
        selectedEndDay.value = range.end
    }

    private fun observeWeekly(showLoading: Boolean) {
        if (hasStarted.getAndSet(true)) {
            observeJob?.cancel()
        }
        if (showLoading) {
            _uiState.value = WeeklyUiState.Loading
        }
        observeJob = viewModelScope.launch {
            try {
                activeProfileStore.observeActiveProfileId()
                    .filterNotNull()
                    .distinctUntilChanged()
                    .flatMapLatest { profileId ->
                        measurementRepository.observeAvailableDays(profileId)
                            .distinctUntilChanged()
                            .combine(selectedEndDay) { daysAvailable, selectedEnd ->
                                daysAvailable to selectedEnd
                            }
                            .flatMapLatest { (daysAvailable, selectedEnd) ->
                                val parsed = daysAvailable.mapNotNull { parseDayOrNull(it) }.sorted()
                                val latest = parsed.lastOrNull() ?: return@flatMapLatest flowOf(WeeklyUiState.Empty)
                                val earliest = parsed.first()
                                val resolvedEnd = when {
                                    selectedEnd == null -> latest
                                    selectedEnd.isBefore(earliest) -> earliest
                                    selectedEnd.isAfter(latest) -> latest
                                    else -> selectedEnd
                                }

                                val selectedRange = WeekRange(
                                    start = resolvedEnd.minusDays((days - 1).coerceAtLeast(0).toLong()),
                                    end = resolvedEnd
                                )
                                val availableRanges = buildWeekRanges(parsed, latest, maxWeeks = 12)
                                val startDay = selectedRange.start.toString()
                                val endDay = selectedRange.end.toString()
                                measurementRepository.observeCurrentAnalysisConfig(profileId)
                                    .flatMapLatest { analysisConfig ->
                                        measurementRepository.getDailySummariesInRange(
                                            profileId = profileId,
                                            startDay = startDay,
                                            endDay = endDay,
                                            config = analysisConfig
                                        ).map { summaries ->
                                            Triple(analysisConfig, summaries, profileId to (selectedRange to availableRanges))
                                        }
                                    }
                                    .map { (analysisConfig, summaries, stateData) ->
                                        val (resolvedProfileId, rangeData) = stateData
                                        val (range, ranges) = rangeData
                                        if (summaries.isEmpty()) {
                                            WeeklyUiState.Empty
                                        } else {
                                            // Each day NRS already carries its own session durations, so the weekly headline is a sum.
                                            val totalNrs = if (summaries.isNotEmpty()) {
                                                summaries.sumOf { it.nrs }
                                            } else {
                                                0.0
                                            }
                                            WeeklyUiState.Data(
                                                days = summaries,
                                                totalNrs = totalNrs,
                                                selectedRange = range,
                                                availableRanges = ranges,
                                                analysisConfig = analysisConfig
                                            )
                                        }
                                    }
                            }
                    }
                    .collect { state ->
                        _uiState.value = state
                    }
            } catch (t: CancellationException) {
                return@launch
            } catch (t: Throwable) {
                _uiState.value = WeeklyUiState.Error(t.message ?: "Unable to load weekly summary.")
            }
        }
    }

    private fun parseDayOrNull(day: String): LocalDate? =
        runCatching { LocalDate.parse(day) }.getOrNull()

    private fun buildWeekRanges(
        availableDays: List<LocalDate>,
        latestDay: LocalDate,
        maxWeeks: Int
    ): List<WeekRange> {
        if (availableDays.isEmpty() || maxWeeks <= 0) return emptyList()
        val earliest = availableDays.first()
        val ranges = ArrayList<WeekRange>(maxWeeks)
        var offset = 0
        while (ranges.size < maxWeeks) {
            val end = latestDay.minusDays(offset.toLong() * 7L)
            if (end.isBefore(earliest)) break
            val start = end.minusDays(6)
            val hasData = availableDays.any { day -> !day.isBefore(start) && !day.isAfter(end) }
            if (hasData) {
                ranges.add(WeekRange(start = start, end = end))
            }
            offset += 1
        }
        return ranges
    }

    companion object {
        fun factory(
            measurementRepository: MeasurementRepository,
            activeProfileStore: ActiveProfileStore,
            nearworkRiskScoreCalculator: NearworkRiskScoreCalculator,
            days: Int = 7
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WeeklyViewModel(
                        measurementRepository = measurementRepository,
                        activeProfileStore = activeProfileStore,
                        nearworkRiskScoreCalculator = nearworkRiskScoreCalculator,
                        days = days
                    ) as T
                }
            }
        }
    }
}

