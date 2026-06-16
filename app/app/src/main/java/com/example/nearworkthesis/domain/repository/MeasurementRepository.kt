package com.example.nearworkthesis.domain.repository

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.model.MonthDaySummary
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement>
    suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING
    ): MeasurementInsertResult
    suspend fun getLatestDay(profileId: Long): String?
    suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement>
    fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig? = null): Flow<DailySummary?>
    fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig? = null): Flow<DailySessionInsights>
    suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig? = null): DataAnalysisDay
    fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>>
    fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig? = null): Flow<List<MonthDaySummary>>
    fun getLastNDays(profileId: Long, days: Int = 7, config: AnalysisConfig? = null): Flow<List<WeeklyDaySummary>>
    fun getDailySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig? = null): Flow<List<WeeklyDaySummary>>
    fun observeAvailableDays(profileId: Long): Flow<List<String>>
    fun observeMeasurementCount(profileId: Long): Flow<Int>
    fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig>
    suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig
    suspend fun deleteDay(profileId: Long, localDay: String): Int
    suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String
    suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig? = null): String
    suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String
    suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig? = null): String
    suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig? = null
    ): ResultsPackCsvs
}
