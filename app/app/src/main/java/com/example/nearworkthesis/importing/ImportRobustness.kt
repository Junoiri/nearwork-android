package com.example.nearworkthesis.importing

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.analysis.AcceptedDistanceRangeRejector
import com.example.nearworkthesis.domain.analysis.AcceptedLuxRangeRejector
import com.example.nearworkthesis.domain.analysis.NonFiniteValueGuard
import com.example.nearworkthesis.domain.analysis.RobustnessConfig
import com.example.nearworkthesis.domain.analysis.TofZeroDistanceRejector

internal object ImportMeasurementRejector {
    fun rejectTimestamp(timestampEpochMillis: Long, robustness: RobustnessConfig): Boolean {
        return timestampEpochMillis <= 0L || NonFiniteValueGuard.shouldRejectTimestamp(timestampEpochMillis, robustness)
    }

    fun rejectDistance(
        distanceCm: Double,
        minDistanceCm: Double,
        maxDistanceCm: Double,
        robustness: RobustnessConfig
    ): Boolean {
        return TofZeroDistanceRejector.shouldReject(distanceCm, robustness) ||
            NonFiniteValueGuard.shouldRejectNumber(distanceCm, robustness) ||
            AcceptedDistanceRangeRejector.shouldReject(distanceCm, minDistanceCm, maxDistanceCm, robustness)
    }

    fun rejectLux(
        lux: Double,
        minLux: Double,
        maxLux: Double,
        robustness: RobustnessConfig
    ): Boolean {
        return NonFiniteValueGuard.shouldRejectNumber(lux, robustness) ||
            AcceptedLuxRangeRejector.shouldReject(lux, minLux, maxLux, robustness)
    }
}

internal object ImportTimestampDeduplicator {
    fun dedupe(
        measurements: List<ParsedMeasurement>,
        policy: DuplicateResolutionPolicy,
        robustness: RobustnessConfig
    ): List<ParsedMeasurement> {
        if (!robustness.deduplicateTimestamps) return measurements
        return when (policy) {
            DuplicateResolutionPolicy.KEEP_EXISTING -> measurements.distinctBy { it.timestampEpochMillis }
            DuplicateResolutionPolicy.REPLACE_WITH_NEW -> {
                val resolved = LinkedHashMap<Long, ParsedMeasurement>(measurements.size)
                for (measurement in measurements) {
                    resolved[measurement.timestampEpochMillis] = measurement
                }
                resolved.values.toList()
            }
        }
    }
}

internal object ImportGapDetector {
    fun detect(sortedTimestamps: List<Long>, robustness: RobustnessConfig): List<DetectedGap> {
        if (!robustness.detectImportGaps) return emptyList()
        if (sortedTimestamps.size < 2) return emptyList()

        val deltas = ArrayList<Long>(sortedTimestamps.size - 1)
        for (i in 1 until sortedTimestamps.size) {
            val delta = sortedTimestamps[i] - sortedTimestamps[i - 1]
            if (delta > 0) deltas.add(delta)
        }
        if (deltas.isEmpty()) return emptyList()

        val typical = medianMillis(deltas.filter { it in 1L..(30L * 60L * 1000L) }.ifEmpty { deltas })
        // Gap threshold: 5× the typical sampling interval, floored at 60 s.
        // At 5 s sampling this gives 60 s; at 10 s sampling, 60 s.
        // Consistent with NearworkSessionDetector (repository analysis path).
        val threshold = maxOf(typical * 5L, 60L * 1000L)

        val gaps = ArrayList<DetectedGap>()
        for (i in 1 until sortedTimestamps.size) {
            val prev = sortedTimestamps[i - 1]
            val curr = sortedTimestamps[i]
            val delta = curr - prev
            if (delta > threshold) {
                gaps.add(
                    DetectedGap(
                        startEpochMillis = prev,
                        endEpochMillis = curr,
                        durationMillis = delta
                    )
                )
            }
        }
        return gaps.sortedByDescending { it.durationMillis }.take(10)
    }
}

