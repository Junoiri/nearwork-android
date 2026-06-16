/** End-to-end data parser for HowFar UF2 files; orchestrates uf2.py, ringfs.py, and database.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import android.util.Log
import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import com.example.nearworkthesis.importing.ParsedMeasurement
import com.example.nearworkthesis.importing.SampleParseResult
import com.example.nearworkthesis.importing.ImportMeasurementRejector
import com.example.nearworkthesis.importing.buildParseResultFromMeasurements
import java.time.Instant
import java.time.ZoneId

private const val DISTANCE_MIN_CM = 10.0
private const val DISTANCE_MAX_CM = 200.0
private const val LUX_MIN = 0.0
private const val LUX_MAX = 50_000.0

class HowfarDataParser(
    private val database: HowfarDatabase = HowfarDatabase,
    private val robustness: RobustnessConfig = RobustnessConfig()
) {
    // Mirrors the Python flow: UF2 -> raw flash -> RingFS records -> decoded measurements.
    // We keep the same version selection rule (sector header version) and the same basic sanity checks.
    fun parseUf2(
        uf2Bytes: ByteArray,
        firmwareVersionOverride: Int? = null,
        anchorMillis: Long = System.currentTimeMillis(),
        cropStartMillis: Long? = null,
        cropEndMillis: Long? = null,
        forceAnchorShift: Boolean = false
    ): HowfarParseResult {
        val rawFlash = HowfarUf2.convertFromUf2(uf2Bytes)
        val versionFromFlash = RingFs.readVersion(rawFlash)?.takeIf { it != 0 }
        val version = firmwareVersionOverride ?: versionFromFlash ?: database.latestVersion
        val codec = database.codecFor(version)

        val recordsBytes = RingFs.readRecords(rawFlash, codec.recordSize)
        val records = recordsBytes.map { codec.decode(it) }
        val correctedTimestamps = reconstructMonotonicTimestampMillis(
            records.map { it.timestampSeconds * 1000L },
            anchorMillis = anchorMillis,
            forceAnchorShift = forceAnchorShift
        )
        logCropWindow(cropStartMillis = cropStartMillis, cropEndMillis = cropEndMillis)

        var invalidTimestampCount = 0
        var invalidDistanceCount = 0
        var invalidLuxCount = 0
        var croppedByTimeWindowCount = 0
        var croppedByEndWindowCount = 0

        val measurements = ArrayList<ParsedMeasurement>(records.size)
        for ((index, record) in records.withIndex()) {
            val measurement = record.toMeasurement()
            val correctedTimestampMillis = correctedTimestamps[index]
            if (ImportMeasurementRejector.rejectTimestamp(correctedTimestampMillis, robustness)) {
                invalidTimestampCount += 1
                continue
            }
            if (cropStartMillis != null && correctedTimestampMillis < cropStartMillis) {
                croppedByTimeWindowCount += 1
                continue
            }
            if (cropEndMillis != null && correctedTimestampMillis > cropEndMillis) {
                croppedByEndWindowCount += 1
                continue
            }
            if (ImportMeasurementRejector.rejectDistance(
                    distanceCm = measurement.distanceCm,
                    minDistanceCm = DISTANCE_MIN_CM,
                    maxDistanceCm = DISTANCE_MAX_CM,
                    robustness = robustness
                )
            ) {
                invalidDistanceCount += 1
                continue
            }
            if (ImportMeasurementRejector.rejectLux(
                    lux = measurement.lux,
                    minLux = LUX_MIN,
                    maxLux = LUX_MAX,
                    robustness = robustness
                )
            ) {
                invalidLuxCount += 1
                continue
            }
            measurements.add(
                ParsedMeasurement(
                    timestampEpochMillis = correctedTimestampMillis,
                    distanceCm = measurement.distanceCm,
                    lux = measurement.lux
                )
            )
        }

        val parseResult = buildParseResultFromMeasurements(
            measurements = measurements,
            totalRows = records.size,
            rejectedRows = invalidTimestampCount + invalidDistanceCount + invalidLuxCount + croppedByTimeWindowCount + croppedByEndWindowCount,
            invalidTimestampCount = invalidTimestampCount,
            invalidDistanceCount = invalidDistanceCount,
            invalidLuxCount = invalidLuxCount,
            croppedByTimeWindowCount = croppedByTimeWindowCount,
            croppedByEndWindowCount = croppedByEndWindowCount
        )
        return HowfarParseResult(
            firmwareVersion = version,
            measurements = measurements,
            parseResult = parseResult,
            croppedByTimeWindowCount = croppedByTimeWindowCount,
            croppedByEndWindowCount = croppedByEndWindowCount
        )
    }
}

data class HowfarParseResult(
    val firmwareVersion: Int,
    val measurements: List<ParsedMeasurement>,
    val parseResult: SampleParseResult,
    val croppedByTimeWindowCount: Int = 0,
    val croppedByEndWindowCount: Int = 0
)

internal fun reconstructMonotonicTimestampMillis(rawTimestamps: List<Long>): List<Long> {
    return reconstructMonotonicTimestampMillis(
        rawTimestamps,
        anchorMillis = System.currentTimeMillis(),
        forceAnchorShift = false
    )
}

internal fun reconstructMonotonicTimestampMillis(
    rawTimestamps: List<Long>,
    anchorMillis: Long,
    forceAnchorShift: Boolean
): List<Long> {
    if (rawTimestamps.isEmpty()) return emptyList()

    val corrected = ArrayList<Long>(rawTimestamps.size)
    var cumulativeOffsetMillis = 0L
    var previousRawTimestampMillis = rawTimestamps.first()
    var previousCorrectedTimestampMillis = previousRawTimestampMillis
    corrected.add(previousCorrectedTimestampMillis)

    for (i in 1 until rawTimestamps.size) {
        val rawTimestampMillis = rawTimestamps[i]
        if (rawTimestampMillis < previousRawTimestampMillis) {
            cumulativeOffsetMillis += (previousCorrectedTimestampMillis + 1000L) - rawTimestampMillis
        }

        val correctedTimestampMillis = rawTimestampMillis + cumulativeOffsetMillis
        corrected.add(correctedTimestampMillis)
        previousRawTimestampMillis = rawTimestampMillis
        previousCorrectedTimestampMillis = correctedTimestampMillis
    }

    val lastCorrectedTimestamp = corrected.last()
    val plausibleWindowMillis = PLAUSIBLE_ANCHOR_WINDOW_MILLIS
    val anchored = if (forceAnchorShift) {
        val shiftMillis = anchorMillis - lastCorrectedTimestamp
        runCatching {
            Log.i(
                HOWFAR_PARSER_LOG_TAG,
                "anchor applied - manual mode forced shift=${shiftMillis}ms last=$lastCorrectedTimestamp"
            )
        }
        corrected.map { it + shiftMillis }
    } else if (kotlin.math.abs(anchorMillis - lastCorrectedTimestamp) <= plausibleWindowMillis) {
        runCatching {
            Log.i(
                HOWFAR_PARSER_LOG_TAG,
                "anchor skipped - timestamps already plausible, last=$lastCorrectedTimestamp"
            )
        }
        corrected
    } else {
        val shiftMillis = anchorMillis - lastCorrectedTimestamp
        runCatching {
            Log.i(
                HOWFAR_PARSER_LOG_TAG,
                "anchor applied - timestamps out of range, shift=${shiftMillis}ms"
            )
        }
        corrected.map { it + shiftMillis }
    }
    runCatching {
        Log.i(
            HOWFAR_PARSER_LOG_TAG,
            "Anchored HowFar timestamps count=${anchored.size} first=${anchored.first()} last=${anchored.last()}"
        )
    }
    return anchored
}

private fun logCropWindow(cropStartMillis: Long?, cropEndMillis: Long?) {
    runCatching {
        Log.i(
            HOWFAR_PARSER_LOG_TAG,
            "crop window start=${formatEpochMillis(cropStartMillis)} end=${formatEpochMillis(cropEndMillis)}"
        )
    }
}

private fun formatEpochMillis(epochMillis: Long?): String {
    epochMillis ?: return "none"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .toString()
}

private const val HOWFAR_PARSER_LOG_TAG = "HowfarDataParser"
private const val PLAUSIBLE_ANCHOR_WINDOW_MILLIS = 604_800_000L







