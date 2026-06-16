package com.example.nearworkthesis.data.repository

import com.example.nearworkthesis.BuildConfig
import com.example.nearworkthesis.data.local.NearworkDao
import com.example.nearworkthesis.core.util.AppConstants
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AnalysisConfig
import com.example.nearworkthesis.domain.analysis.AnalysisPipelineConfig
import com.example.nearworkthesis.domain.analysis.AnalysisThresholds
import com.example.nearworkthesis.domain.analysis.AnalysisTimeHandling
import com.example.nearworkthesis.domain.analysis.DailySessionInsights
import com.example.nearworkthesis.domain.analysis.DataAnalysisDay
import com.example.nearworkthesis.domain.analysis.NearworkSample
import com.example.nearworkthesis.domain.analysis.NearworkPostProcessor
import com.example.nearworkthesis.domain.analysis.NearworkSettings
import com.example.nearworkthesis.domain.analysis.PreprocessingPipeline
import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import com.example.nearworkthesis.domain.analysis.SessionAveragedNrsCalculator
import com.example.nearworkthesis.domain.analysis.ValidationSummary
import com.example.nearworkthesis.domain.export.DailyResultsRow
import com.example.nearworkthesis.domain.export.ImportQualityRow
import com.example.nearworkthesis.domain.export.ResultsPackCsvBuilder
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.export.ResultsPackManifestBuilder
import com.example.nearworkthesis.domain.export.SessionResultsRow
import com.example.nearworkthesis.domain.model.DailySummary
import com.example.nearworkthesis.domain.model.HistoryDaySummary
import com.example.nearworkthesis.domain.model.MonthDaySummary
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.settings.SettingsStore
import com.example.nearworkthesis.settings.SettingsDefaults
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class RoomMeasurementRepository(
    private val nearworkDao: NearworkDao,
    private val settingsStore: SettingsStore,
    private val preprocessingPipeline: PreprocessingPipeline = PreprocessingPipeline(),
    private val postProcessor: NearworkPostProcessor = NearworkPostProcessor(),
    // I share this helper here so every per-day summary gets the same session-first NRS.
    private val sessionAveragedNrsCalculator: SessionAveragedNrsCalculator = SessionAveragedNrsCalculator()
) : MeasurementRepository {

    override suspend fun getMeasurementsForProfile(profileId: Long): List<Measurement> {
        return nearworkDao.getMeasurementsForProfile(profileId).map { entity ->
            Measurement(
                id = entity.id,
                profileId = entity.profileId,
                sessionId = entity.sessionId,
                timestampEpochMillis = entity.timestampEpochMillis,
                localDay = entity.localDay,
                distanceCm = entity.distanceCm,
                lux = entity.lux
            )
        }
    }

    override suspend fun addMeasurements(
        measurements: List<Measurement>,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): MeasurementInsertResult {
        if (measurements.isEmpty()) {
            return MeasurementInsertResult(insertedCount = 0, replacedCount = 0)
        }

        return when (duplicateResolutionPolicy) {
            DuplicateResolutionPolicy.KEEP_EXISTING -> {
                val ids = nearworkDao.insertMeasurements(measurements.map { it.toEntity() })
                MeasurementInsertResult(
                    insertedCount = ids.count { it != -1L },
                    replacedCount = 0
                )
            }
            DuplicateResolutionPolicy.REPLACE_WITH_NEW -> {
                val profileId = measurements.first().profileId
                val timestamps = measurements.map { it.timestampEpochMillis }
                val existing = timestamps
                    .chunked(AppConstants.MAX_SQL_IN_ARGS)
                    .flatMap { chunk ->
                        nearworkDao.getExistingMeasurementTimestamps(profileId, chunk)
                    }
                    .toHashSet()
                var replacedCount = 0
                val inserts = ArrayList<Measurement>(measurements.size)

                for (measurement in measurements) {
                    if (existing.contains(measurement.timestampEpochMillis)) {
                        val updated = nearworkDao.updateMeasurementByProfileAndTimestamp(
                            profileId = measurement.profileId,
                            timestampEpochMillis = measurement.timestampEpochMillis,
                            sessionId = measurement.sessionId,
                            localDay = measurement.localDay,
                            distanceCm = measurement.distanceCm,
                            lux = measurement.lux
                        )
                        if (updated > 0) {
                            replacedCount += 1
                        }
                    } else {
                        inserts.add(measurement)
                    }
                }

                val ids = if (inserts.isNotEmpty()) {
                    nearworkDao.insertMeasurements(inserts.map { it.toEntity() })
                } else {
                    emptyList()
                }

                MeasurementInsertResult(
                    insertedCount = ids.count { it != -1L },
                    replacedCount = replacedCount
                )
            }
        }
    }

    override suspend fun getLatestDay(profileId: Long): String? {
        return nearworkDao.getLatestLocalDay(profileId)
    }

    override suspend fun getMeasurementsForLocalDay(profileId: Long, localDay: String): List<Measurement> {
        return nearworkDao.getMeasurementsForLocalDay(profileId, localDay).map { entity ->
            Measurement(
                id = entity.id,
                profileId = entity.profileId,
                sessionId = entity.sessionId,
                timestampEpochMillis = entity.timestampEpochMillis,
                localDay = entity.localDay,
                distanceCm = entity.distanceCm,
                lux = entity.lux
            )
        }
    }

    override suspend fun deleteDay(profileId: Long, localDay: String): Int {
        return nearworkDao.deleteMeasurementsForLocalDay(profileId, localDay)
    }

    override fun observeCurrentAnalysisConfig(profileId: Long): Flow<AnalysisConfig> {
        val thresholdsFlow = combine(
            settingsStore.observeLowLightThresholdLux(),
            settingsStore.observeNearworkDistanceThresholdCm()
        ) { lowLight, nearwork ->
            ThresholdInputs(
                lowLightThresholdLux = lowLight,
                nearworkDistanceThresholdCm = nearwork
            )
        }

        val thresholdCoreExtrasFlow = combine(
            settingsStore.observeBreakGapSeconds(),
            settingsStore.observeMinSessionDurationSeconds(),
            settingsStore.observeCloseDistanceThresholdCm(),
            settingsStore.observeExtremeCloseThresholdCm()
        ) { breakGap, minSession, closeDistance, extremeClose ->
            ThresholdExtraInputs(
                breakGapSeconds = breakGap,
                minSessionDurationSeconds = minSession,
                closeDistanceThresholdCm = closeDistance,
                extremeCloseThresholdCm = extremeClose,
                replaceAlsSingleSampleSpikes = false,
                alsSpikeThresholdLux = 0.0
            )
        }

        val thresholdExtrasFlow = combine(
            thresholdCoreExtrasFlow,
            settingsStore.observeReplaceAlsSingleSampleSpikes(),
            settingsStore.observeAlsSpikeThresholdLux()
        ) { extras, replaceAlsSpikes, alsSpikeThresholdLux ->
            extras.copy(
                replaceAlsSingleSampleSpikes = replaceAlsSpikes,
                alsSpikeThresholdLux = alsSpikeThresholdLux
            )
        }

        return combine(
            thresholdsFlow,
            thresholdExtrasFlow,
            observeProfileTimezoneId(profileId)
        ) { thresholds, thresholdExtras, timezoneId ->
            buildAnalysisConfig(
                lowLightThresholdLux = thresholds.lowLightThresholdLux,
                nearworkDistanceThresholdCm = thresholds.nearworkDistanceThresholdCm,
                breakGapSeconds = thresholdExtras.breakGapSeconds,
                minSessionDurationSeconds = thresholdExtras.minSessionDurationSeconds,
                closeDistanceThresholdCm = thresholdExtras.closeDistanceThresholdCm,
                extremeCloseThresholdCm = thresholdExtras.extremeCloseThresholdCm,
                replaceAlsSingleSampleSpikes = thresholdExtras.replaceAlsSingleSampleSpikes,
                alsSpikeThresholdLux = thresholdExtras.alsSpikeThresholdLux,
                timezoneId = timezoneId
            )
        }
    }

    override suspend fun getCurrentAnalysisConfig(profileId: Long): AnalysisConfig {
        val lowLight = settingsStore.observeLowLightThresholdLux().first()
        val nearwork = settingsStore.observeNearworkDistanceThresholdCm().first()
        val breakGap = settingsStore.observeBreakGapSeconds().first()
        val minSession = settingsStore.observeMinSessionDurationSeconds().first()
        val closeDistance = settingsStore.observeCloseDistanceThresholdCm().first()
        val extremeClose = settingsStore.observeExtremeCloseThresholdCm().first()
        val replaceAlsSpikes = settingsStore.observeReplaceAlsSingleSampleSpikes().first()
        val alsSpikeThresholdLux = settingsStore.observeAlsSpikeThresholdLux().first()
        val timezoneId = resolveProfileTimezoneId(profileId)
        return buildAnalysisConfig(
            lowLightThresholdLux = lowLight,
            nearworkDistanceThresholdCm = nearwork,
            breakGapSeconds = breakGap,
            minSessionDurationSeconds = minSession,
            closeDistanceThresholdCm = closeDistance,
            extremeCloseThresholdCm = extremeClose,
            replaceAlsSingleSampleSpikes = replaceAlsSpikes,
            alsSpikeThresholdLux = alsSpikeThresholdLux,
            timezoneId = timezoneId
        )
    }

    override fun getDailySummary(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySummary?> {
        val baseSummary = nearworkDao.getDailySummary(
            profileId = profileId,
            day = day
        ).map { tuple ->
            tuple?.toDomain()
        }

        return combine(baseSummary, observeAnalysisConfig(profileId, config)) { summary, analysisConfig ->
            summary to analysisConfig
        }.mapLatest { (summary, analysisConfig) ->
            if (summary == null) return@mapLatest null

            val measurements = withContext(Dispatchers.IO) {
                nearworkDao.getMeasurementsForLocalDay(
                    profileId = profileId,
                    localDay = day
                )
            }

            val pipeline = pipelineForConfig(analysisConfig, config)

            withContext(Dispatchers.Default) {
                val rawSamples = measurements.map { m ->
                    NearworkSample(
                        timestampMillis = m.timestampEpochMillis,
                        distanceCm = m.distanceCm,
                        lux = m.lux
                    )
                }

                val processed = pipeline.process(rawSamples)
                val post = postProcessor.compute(
                    samples = processed.samples,
                    settings = analysisConfig.thresholds.toNearworkSettings(),
                    preprocessing = processed,
                    robustness = analysisConfig.pipeline.robustness
                )

                summary.copy(
                    diopterHoursTotal = post.totalDiopterHours,
                    lowLightMinutes = post.lowLightMinutes
                )
            }
        }
    }

    override fun observeDailySessionInsights(profileId: Long, day: String, config: AnalysisConfig?): Flow<DailySessionInsights> {
        return observeAnalysisConfig(profileId, config).mapLatest { analysisConfig ->
            val measurements = withContext(Dispatchers.IO) {
                nearworkDao.getMeasurementsForLocalDay(
                    profileId = profileId,
                    localDay = day
                )
            }

            val pipeline = pipelineForConfig(analysisConfig, config)

            withContext(Dispatchers.Default) {
                val rawSamples = measurements.map { m ->
                    NearworkSample(
                        timestampMillis = m.timestampEpochMillis,
                        distanceCm = m.distanceCm,
                        lux = m.lux
                    )
                }
                val processed = pipeline.process(rawSamples)
                val post = postProcessor.compute(
                    samples = processed.samples,
                    settings = analysisConfig.thresholds.toNearworkSettings(),
                    preprocessing = processed,
                    robustness = analysisConfig.pipeline.robustness
                )

                DailySessionInsights(
                    sessions = post.sessions,
                    longestSession = post.longestSession,
                    flaggedSessions = post.flaggedSessions
                )
            }
        }
    }


    override suspend fun getDataAnalysisDay(profileId: Long, day: String, config: AnalysisConfig?): DataAnalysisDay {
        val analysisConfig = resolveAnalysisConfig(profileId, config)
        val pipeline = pipelineForConfig(analysisConfig, config)
        val measurements = withContext(Dispatchers.IO) {
            nearworkDao.getMeasurementsForLocalDay(
                profileId = profileId,
                localDay = day
            )
        }

        return withContext(Dispatchers.Default) {
            val rawSamples = measurements.map { m ->
                NearworkSample(
                    timestampMillis = m.timestampEpochMillis,
                    distanceCm = m.distanceCm,
                    lux = m.lux
                )
            }

            val processed = pipeline.process(rawSamples)
            val gaps = detectGaps(rawSamples.map { it.timestampMillis }).gaps
            val avgRaw = rawSamples.map { it.distanceCm }.averageOrNull()
            val avgProcessed = processed.samples.map { it.distanceCm }.averageOrNull()
            val absDiff = if (avgRaw != null && avgProcessed != null) kotlin.math.abs(avgRaw - avgProcessed) else null

            val silentDropCount = (processed.stats.rawCount - processed.stats.dedupedCount - processed.stats.rejectedCount) - processed.samples.size

            DataAnalysisDay(
                day = day,
                rawSamples = rawSamples.sortedBy { it.timestampMillis },
                processedSamples = processed.samples,
                summary = ValidationSummary(
                    rawSampleCount = processed.stats.rawCount,
                    processedSampleCount = processed.samples.size,
                    rejectedOutliersCount = processed.stats.rejectedCount,
                    dedupedCount = processed.stats.dedupedCount,
                    gapCount = gaps.size,
                    maxGapSeconds = (gaps.maxOfOrNull { it.durationMillis } ?: 0L).toInt() / 1000,
                    avgDistanceRawCm = avgRaw,
                    avgDistanceProcessedCm = avgProcessed,
                    avgDistanceAbsDiffCm = absDiff,
                    silentDropCount = silentDropCount
                )
            )
        }
    }
    override fun getHistoryDays(profileId: Long): Flow<List<HistoryDaySummary>> {
        return nearworkDao.getHistoryDays(profileId).map { tuples ->
            tuples.map { it.toDomain() }
        }
    }

        override fun observeDaySummariesInRange(profileId: Long, startDay: String, endDay: String, config: AnalysisConfig?): Flow<List<MonthDaySummary>> {
        val base = nearworkDao.observeDaySummariesInRange(
            profileId = profileId,
            startDay = startDay,
            endDay = endDay
        )

        return combine(base, observeAnalysisConfig(profileId, config)) { summaries, analysisConfig ->
            summaries to analysisConfig
        }.mapLatest { (summaries, analysisConfig) ->
            if (summaries.isEmpty()) return@mapLatest emptyList()
            val pipeline = pipelineForConfig(analysisConfig, config)
            withContext(Dispatchers.Default) {
                summaries.map { summary ->
                    val measurements = withContext(Dispatchers.IO) {
                        nearworkDao.getMeasurementsForLocalDay(
                            profileId = profileId,
                            localDay = summary.day
                        )
                    }

                    val rawSamples = measurements.map { m ->
                        NearworkSample(
                            timestampMillis = m.timestampEpochMillis,
                            distanceCm = m.distanceCm,
                            lux = m.lux
                        )
                    }

                    val processed = pipeline.process(rawSamples)
                    val post = postProcessor.compute(
                        samples = processed.samples,
                        settings = analysisConfig.thresholds.toNearworkSettings(),
                        preprocessing = processed,
                        robustness = analysisConfig.pipeline.robustness
                    )
                    // I compute day NRS from session averages here so history and weekly stay aligned with Daily.
                    val nrs = sessionAveragedNrsCalculator.calculate(
                        samples = processed.samples,
                        thresholds = analysisConfig.thresholds,
                        robustness = analysisConfig.pipeline.robustness
                    ).nrs

                    MonthDaySummary(
                        day = summary.day,
                        sampleCount = summary.sampleCount,
                        diopterHoursTotal = post.totalDiopterHours,
                        nrs = nrs,
                        lowLightMinutes = post.lowLightMinutes
                    )
                }
            }
        }
    }

    override fun getLastNDays(profileId: Long, days: Int, config: AnalysisConfig?): Flow<List<WeeklyDaySummary>> {
        val safeDays = days.coerceAtLeast(1)
        val base = nearworkDao.getLastNDays(profileId = profileId, days = safeDays).map { tuples ->
            tuples.map { it.toDomain() }
        }

        return combine(base, observeAnalysisConfig(profileId, config)) { summaries, analysisConfig ->
            summaries to analysisConfig
        }.mapLatest { (summaries, analysisConfig) ->
            if (summaries.isEmpty()) return@mapLatest emptyList()
            val pipeline = pipelineForConfig(analysisConfig, config)
            withContext(Dispatchers.Default) {
                summaries.map { summary ->
                    val measurements = withContext(Dispatchers.IO) {
                        nearworkDao.getMeasurementsForLocalDay(
                            profileId = profileId,
                            localDay = summary.day
                        )
                    }

                    val rawSamples = measurements.map { m ->
                        NearworkSample(
                            timestampMillis = m.timestampEpochMillis,
                            distanceCm = m.distanceCm,
                            lux = m.lux
                        )
                    }

                    val processed = pipeline.process(rawSamples)
                    val post = postProcessor.compute(
                        samples = processed.samples,
                        settings = analysisConfig.thresholds.toNearworkSettings(),
                        preprocessing = processed,
                        robustness = analysisConfig.pipeline.robustness
                    )
                    // I keep weekly/day cards on the same NRS path the Daily screen uses.
                    val nrs = sessionAveragedNrsCalculator.calculate(
                        samples = processed.samples,
                        thresholds = analysisConfig.thresholds,
                        robustness = analysisConfig.pipeline.robustness
                    ).nrs

                    summary.copy(
                        diopterHoursTotal = post.totalDiopterHours,
                        nrs = nrs,
                        lowLightMinutes = post.lowLightMinutes
                    )
                }
            }
        }
    }

    override fun getDailySummariesInRange(
        profileId: Long,
        startDay: String,
        endDay: String,
        config: AnalysisConfig?
    ): Flow<List<WeeklyDaySummary>> {
        val base = nearworkDao.getDailySummariesInRange(
            profileId = profileId,
            startDay = startDay,
            endDay = endDay
        ).map { tuples ->
            tuples.map { it.toWeeklyDomain() }
        }

        return combine(base, observeAnalysisConfig(profileId, config)) { summaries, analysisConfig ->
            summaries to analysisConfig
        }.mapLatest { (summaries, analysisConfig) ->
            if (summaries.isEmpty()) return@mapLatest emptyList()
            val pipeline = pipelineForConfig(analysisConfig, config)
            withContext(Dispatchers.Default) {
                summaries.map { summary ->
                    val measurements = withContext(Dispatchers.IO) {
                        nearworkDao.getMeasurementsForLocalDay(
                            profileId = profileId,
                            localDay = summary.day
                        )
                    }

                    val rawSamples = measurements.map { m ->
                        NearworkSample(
                            timestampMillis = m.timestampEpochMillis,
                            distanceCm = m.distanceCm,
                            lux = m.lux
                        )
                    }

                    val processed = pipeline.process(rawSamples)
                    val post = postProcessor.compute(
                        samples = processed.samples,
                        settings = analysisConfig.thresholds.toNearworkSettings(),
                        preprocessing = processed,
                        robustness = analysisConfig.pipeline.robustness
                    )
                    // I mirror the same session-averaged NRS here for arbitrary date ranges too.
                    val nrs = sessionAveragedNrsCalculator.calculate(
                        samples = processed.samples,
                        thresholds = analysisConfig.thresholds,
                        robustness = analysisConfig.pipeline.robustness
                    ).nrs

                    summary.copy(
                        diopterHoursTotal = post.totalDiopterHours,
                        nrs = nrs,
                        lowLightMinutes = post.lowLightMinutes
                    )
                }
            }
        }
    }

    override fun observeAvailableDays(profileId: Long): Flow<List<String>> {
        return nearworkDao.observeAvailableDays(profileId)
    }

    override fun observeMeasurementCount(profileId: Long): Flow<Int> {
        return nearworkDao.observeMeasurementCount(profileId)
    }
    override suspend fun exportRawCsv(profileId: Long, startDay: String?, endDay: String?): String {
        val availableDays = nearworkDao.getAvailableDays(profileId)
        val resolved = resolveDayRange(availableDays = availableDays, startDay = startDay, endDay = endDay)
            ?: return rawCsvHeaderOnly()

        val measurements = nearworkDao.getMeasurementsForLocalDayRange(
            profileId = profileId,
            startDay = resolved.startDay,
            endDay = resolved.endDay
        )

        return buildString {
            appendLine("timestamp,epoch_millis,profile_id,session_id,distance_cm,illumination_lux")
            for (m in measurements) {
                val iso = Instant.ofEpochMilli(m.timestampEpochMillis)
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                append(iso)
                append(',')
                append(m.timestampEpochMillis)
                append(',')
                append(m.profileId)
                append(',')
                append(m.sessionId)
                append(',')
                append(m.distanceCm)
                append(',')
                append(m.lux)
                append('\n')
            }
        }
    }

    override suspend fun exportProcessedCsv(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): String {
        val availableDays = nearworkDao.getAvailableDays(profileId)
        val resolved = resolveDayRange(availableDays = availableDays, startDay = startDay, endDay = endDay)
            ?: return processedCsvHeaderOnly()

        val measurements = nearworkDao.getMeasurementsForLocalDayRange(
            profileId = profileId,
            startDay = resolved.startDay,
            endDay = resolved.endDay
        )

        val analysisConfig = resolveAnalysisConfig(profileId, config)
        val pipeline = pipelineForConfig(analysisConfig, config)
        val rawSamples = measurements.map { m ->
            NearworkSample(
                timestampMillis = m.timestampEpochMillis,
                distanceCm = m.distanceCm,
                lux = m.lux
            )
        }
        val processed = pipeline.process(rawSamples).samples

        return buildString {
            appendLine("timestamp,epoch_millis,profile_id,distance_cm,illumination_lux")
            for (s in processed) {
                val iso = Instant.ofEpochMilli(s.timestampMillis)
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                append(iso)
                append(',')
                append(s.timestampMillis)
                append(',')
                append(profileId)
                append(',')
                append(s.distanceCm)
                append(',')
                append(s.lux)
                append('\n')
            }
        }
    }

    override suspend fun exportDailySummaryCsv(profileId: Long, startDay: String?, endDay: String?): String {
        val availableDays = nearworkDao.getAvailableDays(profileId)
        val resolved = resolveDayRange(availableDays = availableDays, startDay = startDay, endDay = endDay)
            ?: return dailySummaryCsvHeaderOnly()

        val tuples = nearworkDao.getDailySummariesInRange(
            profileId = profileId,
            startDay = resolved.startDay,
            endDay = resolved.endDay
        ).first()

        return buildString {
            appendLine("date,sampleCount,avgDistanceCm,minDistanceCm,maxDistanceCm,avgLux,minLux,maxLux,firstTimestamp,lastTimestamp")
            for (t in tuples) {
                append(t.day)
                append(',')
                append(t.sampleCount)
                append(',')
                append(t.avgDistanceCm ?: "")
                append(',')
                append(t.minDistanceCm ?: "")
                append(',')
                append(t.maxDistanceCm ?: "")
                append(',')
                append(t.avgLux ?: "")
                append(',')
                append(t.minLux ?: "")
                append(',')
                append(t.maxLux ?: "")
                append(',')
                append((t.firstTimestampIso ?: "").replace(" ", "T"))
                append(',')
                append((t.lastTimestampIso ?: "").replace(" ", "T"))
                append('\n')
            }
        }
    }


    override suspend fun exportAnalysisReportCsv(profileId: Long, startDay: String?, endDay: String?, config: AnalysisConfig?): String {
        val availableDays = nearworkDao.getAvailableDays(profileId)
        val resolved = resolveDayRange(availableDays = availableDays, startDay = startDay, endDay = endDay)
            ?: return analysisReportHeaderOnly()

        val analysisConfig = resolveAnalysisConfig(profileId, config)
        val pipeline = pipelineForConfig(analysisConfig, config)
        val lowLightThresholdLux = analysisConfig.thresholds.lowLightThresholdLux

        val startDate = LocalDate.parse(resolved.startDay)
        val endDateInclusive = LocalDate.parse(resolved.endDay)

        return withContext(Dispatchers.Default) {
            buildString {
                appendLine("date,sampleCount,diopterHoursTotal,lowLightMinutes,rejectedCount,dedupedCount,smoothingWindowSize,lowLightThresholdLux")
                var day = startDate
                while (!day.isAfter(endDateInclusive)) {
                    val dayString = day.toString()
                    val measurements = withContext(Dispatchers.IO) {
                        nearworkDao.getMeasurementsForLocalDay(
                            profileId = profileId,
                            localDay = dayString
                        )
                    }

                    val rawSamples = measurements.map { m ->
                        NearworkSample(
                            timestampMillis = m.timestampEpochMillis,
                            distanceCm = m.distanceCm,
                            lux = m.lux
                        )
                    }

                    val processed = pipeline.process(rawSamples)
                    val post = postProcessor.compute(
                        samples = processed.samples,
                        settings = analysisConfig.thresholds.toNearworkSettings(),
                        preprocessing = processed,
                        robustness = analysisConfig.pipeline.robustness
                    )

                    append(dayString)
                    append(',')
                    append(measurements.size)
                    append(',')
                    append(post.totalDiopterHours)
                    append(',')
                    append(post.lowLightMinutes)
                    append(',')
                    append(processed.stats.rejectedCount)
                    append(',')
                    append(processed.stats.dedupedCount)
                    append(',')
                    append(processed.stats.smoothingWindowSize)
                    append(',')
                    append(lowLightThresholdLux)
                    append('\n')

                    day = day.plusDays(1)
                }
            }
        }
    }

    override suspend fun exportResultsPackCsvs(
        profileId: Long,
        startDay: String?,
        endDay: String?,
        config: AnalysisConfig?
    ): ResultsPackCsvs {
        val availableDays = nearworkDao.getAvailableDays(profileId)
        val resolved = resolveDayRange(availableDays = availableDays, startDay = startDay, endDay = endDay)
        val analysisConfig = config ?: getCurrentAnalysisConfig(profileId)
        val duplicateResolutionPolicy = settingsStore.observeDuplicateResolutionPolicy().first()
        val pipeline = if (config == null) preprocessingPipeline else PreprocessingPipeline(analysisConfig.pipeline.toPreprocessingConfig())
        val thresholds = analysisConfig.thresholds
        val pipelineConfig = analysisConfig.pipeline
        val timeHandling = analysisConfig.timeHandling

        val profile = nearworkDao.getProfile(profileId)
        val profileName = profile?.name ?: "Profile ${profileId}"
        val timeHandlingStatement = timeHandling.statement

        if (resolved == null) {
            val manifestJson = ResultsPackManifestBuilder.build(
                ResultsPackManifestBuilder.Manifest(
                    exportCreatedAtIsoUtc = nowIsoUtc(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    profileId = profileId,
                    profileName = profileName,
                    startDay = startDay ?: "unknown",
                    endDay = endDay ?: "unknown",
                    preprocessingSmoothingWindow = pipelineConfig.smoothingWindowSize,
                    dedupeRule = pipelineConfig.dedupeRule,
                    duplicateResolutionPolicy = duplicateResolutionPolicy.manifestValue,
                    distanceRangeMinCm = pipelineConfig.distanceRangeMinCm,
                    distanceRangeMaxCm = pipelineConfig.distanceRangeMaxCm,
                    luxRangeMin = pipelineConfig.luxRangeMin,
                    luxRangeMax = pipelineConfig.luxRangeMax,
                    gapDetectionThresholdSeconds = gapDetectionThresholdSeconds(emptyList()),
                    sessionNearworkDistanceThresholdCm = thresholds.nearworkDistanceThresholdCm,
                    sessionBreakGapSeconds = thresholds.breakGapSeconds,
                    sessionMinSessionDurationSeconds = thresholds.minSessionDurationSeconds,
                    sessionCloseDistanceThresholdCm = thresholds.closeDistanceThresholdCm,
                    sessionExtremeCloseThresholdCm = thresholds.extremeCloseThresholdCm,
                    lowLightThresholdLux = thresholds.lowLightThresholdLux,
                    timezoneId = timeHandling.timezoneId,
                    timeHandlingStatement = timeHandlingStatement,
                    settingsUsed = analysisConfig
                )
            )
            return ResultsPackCsvs(
                dailyResultsCsv = ResultsPackCsvBuilder.buildDailyResultsCsv(emptyList()),
                sessionsResultsCsv = ResultsPackCsvBuilder.buildSessionsResultsCsv(emptyList()),
                importQualityCsv = ResultsPackCsvBuilder.buildImportQualityCsv(emptyList()),
                manifestJson = manifestJson,
                daysWithSamples = 0
            )
        }

        val startDate = LocalDate.parse(resolved.startDay)
        val endDateInclusive = LocalDate.parse(resolved.endDay)
        return withContext(Dispatchers.Default) {
            val dailyRows = ArrayList<DailyResultsRow>()
            val sessionRows = ArrayList<SessionResultsRow>()
            val gapThresholdSeconds = ArrayList<Double>()
            val allTimestamps = ArrayList<Long>()

            var day = startDate
            while (!day.isAfter(endDateInclusive)) {
                val dayString = day.toString()
                val measurements = withContext(Dispatchers.IO) {
                    nearworkDao.getMeasurementsForLocalDay(
                        profileId = profileId,
                        localDay = dayString
                    )
                }

                val rawSamples = measurements.map { m ->
                    NearworkSample(
                        timestampMillis = m.timestampEpochMillis,
                        distanceCm = m.distanceCm,
                        lux = m.lux
                    )
                }
                allTimestamps.addAll(rawSamples.map { it.timestampMillis })

                val processed = pipeline.process(rawSamples)
                val post = postProcessor.compute(
                    samples = processed.samples,
                    settings = thresholds.toNearworkSettings(),
                    preprocessing = processed,
                    robustness = analysisConfig.pipeline.robustness
                )
                // I compute the day NRS once here so the results pack reuses the same session-averaged metric shown in the app.
                val dailyNrs = sessionAveragedNrsCalculator.calculate(
                    samples = processed.samples,
                    thresholds = thresholds,
                    robustness = analysisConfig.pipeline.robustness
                )
                // I derive per-session export metrics from the same processed samples so NRS and mean lux share the app's session segmentation.
                val sessionMetrics = buildSessionMetricsByKey(
                    processedSamples = processed.samples,
                    thresholds = thresholds,
                    calculator = sessionAveragedNrsCalculator,
                    robustness = analysisConfig.pipeline.robustness
                )
                val sessions = post.sessions
                val longest = post.longestSession
                val riskyCount = sessions.count { s ->
                    val flags = classifySessionFlags(
                        session = s,
                        closeDistanceThresholdCm = thresholds.closeDistanceThresholdCm.toDouble(),
                        extremeCloseThresholdCm = thresholds.extremeCloseThresholdCm.toDouble()
                    )
                    flags.flagsCloseDistance == 1 || flags.flagsLowLight == 1 || flags.flagsExtremeClose == 1
                }

                val gapResult = detectGaps(rawSamples.map { it.timestampMillis })
                val gaps = gapResult.gaps
                gapThresholdSeconds.add(gapResult.thresholdMillis / 1000.0)
                val maxGapSeconds = (gaps.maxOfOrNull { it.durationMillis } ?: 0L).toInt() / 1000

                if (rawSamples.isNotEmpty()) {
                    dailyRows.add(
                        DailyResultsRow(
                            date = dayString,
                            sampleCount = rawSamples.size,
                            diopterHoursTotal = post.totalDiopterHours,
                            // I export the session-averaged daily NRS here so the daily CSV matches the thesis summary metric.
                            nrsSessionAverage = dailyNrs.nrs,
                            lowLightMinutes = post.lowLightMinutes,
                            longestSessionSeconds = longest?.durationSeconds ?: 0L,
                            riskySessionCount = riskyCount,
                            gapCount = gaps.size,
                            largestGapSeconds = if (gaps.isEmpty()) null else maxGapSeconds
                        )
                    )
                }

                for (session in sessions) {
                    // I resolve the paired session metrics by time window so each exported row carries the matching NRS and mean lux.
                    val sessionMetric = sessionMetrics[session.toKey()] ?: SessionExportMetrics(meanLux = Double.NaN, nrs = 0.0)
                    val flags = classifySessionFlags(
                        session = session,
                        closeDistanceThresholdCm = thresholds.closeDistanceThresholdCm.toDouble(),
                        extremeCloseThresholdCm = thresholds.extremeCloseThresholdCm.toDouble()
                    )
                    sessionRows.add(
                        SessionResultsRow(
                            date = dayString,
                            sessionStartIsoUtc = isoUtc(session.startTimestampMillis),
                            sessionEndIsoUtc = isoUtc(session.endTimestampMillis),
                            durationSeconds = session.durationSeconds,
                            avgDistanceCm = session.avgDistanceCm,
                            minDistanceCm = session.minDistanceCm,
                            // I export per-session mean lux here because the results pack needs the descriptive light level beside NRS and D·h.
                            meanLux = sessionMetric.meanLux,
                            diopterHoursInSession = session.diopterHoursInSession,
                            // I export per-session NRS here so the session CSV carries the normalized session score used elsewhere in the app.
                            nrs = sessionMetric.nrs,
                            lowLightSecondsInSession = session.lowLightSecondsInSession,
                            flagsCloseDistance = flags.flagsCloseDistance,
                            flagsLowLight = flags.flagsLowLight,
                            flagsExtremeClose = flags.flagsExtremeClose
                        )
                    )
                }

                day = day.plusDays(1)
            }

            val importEntities = withContext(Dispatchers.IO) { nearworkDao.getImportSessionsForProfile(profileId) }
            val rangeStartMillis = startDate.atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC).toEpochMilli()
            val rangeEndMillisExclusive = endDateInclusive.plusDays(1).atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC).toEpochMilli()

            val importRows = importEntities
                .filter { entity ->
                    val first = entity.firstTimestampEpochMillis
                    val last = entity.lastTimestampEpochMillis
                    if (first == null || last == null) return@filter true
                    val overlaps = last >= rangeStartMillis && first < rangeEndMillisExclusive
                    overlaps
                }
                .sortedBy { it.importedAtEpochMillis }
                .map { entity ->
                    ImportQualityRow(
                        importedAtIsoUtc = isoUtc(entity.importedAtEpochMillis),
                        sourceType = entity.source,
                        filename = entity.filename,
                        totalRows = entity.totalRows,
                        insertedRows = entity.insertedRows,
                        rejectedRows = entity.rejectedRows,
                        rejectedTimestampCount = entity.invalidTimestampCount,
                        rejectedDistanceCount = entity.invalidDistanceCount,
                        rejectedLuxCount = entity.invalidLuxCount,
                        duplicatesRemovedCount = entity.duplicatesRemovedCount,
                        gapCount = entity.gapCount,
                        largestGapSeconds = entity.largestGapDurationMillis?.toInt()?.div(1000),
                        smoothingWindow = pipelineConfig.smoothingWindowSize,
                        thresholdsLowLightLux = thresholds.lowLightThresholdLux,
                        thresholdsNearworkCm = thresholds.nearworkDistanceThresholdCm,
                        thresholdsBreakGapSec = thresholds.breakGapSeconds,
                        thresholdsMinSessionSec = thresholds.minSessionDurationSeconds,
                        thresholdsCloseDistanceCm = thresholds.closeDistanceThresholdCm,
                        thresholdsExtremeCloseCm = thresholds.extremeCloseThresholdCm
                    )
                }

            val manifestJson = ResultsPackManifestBuilder.build(
                ResultsPackManifestBuilder.Manifest(
                    exportCreatedAtIsoUtc = nowIsoUtc(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    profileId = profileId,
                    profileName = profileName,
                    startDay = resolved.startDay,
                    endDay = resolved.endDay,
                    preprocessingSmoothingWindow = pipelineConfig.smoothingWindowSize,
                    dedupeRule = pipelineConfig.dedupeRule,
                    duplicateResolutionPolicy = duplicateResolutionPolicy.manifestValue,
                    distanceRangeMinCm = pipelineConfig.distanceRangeMinCm,
                    distanceRangeMaxCm = pipelineConfig.distanceRangeMaxCm,
                    luxRangeMin = pipelineConfig.luxRangeMin,
                    luxRangeMax = pipelineConfig.luxRangeMax,
                    gapDetectionThresholdSeconds = (gapThresholdSeconds.maxOrNull()
                        ?: gapDetectionThresholdSeconds(allTimestamps)),
                    sessionNearworkDistanceThresholdCm = thresholds.nearworkDistanceThresholdCm,
                    sessionBreakGapSeconds = thresholds.breakGapSeconds,
                    sessionMinSessionDurationSeconds = thresholds.minSessionDurationSeconds,
                    sessionCloseDistanceThresholdCm = thresholds.closeDistanceThresholdCm,
                    sessionExtremeCloseThresholdCm = thresholds.extremeCloseThresholdCm,
                    lowLightThresholdLux = thresholds.lowLightThresholdLux,
                    timezoneId = timeHandling.timezoneId,
                    timeHandlingStatement = timeHandlingStatement,
                    settingsUsed = analysisConfig
                )
            )

            ResultsPackCsvs(
                dailyResultsCsv = ResultsPackCsvBuilder.buildDailyResultsCsv(dailyRows),
                sessionsResultsCsv = ResultsPackCsvBuilder.buildSessionsResultsCsv(sessionRows.sortedBy { it.sessionStartIsoUtc }),
                importQualityCsv = ResultsPackCsvBuilder.buildImportQualityCsv(importRows),
                manifestJson = manifestJson,
                daysWithSamples = dailyRows.size
            )
        }
    }
    private fun observeAnalysisConfig(profileId: Long, config: AnalysisConfig?): Flow<AnalysisConfig> {
        return config?.let { flowOf(it) } ?: observeCurrentAnalysisConfig(profileId)
    }

    private suspend fun resolveAnalysisConfig(profileId: Long, config: AnalysisConfig?): AnalysisConfig {
        return config ?: getCurrentAnalysisConfig(profileId)
    }

    private fun pipelineForConfig(config: AnalysisConfig, override: AnalysisConfig?): PreprocessingPipeline {
        return if (override == null) preprocessingPipeline else PreprocessingPipeline(config.pipeline.toPreprocessingConfig())
    }

    private fun AnalysisPipelineConfig.toPreprocessingConfig(): PreprocessingPipeline.Config {
        return PreprocessingPipeline.Config(
            minDistanceCm = distanceRangeMinCm,
            maxDistanceCm = distanceRangeMaxCm,
            minLux = luxRangeMin,
            maxLux = luxRangeMax,
            smoothingWindowSize = smoothingWindowSize,
            robustness = robustness
        )
    }

    private fun observeProfileTimezoneId(profileId: Long): Flow<String> {
        return kotlinx.coroutines.flow.flow {
            emit(resolveProfileTimezoneId(profileId))
        }
    }

    private suspend fun resolveProfileTimezoneId(profileId: Long): String {
        val candidate = nearworkDao.getProfile(profileId)?.timezoneId
        return resolveTimezoneId(candidate)
    }

    private fun resolveTimezoneId(candidate: String?): String {
        val fallback = ZoneId.systemDefault().id
        if (candidate.isNullOrBlank()) return fallback
        return runCatching { ZoneId.of(candidate).id }.getOrDefault(fallback)
    }

    private fun buildAnalysisConfig(
        lowLightThresholdLux: Int,
        nearworkDistanceThresholdCm: Int,
        breakGapSeconds: Int,
        minSessionDurationSeconds: Int,
        closeDistanceThresholdCm: Int,
        extremeCloseThresholdCm: Int,
        replaceAlsSingleSampleSpikes: Boolean,
        alsSpikeThresholdLux: Double,
        timezoneId: String
    ): AnalysisConfig {
        val thresholds = AnalysisThresholds(
            lowLightThresholdLux = lowLightThresholdLux,
            nearworkDistanceThresholdCm = nearworkDistanceThresholdCm,
            breakGapSeconds = breakGapSeconds,
            minSessionDurationSeconds = minSessionDurationSeconds,
            closeDistanceThresholdCm = closeDistanceThresholdCm,
            extremeCloseThresholdCm = extremeCloseThresholdCm
        )
        val pipeline = AnalysisPipelineConfig(
            smoothingWindowSize = preprocessingPipeline.config.smoothingWindowSize,
            dedupeRule = DEDUPE_RULE,
            distanceRangeMinCm = preprocessingPipeline.config.minDistanceCm,
            distanceRangeMaxCm = preprocessingPipeline.config.maxDistanceCm,
            luxRangeMin = preprocessingPipeline.config.minLux,
            luxRangeMax = preprocessingPipeline.config.maxLux,
            gapThresholdSeconds = AppConstants.INTERPOLATION_GAP_THRESHOLD_SECONDS,
            robustness = preprocessingPipeline.config.robustness.copy(
                replaceAlsSingleSampleSpikes = replaceAlsSingleSampleSpikes,
                alsSingleSampleSpikeThresholdLux = alsSpikeThresholdLux
            )
        )
        return AnalysisConfig(
            thresholds = thresholds,
            pipeline = pipeline,
            timeHandling = AnalysisTimeHandling(
                timezoneId = timezoneId,
                statement = TIME_HANDLING_STATEMENT
            )
        )
    }

}

private data class ThresholdInputs(
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int
)

private data class ThresholdExtraInputs(
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistanceThresholdCm: Int,
    val extremeCloseThresholdCm: Int,
    val replaceAlsSingleSampleSpikes: Boolean,
    val alsSpikeThresholdLux: Double
)

private data class DetectedGap(val durationMillis: Long)

private data class GapDetectionResult(val gaps: List<DetectedGap>, val thresholdMillis: Long)

private fun List<Double>.averageOrNull(): Double? {
    if (isEmpty()) return null
    return average()
}

private fun detectGaps(sortedTimestamps: List<Long>): GapDetectionResult {
    if (sortedTimestamps.size < 2) return GapDetectionResult(emptyList(), MIN_GAP_THRESHOLD_MILLIS)
    val sorted = sortedTimestamps.sorted()

    val deltas = ArrayList<Long>(sorted.size - 1)
    for (i in 1 until sorted.size) {
        val delta = sorted[i] - sorted[i - 1]
        if (delta > 0) deltas.add(delta)
    }
    if (deltas.isEmpty()) return GapDetectionResult(emptyList(), MIN_GAP_THRESHOLD_MILLIS)

    val typical = medianMillis(deltas.filter { it in 1L..(30L * 60L * 1000L) }.ifEmpty { deltas })
    val threshold = maxOf(typical * 5L, MIN_GAP_THRESHOLD_MILLIS)

    val gaps = ArrayList<DetectedGap>()
    for (i in 1 until sorted.size) {
        val delta = sorted[i] - sorted[i - 1]
        if (delta > threshold) {
            gaps.add(DetectedGap(durationMillis = delta))
        }
    }
    return GapDetectionResult(gaps = gaps, thresholdMillis = threshold)
}

private fun gapDetectionThresholdSeconds(timestamps: List<Long>): Double {
    return detectGaps(timestamps).thresholdMillis / 1000.0
}

private fun medianMillis(values: List<Long>): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        (sorted[mid - 1] + sorted[mid]) / 2L
    }
}

private fun Measurement.toEntity() = com.example.nearworkthesis.data.local.MeasurementEntity(
    id = id,
    profileId = profileId,
    sessionId = sessionId,
    timestampEpochMillis = timestampEpochMillis,
    localDay = localDay,
    distanceCm = distanceCm,
    lux = lux
)

private fun com.example.nearworkthesis.data.local.DailySummaryTuple.toDomain(): DailySummary {
    return DailySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistanceCm,
        minDistanceCm = minDistanceCm,
        maxDistanceCm = maxDistanceCm,
        avgLux = avgLux,
        minLux = minLux,
        maxLux = maxLux,
        diopterHoursTotal = 0.0,
        lowLightMinutes = 0,
        firstTimestampIso = firstTimestampIso?.replace(" ", "T"),
        lastTimestampIso = lastTimestampIso?.replace(" ", "T")
    )
}

private fun com.example.nearworkthesis.data.local.HistoryDayTuple.toDomain(): HistoryDaySummary {
    return HistoryDaySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistanceCm,
        avgLux = avgLux,
        diopterHoursTotal = 0.0,
        nrs = 0.0,
        firstTimestampIso = firstTimestampIso?.replace(" ", "T"),
        lastTimestampIso = lastTimestampIso?.replace(" ", "T")
    )
}

private fun com.example.nearworkthesis.data.local.WeeklyDayTuple.toDomain(): WeeklyDaySummary {
    return WeeklyDaySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistanceCm,
        avgLux = avgLux,
        diopterHoursTotal = 0.0,
        nrs = 0.0,
        lowLightMinutes = 0,
        firstTimestampIso = firstTimestampIso?.replace(" ", "T"),
        lastTimestampIso = lastTimestampIso?.replace(" ", "T")
    )
}

private fun com.example.nearworkthesis.data.local.DailySummaryTuple.toWeeklyDomain(): WeeklyDaySummary {
    return WeeklyDaySummary(
        day = day,
        sampleCount = sampleCount,
        avgDistanceCm = avgDistanceCm,
        avgLux = avgLux,
        diopterHoursTotal = 0.0,
        // I keep placeholder NRS explicit here so old tuple-only paths still satisfy the richer UI model.
        nrs = 0.0,
        lowLightMinutes = 0,
        firstTimestampIso = firstTimestampIso?.replace(" ", "T"),
        lastTimestampIso = lastTimestampIso?.replace(" ", "T")
    )
}

private data class DayRange(val startDay: String, val endDay: String)

private fun resolveDayRange(
    availableDays: List<String>,
    startDay: String?,
    endDay: String?
): DayRange? {
    if (availableDays.isEmpty()) return null
    val minDay = availableDays.first()
    val maxDay = availableDays.last()
    val resolvedStart = startDay ?: minDay
    val resolvedEnd = endDay ?: maxDay
    return if (resolvedStart <= resolvedEnd) {
        DayRange(startDay = resolvedStart, endDay = resolvedEnd)
    } else {
        DayRange(startDay = resolvedEnd, endDay = resolvedStart)
    }
}

    private fun rawCsvHeaderOnly(): String = "timestamp,epoch_millis,profile_id,session_id,distance_cm,illumination_lux\n"
    private fun processedCsvHeaderOnly(): String = "timestamp,epoch_millis,profile_id,distance_cm,illumination_lux\n"

private fun dailySummaryCsvHeaderOnly(): String =
    "date,sampleCount,avgDistanceCm,minDistanceCm,maxDistanceCm,avgLux,minLux,maxLux,firstTimestamp,lastTimestamp\n"

private fun analysisReportHeaderOnly(): String =
    "date,sampleCount,diopterHoursTotal,lowLightMinutes,rejectedCount,dedupedCount,smoothingWindowSize,lowLightThresholdLux\n"

private fun isoUtc(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDateTime().format(ISO_LOCAL_DATE_TIME)
}

private fun nowIsoUtc(): String {
    return Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime().format(ISO_LOCAL_DATE_TIME)
}

private data class SessionFlagRow(val flagsCloseDistance: Int, val flagsLowLight: Int, val flagsExtremeClose: Int)
// I keep the keyed export payload explicit here so the results-pack loop can attach session NRS and mean lux without recomputing.
private data class SessionExportMetrics(val meanLux: Double, val nrs: Double)
// I key session metrics by the detector window boundaries so session summaries and NRS stay aligned across export paths.
private data class SessionKey(val startTimestampMillis: Long, val endTimestampMillis: Long, val durationSeconds: Long)

private fun AnalysisThresholds.toNearworkSettings(): NearworkSettings {
    return NearworkSettings(
        lowLightThresholdLux = lowLightThresholdLux,
        nearworkDistanceThresholdCm = nearworkDistanceThresholdCm,
        breakGapSeconds = breakGapSeconds,
        minSessionDurationSeconds = minSessionDurationSeconds,
        closeDistanceThresholdCm = closeDistanceThresholdCm,
        extremeCloseThresholdCm = extremeCloseThresholdCm
    )
}

private fun buildSessionMetricsByKey(
    processedSamples: List<NearworkSample>,
    thresholds: AnalysisThresholds,
    calculator: SessionAveragedNrsCalculator,
    robustness: RobustnessConfig
): Map<SessionKey, SessionExportMetrics> {
    // I reuse the calculator's session windows here so exported per-session NRS follows the same grouping as the rest of the app.
    val sessionEntries = calculator.calculateWithSessions(processedSamples, thresholds, robustness).sessionEntries
    return sessionEntries.associate { entry ->
        // I average finite lux samples from the matched window so the exported meanLux reflects the full calibrated session series.
        val meanLux = entry.sessionWindow.samples.map { it.lux }.filter { it.isFinite() }.averageOrNull() ?: Double.NaN
        entry.sessionWindow.toKey() to SessionExportMetrics(
            meanLux = meanLux,
            nrs = entry.nrsResult.nrs
        )
    }
}

// I normalize session lookups to one key shape here so detector windows and post-processed sessions can be matched safely.
private fun com.example.nearworkthesis.domain.analysis.NearworkSessionDetector.DetectedSessionWindow.toKey(): SessionKey {
    return SessionKey(
        startTimestampMillis = startTimestampMillis,
        endTimestampMillis = endTimestampMillis,
        durationSeconds = durationSeconds
    )
}

// I reuse the same key shape for exported sessions so per-session NRS and mean lux attach to the correct row.
private fun com.example.nearworkthesis.domain.analysis.NearworkSession.toKey(): SessionKey {
    return SessionKey(
        startTimestampMillis = startTimestampMillis,
        endTimestampMillis = endTimestampMillis,
        durationSeconds = durationSeconds
    )
}

private fun classifySessionFlags(
    session: com.example.nearworkthesis.domain.analysis.NearworkSession,
    closeDistanceThresholdCm: Double,
    extremeCloseThresholdCm: Double
): SessionFlagRow {
    val closeDistance = if (session.minDistanceCm < closeDistanceThresholdCm) 1 else 0
    val lowLight = if (session.lowLightSecondsInSession > 0L) 1 else 0
    val extremeClose = if (session.minDistanceCm < extremeCloseThresholdCm) 1 else 0
    return SessionFlagRow(flagsCloseDistance = closeDistance, flagsLowLight = lowLight, flagsExtremeClose = extremeClose)
}

private const val MIN_GAP_THRESHOLD_MILLIS = 60L * 1000L
private const val TIME_HANDLING_STATEMENT = "measurements stored as epoch millis UTC; localDay derived in timezoneId"
private const val DEDUPE_RULE = "same timestamp keep last"















































