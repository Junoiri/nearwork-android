package com.example.nearworkthesis.importing

import java.io.InputStream
import java.io.InputStreamReader
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.example.nearworkthesis.domain.analysis.RobustnessConfig

data class ParsedMeasurement(
    val timestampEpochMillis: Long,
    val distanceCm: Double,
    val lux: Double
)

data class DetectedGap(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMillis: Long
)

data class SampleParseResult(
    val measurements: List<ParsedMeasurement>,
    val totalRows: Int,
    val rejectedRows: Int,
    val invalidTimestampCount: Int,
    val invalidDistanceCount: Int,
    val invalidLuxCount: Int,
    val croppedByTimeWindowCount: Int = 0,
    val croppedByEndWindowCount: Int = 0,
    val duplicatesInFileCount: Int,
    val gaps: List<DetectedGap>,
    val firstTimestampEpochMillis: Long?,
    val lastTimestampEpochMillis: Long?
)

class SampleCsvParser(
    private val timestampFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    ),
    private val robustness: RobustnessConfig = RobustnessConfig()
) {

    fun parse(inputStream: InputStream): SampleParseResult {
        InputStreamReader(inputStream, Charsets.UTF_8).buffered().useLines { lines ->
            val iterator = lines.iterator()
            if (!iterator.hasNext()) {
                return SampleParseResult(
                    measurements = emptyList(),
                    totalRows = 0,
                    rejectedRows = 0,
                    invalidTimestampCount = 0,
                    invalidDistanceCount = 0,
                    invalidLuxCount = 0,
                    croppedByTimeWindowCount = 0,
                    croppedByEndWindowCount = 0,
                    duplicatesInFileCount = 0,
                    gaps = emptyList(),
                    firstTimestampEpochMillis = null,
                    lastTimestampEpochMillis = null
                )
            }

            val header = iterator.next()
            val indexes = resolveIndexes(header)

            var totalRows = 0
            var rejectedRows = 0
            var invalidTimestampCount = 0
            var invalidDistanceCount = 0
            var invalidLuxCount = 0
            var duplicatesInFileCount = 0

            val measurements = ArrayList<ParsedMeasurement>()
            val seenTimestamps = HashSet<Long>()

            while (iterator.hasNext()) {
                val line = iterator.next()
                if (line.isBlank()) continue
                totalRows += 1

                val values = parseCsvLine(line)
                val timestampString = values.getOrNull(indexes.timestamp)?.trim().orEmpty()
                val distanceString = values.getOrNull(indexes.distance)?.trim().orEmpty()
                val luxString = values.getOrNull(indexes.lux)?.trim().orEmpty()

                val timestampMillis = parseTimestamp(timestampString)
                if (timestampMillis == null) {
                    rejectedRows += 1
                    invalidTimestampCount += 1
                    continue
                }

                val parsedDistance = distanceString.toDoubleOrNull()?.let(indexes::normalizeDistanceCm)
                if (parsedDistance == null || ImportMeasurementRejector.rejectDistance(
                        distanceCm = parsedDistance,
                        minDistanceCm = 10.0,
                        maxDistanceCm = 200.0,
                        robustness = robustness
                    )
                ) {
                    rejectedRows += 1
                    invalidDistanceCount += 1
                    continue
                }

                val parsedLux = luxString.toDoubleOrNull()
                if (parsedLux == null || ImportMeasurementRejector.rejectLux(
                        lux = parsedLux,
                        minLux = 0.0,
                        maxLux = 50_000.0,
                        robustness = robustness
                    )
                ) {
                    rejectedRows += 1
                    invalidLuxCount += 1
                    continue
                }

                val measurement = ParsedMeasurement(
                    timestampEpochMillis = timestampMillis,
                    distanceCm = parsedDistance,
                    lux = parsedLux
                )

                if (robustness.deduplicateTimestamps && !seenTimestamps.add(timestampMillis)) {
                    duplicatesInFileCount += 1
                }
                measurements.add(measurement)
            }

            val sortedMeasurements = measurements.sortedBy { it.timestampEpochMillis }
            val timestamps = sortedMeasurements.map { it.timestampEpochMillis }
            val gaps = detectGaps(timestamps, robustness)
            val firstTimestamp = timestamps.firstOrNull()
            val lastTimestamp = timestamps.lastOrNull()

            return SampleParseResult(
                measurements = sortedMeasurements,
                totalRows = totalRows,
                rejectedRows = rejectedRows,
                invalidTimestampCount = invalidTimestampCount,
                invalidDistanceCount = invalidDistanceCount,
                invalidLuxCount = invalidLuxCount,
                croppedByTimeWindowCount = 0,
                croppedByEndWindowCount = 0,
                duplicatesInFileCount = duplicatesInFileCount,
                gaps = gaps,
                firstTimestampEpochMillis = firstTimestamp,
                lastTimestampEpochMillis = lastTimestamp
            )
        }
    }

    private fun resolveIndexes(header: String): ColumnIndexes {
        val columns = parseCsvLine(header)
        val timestampIndex = columns.indexOfFirst { it.trim().equals("timestamp", ignoreCase = true) }
        val distanceCmIndex = columns.indexOfFirst { it.trim().equals("distance_cm", ignoreCase = true) }
        val distanceMmIndex = columns.indexOfFirst { it.trim().equals("tof_distance_mm", ignoreCase = true) }
        val luxIndex = columns.indexOfFirst {
            val normalized = it.trim()
            normalized.equals("illumination_lux", ignoreCase = true) ||
                normalized.equals("alx_lx", ignoreCase = true)
        }

        val distanceIndex: Int
        val distanceUnit: DistanceUnit
        when {
            distanceCmIndex != -1 -> {
                distanceIndex = distanceCmIndex
                distanceUnit = DistanceUnit.CENTIMETERS
            }
            distanceMmIndex != -1 -> {
                distanceIndex = distanceMmIndex
                distanceUnit = DistanceUnit.MILLIMETERS
            }
            else -> {
                distanceIndex = -1
                distanceUnit = DistanceUnit.CENTIMETERS
            }
        }

        if (timestampIndex == -1 || distanceIndex == -1 || luxIndex == -1) {
            throw IllegalArgumentException("Missing required CSV columns.")
        }

        return ColumnIndexes(
            timestamp = timestampIndex,
            distance = distanceIndex,
            lux = luxIndex,
            distanceUnit = distanceUnit
        )
    }

    private fun parseTimestamp(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        trimmed.toLongOrNull()?.let { epoch ->
            return if (epoch > 10_000_000_000L) epoch else epoch * 1000L
        }

        for (formatter in timestampFormatters) {
            try {
                val timestamp = LocalDateTime.parse(trimmed, formatter)
                return timestamp.toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeException) {
                // Try next
            }
        }

        return try {
            OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

}

fun buildParseResultFromMeasurements(
    measurements: List<ParsedMeasurement>,
    totalRows: Int = measurements.size,
    rejectedRows: Int = 0,
    invalidTimestampCount: Int = 0,
    invalidDistanceCount: Int = 0,
    invalidLuxCount: Int = 0,
    croppedByTimeWindowCount: Int = 0,
    croppedByEndWindowCount: Int = 0
): SampleParseResult {
    val sortedMeasurements = measurements.sortedBy { it.timestampEpochMillis }
    val timestamps = sortedMeasurements.map { it.timestampEpochMillis }
    val duplicatesInFileCount = timestamps.size - timestamps.distinct().size
    val gaps = detectGaps(timestamps, RobustnessConfig())
    val firstTimestamp = timestamps.firstOrNull()
    val lastTimestamp = timestamps.lastOrNull()

    return SampleParseResult(
        measurements = sortedMeasurements,
        totalRows = totalRows,
        rejectedRows = rejectedRows,
        invalidTimestampCount = invalidTimestampCount,
        invalidDistanceCount = invalidDistanceCount,
        invalidLuxCount = invalidLuxCount,
        croppedByTimeWindowCount = croppedByTimeWindowCount,
        croppedByEndWindowCount = croppedByEndWindowCount,
        duplicatesInFileCount = duplicatesInFileCount,
        gaps = gaps,
        firstTimestampEpochMillis = firstTimestamp,
        lastTimestampEpochMillis = lastTimestamp
    )
}

private data class ColumnIndexes(
    val timestamp: Int,
    val distance: Int,
    val lux: Int,
    val distanceUnit: DistanceUnit
) {
    fun normalizeDistanceCm(rawDistance: Double): Double {
        return when (distanceUnit) {
            DistanceUnit.CENTIMETERS -> rawDistance
            DistanceUnit.MILLIMETERS -> rawDistance / 10.0
        }
    }
}

private enum class DistanceUnit {
    CENTIMETERS,
    MILLIMETERS
}

private fun parseCsvLine(line: String): List<String> {
    if (line.isEmpty()) return emptyList()
    val out = ArrayList<String>(8)
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when (c) {
            '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 1
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> {
                if (inQuotes) {
                    current.append(c)
                } else {
                    out.add(current.toString())
                    current.setLength(0)
                }
            }
            else -> current.append(c)
        }
        i += 1
    }
    out.add(current.toString())
    return out
}

internal fun detectGaps(sortedTimestamps: List<Long>, robustness: RobustnessConfig = RobustnessConfig()): List<DetectedGap> {
    return ImportGapDetector.detect(sortedTimestamps, robustness)
}

internal fun medianMillis(values: List<Long>): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[mid]
    } else {
        (sorted[mid - 1] + sorted[mid]) / 2L
    }
}

