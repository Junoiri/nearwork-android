package com.example.nearworkthesis.testutil

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.demo.DemoDataset
import com.example.nearworkthesis.domain.demo.DemoRepository
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsDefaults
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeSettingsStore(
    initialPostImportNotificationEnabled: Boolean = false
) : SettingsStore {
    private val lowLightThresholdLux = MutableStateFlow(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX)
    private val nearworkDistanceThresholdCm = MutableStateFlow(SettingsDefaults.NEARWORK_DISTANCE_THRESHOLD_CM)
    private val breakGapSeconds = MutableStateFlow(SettingsDefaults.BREAK_GAP_SECONDS)
    private val minSessionDurationSeconds = MutableStateFlow(SettingsDefaults.MIN_SESSION_DURATION_SECONDS)
    private val closeDistanceThresholdCm = MutableStateFlow(SettingsDefaults.CLOSE_DISTANCE_THRESHOLD_CM)
    private val extremeCloseThresholdCm = MutableStateFlow(SettingsDefaults.EXTREME_CLOSE_THRESHOLD_CM)
    private val replaceAlsSingleSampleSpikes = MutableStateFlow(SettingsDefaults.REPLACE_ALS_SINGLE_SAMPLE_SPIKES)
    private val alsSpikeThresholdLux = MutableStateFlow(SettingsDefaults.ALS_SPIKE_THRESHOLD_LUX)
    private val showDebugOverlay = MutableStateFlow(SettingsDefaults.SHOW_DEBUG_OVERLAY)
    private val lastDemoProfileId = MutableStateFlow<Long?>(null)
    private val dailyReminderEnabled = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_ENABLED)
    private val dailyReminderTimeLocal = MutableStateFlow(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL)
    private val postImportNotificationEnabled = MutableStateFlow(initialPostImportNotificationEnabled)
    private val duplicateResolutionPolicy = MutableStateFlow(SettingsDefaults.DUPLICATE_RESOLUTION_POLICY)

    val postImportNotificationEnabledValue: Boolean
        get() = postImportNotificationEnabled.value

    val closeDistanceThresholdCmValue: Int
        get() = closeDistanceThresholdCm.value

    val showDebugOverlayValue: Boolean
        get() = showDebugOverlay.value

    val duplicateResolutionPolicyValue: DuplicateResolutionPolicy
        get() = duplicateResolutionPolicy.value

    override fun observeLowLightThresholdLux(): Flow<Int> = lowLightThresholdLux
    override suspend fun setLowLightThresholdLux(lux: Int) {
        lowLightThresholdLux.value = lux
    }

    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = nearworkDistanceThresholdCm
    override suspend fun setNearworkDistanceThresholdCm(value: Int) {
        nearworkDistanceThresholdCm.value = value
    }

    override fun observeBreakGapSeconds(): Flow<Int> = breakGapSeconds
    override suspend fun setBreakGapSeconds(value: Int) {
        breakGapSeconds.value = value
    }

    override fun observeMinSessionDurationSeconds(): Flow<Int> = minSessionDurationSeconds
    override suspend fun setMinSessionDurationSeconds(value: Int) {
        minSessionDurationSeconds.value = value
    }

    override fun observeCloseDistanceThresholdCm(): Flow<Int> = closeDistanceThresholdCm
    override suspend fun setCloseDistanceThresholdCm(value: Int) {
        closeDistanceThresholdCm.value = value
    }

    override fun observeExtremeCloseThresholdCm(): Flow<Int> = extremeCloseThresholdCm
    override suspend fun setExtremeCloseThresholdCm(value: Int) {
        extremeCloseThresholdCm.value = value
    }

    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = replaceAlsSingleSampleSpikes
    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) {
        replaceAlsSingleSampleSpikes.value = enabled
    }

    override fun observeAlsSpikeThresholdLux(): Flow<Double> = alsSpikeThresholdLux
    override suspend fun setAlsSpikeThresholdLux(value: Double) {
        alsSpikeThresholdLux.value = value
    }

    override fun observeShowDebugOverlay(): Flow<Boolean> = showDebugOverlay
    override suspend fun setShowDebugOverlay(enabled: Boolean) {
        showDebugOverlay.value = enabled
    }

    override fun observeLastDemoProfileId(): Flow<Long?> = lastDemoProfileId
    override suspend fun setLastDemoProfileId(profileId: Long?) {
        lastDemoProfileId.value = profileId
    }

    override fun observeDailyReminderEnabled(): Flow<Boolean> = dailyReminderEnabled
    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dailyReminderEnabled.value = enabled
    }

    override fun observeDailyReminderTimeLocal(): Flow<String> = dailyReminderTimeLocal
    override suspend fun setDailyReminderTimeLocal(value: String) {
        dailyReminderTimeLocal.value = value
    }

    override fun observePostImportNotificationEnabled(): Flow<Boolean> = postImportNotificationEnabled
    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) {
        postImportNotificationEnabled.value = enabled
    }

    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = duplicateResolutionPolicy
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) {
        duplicateResolutionPolicy.value = policy
    }
}

class FakeNotificationScheduler : NotificationScheduler {
    override fun ensureChannels() = Unit
    override suspend fun rescheduleDailyReminder() = Unit
    override suspend fun cancelDailyReminder() = Unit
    override suspend fun enqueuePostImportSummary(summary: ImportSummary) = Unit
}

class FakeDemoRepository : DemoRepository {
    override suspend fun listDemoDatasets(): List<DemoDataset> = emptyList()

    override suspend fun importDemoDataset(profileId: Long, filename: String): ImportResult {
        return ImportResult.Success(
            ImportSummary(
                filename = filename,
                sourceType = ImportSourceType.ASSET,
                totalRows = 1,
                insertedRows = 1,
                rejectedRows = 0,
                firstTimestampEpochMillis = null,
                lastTimestampEpochMillis = null
            )
        )
    }

    override suspend fun clearProfileData(profileId: Long) = Unit
}

class FakeProfileRepository(
    initialProfiles: List<Profile> = listOf(
        Profile(
            id = 1L,
            name = "Profile 1",
            createdAtEpochMillis = 0L,
            timezoneId = "UTC",
            dateOfBirth = null
        )
    )
) : ProfileRepository {
    private val profiles = MutableStateFlow(initialProfiles)
    private var nextId = (initialProfiles.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun getProfiles(): List<Profile> = profiles.value
    override fun observeProfiles(): Flow<List<Profile>> = profiles
    override suspend fun getProfile(profileId: Long): Profile? = profiles.value.firstOrNull { it.id == profileId }
    override suspend fun upsertProfile(profile: Profile): Long {
        val current = profiles.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            current[existingIndex] = profile
            profiles.value = current
            return profile.id
        }
        val resolvedId = if (profile.id == 0L) nextId++ else profile.id
        profiles.value = current + profile.copy(id = resolvedId)
        return resolvedId
    }

    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long {
        val id = nextId++
        profiles.value = profiles.value + Profile(
            id = id,
            name = name,
            createdAtEpochMillis = createdAtEpochMillis,
            timezoneId = "UTC",
            dateOfBirth = dateOfBirth
        )
        return id
    }

    override suspend fun renameProfile(profileId: Long, name: String) {
        profiles.value = profiles.value.map { profile ->
            if (profile.id == profileId) profile.copy(name = name) else profile
        }
    }

    override suspend fun deleteProfile(profileId: Long) {
        profiles.value = profiles.value.filterNot { it.id == profileId }
    }

    override suspend fun countMeasurements(profileId: Long): Int = 0
}

class FakeActiveProfileStore(initialId: Long? = 1L) : ActiveProfileStore {
    private val activeProfileId = MutableStateFlow(initialId)

    override fun observeActiveProfileId(): Flow<Long?> = activeProfileId

    override suspend fun setActiveProfileId(id: Long) {
        activeProfileId.value = id
    }
}

class FakeMeasurementRepository(
    private val availableDays: List<String> = emptyList(),
    private val weeklySummaries: List<WeeklyDaySummary> = emptyList(),
    private val monthSummaries: List<MonthDaySummary> = emptyList(),
    private val analysisConfig: AnalysisConfig = defaultAnalysisConfig()
) : MeasurementRepository {
    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> = emptyList()

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult = MeasurementInsertResult(insertedCount = measurements.size, replacedCount = 0)

    override suspend fun getLatestDay(profileId: Long): String? = availableDays.maxOrNull()
    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> = emptyList()
    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> = flowOf(null)
    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> {
        throw NotImplementedError("Unused in androidTest fake")
    }

    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        throw NotImplementedError("Unused in androidTest fake")
    }

    override fun getHistoryDays(profileId: Long) = flowOf(emptyList<com.example.nearworkthesis.domain.model.HistoryDaySummary>())

    override fun observeDaySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<MonthDaySummary>> {
        return flowOf(monthSummaries.filter { it.day in startDay..endDay })
    }

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> {
        return flowOf(weeklySummaries.sortedByDescending { it.day }.take(days))
    }

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<WeeklyDaySummary>> {
        return flowOf(weeklySummaries.filter { it.day in startDay..endDay }.sortedByDescending { it.day })
    }

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> = flowOf(availableDays.sorted())
    override fun observeMeasurementCount(profileId: Long): Flow<Int> = flowOf(0)
    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> = flowOf(analysisConfig)
    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig = analysisConfig
    override suspend fun deleteDay(profileId: Long, localDay: String): Int = 0
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportProcessedCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""
    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String = ""
    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String = ""

    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): ResultsPackCsvs {
        throw NotImplementedError("Unused in androidTest fake")
    }
}
