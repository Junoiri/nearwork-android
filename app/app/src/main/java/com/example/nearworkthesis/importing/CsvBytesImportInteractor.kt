package com.example.nearworkthesis.importing

import android.util.Log
import com.example.nearworkthesis.core.util.AppConstants
import com.example.nearworkthesis.domain.ImportGap
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.PreprocessingPipeline
import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import com.example.nearworkthesis.domain.model.ImportSession
import com.example.nearworkthesis.domain.model.Measurement
import com.example.nearworkthesis.domain.repository.ImportSessionRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.MeasurementInsertResult
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.SettingsStore
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CsvBytesImportInteractor(
    private val transactionRunner: ImportTransactionRunner,
    private val importSessionRepository: ImportSessionRepository,
    private val measurementRepository: MeasurementRepository,
    private val settingsStore: SettingsStore,
    private val profileRepository: ProfileRepository,
    private val preprocessingPipeline: PreprocessingPipeline = PreprocessingPipeline(),
    private val parser: SampleCsvParser = SampleCsvParser()
) {

    suspend fun importCsvBytes(
        profileId: Long,
        filename: String,
        sourceType: ImportSourceType,
        bytes: ByteArray
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val parseResult = ByteArrayInputStream(bytes).use { parser.parse(it) }
                importParsedMeasurements(profileId, filename, sourceType, parseResult)
            }.getOrElse { t ->
                ImportResult.Error(t.message ?: "Unable to import CSV.")
            }
        }
    }

    suspend fun importParsedMeasurements(
        profileId: Long,
        filename: String,
        sourceType: ImportSourceType,
        parseResult: SampleParseResult
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val recordingDurationMillis = calculateRecordingDurationMillis(parseResult)
                if (recordingDurationMillis != null && recordingDurationMillis < MIN_RECORDING_DURATION_MILLIS) {
                    return@withContext ImportResult.Error(
                        "Import rejected: recordings must span at least ${MIN_RECORDING_DURATION_HOURS} hours. " +
                            "This file covers ${formatDurationHours(recordingDurationMillis)} hours."
                    )
                }

                val importedAtEpochMillis = System.currentTimeMillis()
                val timezoneId = resolveTimezoneId(profileId)
                val duplicateResolutionPolicy = settingsStore.observeDuplicateResolutionPolicy().first()
                val provenance = buildProvenance(timezoneId, duplicateResolutionPolicy)
                val preprocessingInput = parseResult.measurements.map { parsed ->
                    com.example.nearworkthesis.domain.analysis.NearworkSample(
                        timestampMillis = parsed.timestampEpochMillis,
                        distanceCm = parsed.distanceCm,
                        lux = parsed.lux
                    )
                }
                val preprocessingResult = preprocessingPipeline.process(preprocessingInput)

                val gaps = parseResult.gaps.map { gap ->
                    ImportGap(
                        startEpochMillis = gap.startEpochMillis,
                        endEpochMillis = gap.endEpochMillis,
                        durationMillis = gap.durationMillis
                    )
                }
                val largestGap = parseResult.gaps.maxOfOrNull { it.durationMillis }

                transactionRunner.withTransaction {
                    val sessionId = importSessionRepository.upsertSession(
                        ImportSession(
                            profileId = profileId,
                            importedAtEpochMillis = importedAtEpochMillis,
                            filename = filename,
                            totalRows = parseResult.totalRows,
                            insertedRows = 0,
                            rejectedRows = parseResult.rejectedRows,
                            invalidTimestampCount = parseResult.invalidTimestampCount,
                            invalidDistanceCount = parseResult.invalidDistanceCount,
                            invalidLuxCount = parseResult.invalidLuxCount,
                            duplicatesRemovedCount = parseResult.duplicatesInFileCount,
                            gapCount = parseResult.gaps.size,
                            largestGapDurationMillis = largestGap,
                            firstTimestampEpochMillis = parseResult.firstTimestampEpochMillis,
                            lastTimestampEpochMillis = parseResult.lastTimestampEpochMillis,
                            source = sourceType.name,
                            timezoneId = provenance.timezoneId,
                            note = "Imported via $sourceType; preprocessing_output_count=${preprocessingResult.stats.outputCount}"
                        )
                    )

                    val zone = ZoneId.of(provenance.timezoneId)
                    val dedupedMeasurements = dedupeMeasurements(
                        measurements = parseResult.measurements,
                        policy = duplicateResolutionPolicy,
                        robustness = preprocessingPipeline.config.robustness
                    )
                        .map { parsed ->
                            val localDay = Instant.ofEpochMilli(parsed.timestampEpochMillis)
                                .atZone(zone)
                                .toLocalDate()
                                .toString()
                            Measurement(
                                profileId = profileId,
                                sessionId = sessionId,
                                timestampEpochMillis = parsed.timestampEpochMillis,
                                localDay = localDay,
                                distanceCm = parsed.distanceCm,
                                lux = parsed.lux
                            )
                        }

                    val insertResult = if (dedupedMeasurements.isNotEmpty()) {
                        measurementRepository.addMeasurements(
                            dedupedMeasurements,
                            duplicateResolutionPolicy
                        )
                    } else {
                        MeasurementInsertResult(insertedCount = 0, replacedCount = 0)
                    }

                    val insertedCount = insertResult.insertedCount
                    val duplicatesExisting = when (duplicateResolutionPolicy) {
                        DuplicateResolutionPolicy.KEEP_EXISTING -> (dedupedMeasurements.size - insertedCount).coerceAtLeast(0)
                        DuplicateResolutionPolicy.REPLACE_WITH_NEW -> insertResult.replacedCount
                    }
                    val duplicatesEncountered = parseResult.duplicatesInFileCount + duplicatesExisting
                    val duplicatesKept = if (duplicateResolutionPolicy == DuplicateResolutionPolicy.KEEP_EXISTING) {
                        duplicatesEncountered
                    } else {
                        0
                    }
                    val duplicatesReplaced = if (duplicateResolutionPolicy == DuplicateResolutionPolicy.REPLACE_WITH_NEW) {
                        duplicatesEncountered
                    } else {
                        0
                    }

                    importSessionRepository.upsertSession(
                        ImportSession(
                            id = sessionId,
                            profileId = profileId,
                            importedAtEpochMillis = importedAtEpochMillis,
                            filename = filename,
                            totalRows = parseResult.totalRows,
                            insertedRows = insertedCount,
                            rejectedRows = parseResult.rejectedRows,
                            invalidTimestampCount = parseResult.invalidTimestampCount,
                            invalidDistanceCount = parseResult.invalidDistanceCount,
                            invalidLuxCount = parseResult.invalidLuxCount,
                            duplicatesRemovedCount = duplicatesKept,
                            gapCount = parseResult.gaps.size,
                            largestGapDurationMillis = largestGap,
                            firstTimestampEpochMillis = parseResult.firstTimestampEpochMillis,
                            lastTimestampEpochMillis = parseResult.lastTimestampEpochMillis,
                            source = sourceType.name,
                            timezoneId = provenance.timezoneId,
                            note = "Imported via $sourceType; preprocessing_output_count=${preprocessingResult.stats.outputCount}"
                        )
                    )

                    val summary = ImportSummary(
                        filename = filename,
                        sourceType = sourceType,
                        totalRows = parseResult.totalRows,
                        insertedRows = insertedCount,
                        rejectedRows = parseResult.rejectedRows,
                        invalidTimestampCount = parseResult.invalidTimestampCount,
                        invalidDistanceCount = parseResult.invalidDistanceCount,
                        invalidLuxCount = parseResult.invalidLuxCount,
                        croppedByTimeWindowCount = parseResult.croppedByTimeWindowCount,
                        croppedByEndWindowCount = parseResult.croppedByEndWindowCount,
                        duplicatesRemovedCount = duplicatesKept,
                        gapCount = parseResult.gaps.size,
                        largestGapDurationMillis = largestGap,
                        gaps = gaps,
                        firstTimestampEpochMillis = parseResult.firstTimestampEpochMillis,
                        lastTimestampEpochMillis = parseResult.lastTimestampEpochMillis,
                        timezoneId = provenance.timezoneId,
                        lowLightThresholdLux = provenance.lowLightThresholdLux,
                        nearworkDistanceThresholdCm = provenance.nearworkDistanceThresholdCm,
                        breakGapSeconds = provenance.breakGapSeconds,
                        minSessionDurationSeconds = provenance.minSessionDurationSeconds,
                        closeDistanceThresholdCm = provenance.closeDistanceThresholdCm,
                        extremeCloseThresholdCm = provenance.extremeCloseThresholdCm,
                        smoothingWindowSize = provenance.smoothingWindowSize,
                        duplicateResolutionPolicy = duplicateResolutionPolicy,
                        duplicatesEncounteredCount = duplicatesEncountered,
                        duplicatesKeptCount = duplicatesKept,
                        duplicatesReplacedCount = duplicatesReplaced
                    )

                    runCatching {
                        Log.i(
                            IMPORT_SUMMARY_LOG_TAG,
                            "Imported file=$filename source=$sourceType inserted=$insertedCount rejected=${parseResult.rejectedRows} " +
                                "firstTs=${summary.firstTimestampEpochMillis} lastTs=${summary.lastTimestampEpochMillis} " +
                                "timezone=${summary.timezoneId} firstDay=${affectedDay(summary.firstTimestampEpochMillis, provenance.timezoneId)} " +
                                "lastDay=${affectedDay(summary.lastTimestampEpochMillis, provenance.timezoneId)}"
                        )
                    }

                    if (insertedCount > 0 || insertResult.replacedCount > 0) {
                        ImportResult.Success(summary)
                    } else {
                        ImportResult.NoNewData(summary)
                    }
                }
            }.getOrElse { t ->
                ImportResult.Error(t.message ?: "Unable to import CSV.")
            }
        }
    }

    private suspend fun resolveTimezoneId(profileId: Long): String {
        val profileTz = profileRepository.getProfile(profileId)?.timezoneId
        val fallback = ZoneId.systemDefault().id
        val candidate = profileTz ?: fallback
        return runCatching { ZoneId.of(candidate).id }.getOrDefault(fallback)
    }

    private suspend fun buildProvenance(
        timezoneId: String,
        duplicateResolutionPolicy: DuplicateResolutionPolicy
    ): ImportProvenance {
        val lowLightThresholdLux = settingsStore.observeLowLightThresholdLux().first()
        val nearworkDistanceThresholdCm = settingsStore.observeNearworkDistanceThresholdCm().first()
        val breakGapSeconds = settingsStore.observeBreakGapSeconds().first()
        val minSessionDurationSeconds = settingsStore.observeMinSessionDurationSeconds().first()
        val closeDistanceThresholdCm = settingsStore.observeCloseDistanceThresholdCm().first()
        val extremeCloseThresholdCm = settingsStore.observeExtremeCloseThresholdCm().first()
        val replaceAlsSingleSampleSpikes = settingsStore.observeReplaceAlsSingleSampleSpikes().first()
        val alsSpikeThresholdLux = settingsStore.observeAlsSpikeThresholdLux().first()

        val smoothingWindowSize = preprocessingPipeline.config.smoothingWindowSize

        return ImportProvenance(
            timezoneId = timezoneId,
            lowLightThresholdLux = lowLightThresholdLux,
            nearworkDistanceThresholdCm = nearworkDistanceThresholdCm,
            breakGapSeconds = breakGapSeconds,
            minSessionDurationSeconds = minSessionDurationSeconds,
            closeDistanceThresholdCm = closeDistanceThresholdCm,
            extremeCloseThresholdCm = extremeCloseThresholdCm,
            smoothingWindowSize = smoothingWindowSize
        )
    }
}

private fun affectedDay(timestampEpochMillis: Long?, timezoneId: String): String? {
    timestampEpochMillis ?: return null
    return Instant.ofEpochMilli(timestampEpochMillis)
        .atZone(ZoneId.of(timezoneId))
        .toLocalDate()
        .toString()
}

private fun dedupeMeasurements(
    measurements: List<ParsedMeasurement>,
    policy: DuplicateResolutionPolicy,
    robustness: RobustnessConfig
): List<ParsedMeasurement> {
    return ImportTimestampDeduplicator.dedupe(measurements, policy, robustness)
}

private fun calculateRecordingDurationMillis(parseResult: SampleParseResult): Long? {
    val firstTimestamp = parseResult.firstTimestampEpochMillis ?: return null
    val lastTimestamp = parseResult.lastTimestampEpochMillis ?: return null
    return (lastTimestamp - firstTimestamp).coerceAtLeast(0L)
}

private fun formatDurationHours(durationMillis: Long): String {
    return String.format(java.util.Locale.US, "%.2f", durationMillis / 3_600_000.0)
}

private data class ImportProvenance(
    val timezoneId: String,
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int,
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistanceThresholdCm: Int,
    val extremeCloseThresholdCm: Int,
    val smoothingWindowSize: Int
)
private const val IMPORT_SUMMARY_LOG_TAG = "ImportSummary"
private const val MIN_RECORDING_DURATION_HOURS = AppConstants.MIN_IMPORT_DURATION_HOURS
private const val MIN_RECORDING_DURATION_MILLIS = MIN_RECORDING_DURATION_HOURS * 60L * 60L * 1000L

