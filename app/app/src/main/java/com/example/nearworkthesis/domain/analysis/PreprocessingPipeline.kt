package com.example.nearworkthesis.domain.analysis

import com.example.nearworkthesis.core.util.AppConstants

data class PreprocessingStats(
    val rawCount: Int,
    val dedupedCount: Int,
    val rejectedCount: Int,
    val outputCount: Int,
    val smoothingWindowSize: Int
)

data class PreprocessingResult(
    val tSeconds: List<Int>,
    val sInterpDistanceCm: List<Double>,
    val sFilterDistanceCm: List<Double>,
    val sInterpIlluminationLux: List<Double>,
    val sFilterIlluminationLux: List<Double>,
    val samples: List<NearworkSample>,
    val interpolatedSamples: List<NearworkSample>,
    val stats: PreprocessingStats
)

class PreprocessingPipeline(
    val config: Config = Config()
) {

    enum class IlluminationSmoothing {
        NONE,
        ROLLING_MEAN_60
    }

    data class Config(
        val minDistanceCm: Double = 10.0,
        val maxDistanceCm: Double = 200.0,
        val minLux: Double = 0.0,
        val maxLux: Double = 50_000.0,
        val smoothingWindowSize: Int = 60,
        val interpolationGapThresholdSeconds: Int = AppConstants.INTERPOLATION_GAP_THRESHOLD_SECONDS,
        val robustness: RobustnessConfig = RobustnessConfig()
    ) {
        init {
            require(minDistanceCm > 0.0)
            require(maxDistanceCm >= minDistanceCm)
            require(minLux >= 0.0)
            require(maxLux >= minLux)
            require(smoothingWindowSize >= 1)
            require(interpolationGapThresholdSeconds >= 1)
        }
    }

    /**
     * Main preprocessing entry for imported raw samples.
     *
     * Step order (MATLAB-shaped):
     * 1) validate raw distance/lux ranges
     * 2) build relative time axis from epoch millis
     * 3) interpolate both Distance and Illumination to 1 Hz (same t grid)
     * 4) smooth Distance with rolling mean (always on)
     * 5) smooth Illumination only if requested by illuminationSmoothing
     */
    fun process(
        rawSamples: List<NearworkSample>,
        illuminationSmoothing: IlluminationSmoothing = IlluminationSmoothing.NONE
    ): PreprocessingResult {
        if (rawSamples.isEmpty()) {
            return PreprocessingResult(
                tSeconds = emptyList(),
                sInterpDistanceCm = emptyList(),
                sFilterDistanceCm = emptyList(),
                sInterpIlluminationLux = emptyList(),
                sFilterIlluminationLux = emptyList(),
                samples = emptyList(),
                interpolatedSamples = emptyList(),
                stats = PreprocessingStats(
                    rawCount = 0,
                    dedupedCount = 0,
                    rejectedCount = 0,
                    outputCount = 0,
                    smoothingWindowSize = config.smoothingWindowSize
                )
            )
        }

        var rejectedCount = 0
        val validated = ArrayList<NearworkSample>(rawSamples.size)
        for (sample in rawSamples) {
            if (NonFiniteValueGuard.shouldRejectTimestamp(sample.timestampMillis, config.robustness)) {
                rejectedCount += 1
                continue
            }
            if (TofZeroDistanceRejector.shouldReject(sample.distanceCm, config.robustness)) {
                rejectedCount += 1
                continue
            }
            if (NonFiniteValueGuard.shouldRejectNumber(sample.distanceCm, config.robustness)) {
                rejectedCount += 1
                continue
            }
            if (AcceptedDistanceRangeRejector.shouldReject(
                    distanceCm = sample.distanceCm,
                    minDistanceCm = config.minDistanceCm,
                    maxDistanceCm = config.maxDistanceCm,
                    robustness = config.robustness
                )
            ) {
                rejectedCount += 1
                continue
            }
            if (NonFiniteValueGuard.shouldRejectNumber(sample.lux, config.robustness)) {
                rejectedCount += 1
                continue
            }
            if (AcceptedLuxRangeRejector.shouldReject(
                    lux = sample.lux,
                    minLux = config.minLux,
                    maxLux = config.maxLux,
                    robustness = config.robustness
                )
            ) {
                rejectedCount += 1
                continue
            }
            validated.add(sample)
        }
        if (validated.isEmpty()) {
            return PreprocessingResult(
                tSeconds = emptyList(),
                sInterpDistanceCm = emptyList(),
                sFilterDistanceCm = emptyList(),
                sInterpIlluminationLux = emptyList(),
                sFilterIlluminationLux = emptyList(),
                samples = emptyList(),
                interpolatedSamples = emptyList(),
                stats = PreprocessingStats(
                    rawCount = rawSamples.size,
                    dedupedCount = 0,
                    rejectedCount = rejectedCount,
                    outputCount = 0,
                    smoothingWindowSize = config.smoothingWindowSize
                )
            )
        }

        val sorted = validated.sortedBy { it.timestampMillis }

        val epochMillis = ArrayList<Long>(sorted.size)
        val Distance = ArrayList<Double>(sorted.size)
        val Lux = ArrayList<Double>(sorted.size)
        for (sample in sorted) {
            epochMillis.add(sample.timestampMillis)
            Distance.add(sample.distanceCm)
            Lux.add(sample.lux)
        }

        val series = preprocessEpochMillis(epochMillis = epochMillis, Distance = Distance, Lux = Lux)
        val firstEpoch = series.firstEpochMillis ?: sorted.first().timestampMillis

        val interpolatedSamples = ArrayList<NearworkSample>(series.t.size)
        val filteredSamples = ArrayList<NearworkSample>(series.t.size)
        // Illumination smoothing is optional:
        // - NONE: keep interpolated illumination
        // - ROLLING_MEAN_60: use one-minute rolling mean illumination
        val resolvedIllumination = when (illuminationSmoothing) {
            IlluminationSmoothing.NONE -> series.s_interp_lux
            IlluminationSmoothing.ROLLING_MEAN_60 -> series.s_filter_lux
        }
        for (i in series.t.indices) {
            val timestamp = firstEpoch + series.t[i] * 1000L
            interpolatedSamples.add(
                NearworkSample(
                    timestampMillis = timestamp,
                    distanceCm = series.s_interp[i],
                    lux = series.s_interp_lux[i]
                )
            )
            filteredSamples.add(
                NearworkSample(
                    timestampMillis = timestamp,
                    distanceCm = series.s_filter[i],
                    lux = resolvedIllumination[i]
                )
            )
        }

        return PreprocessingResult(
            tSeconds = series.t,
            sInterpDistanceCm = series.s_interp,
            sFilterDistanceCm = series.s_filter,
            sInterpIlluminationLux = series.s_interp_lux,
            sFilterIlluminationLux = series.s_filter_lux,
            samples = filteredSamples,
            interpolatedSamples = interpolatedSamples,
            stats = PreprocessingStats(
                rawCount = rawSamples.size,
                dedupedCount = series.duplicateCount,
                rejectedCount = rejectedCount,
                outputCount = filteredSamples.size,
                smoothingWindowSize = config.smoothingWindowSize
            )
        )
    }

    fun processTimeOfDay(
        Time_stamp: List<String>,
        Distance: List<Double>,
        Lux: List<Double> = List(Distance.size) { Double.NaN },
        illuminationSmoothing: IlluminationSmoothing = IlluminationSmoothing.NONE
    ): PreprocessingTimeOfDayResult {
        require(Time_stamp.size == Distance.size) { "Time_stamp and Distance sizes must match." }
        require(Lux.size == Distance.size) { "Lux and Distance sizes must match." }

        // Same pipeline as epoch-based process(), but time axis is built from clock time.
        val series = preprocessTimeOfDayInternal(Time_stamp = Time_stamp, Distance = Distance, Lux = Lux)
        val resolvedIllumination = when (illuminationSmoothing) {
            IlluminationSmoothing.NONE -> series.s_interp_lux
            IlluminationSmoothing.ROLLING_MEAN_60 -> series.s_filter_lux
        }
        return PreprocessingTimeOfDayResult(
            tSeconds = series.t,
            sInterpDistanceCm = series.s_interp,
            sFilterDistanceCm = series.s_filter,
            sInterpIlluminationLux = series.s_interp_lux,
            sFilterIlluminationLux = series.s_filter_lux,
            resolvedIlluminationLux = resolvedIllumination,
            duplicateCount = series.duplicateCount
        )
    }

    private fun preprocessEpochMillis(
        epochMillis: List<Long>,
        Distance: List<Double>,
        Lux: List<Double>
    ): MatlabSeries {
        val deduped = TimestampDeduplicator.dedupeEpochRows(
            epochMillis = epochMillis,
            distance = Distance,
            lux = Lux,
            robustness = config.robustness
        )
        if (deduped.isEmpty()) {
            return MatlabSeries(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                duplicateCount = 0,
                firstEpochMillis = null
            )
        }

        val Time_stamp = deduped.map { it.epochMillis }
        val firstEpoch = Time_stamp.first()
        val time_stamp = Time_stamp.map { (it - firstEpoch) / 1000.0 }
        val dedupedDistance = deduped.map { it.distanceCm }
        val Illumination = deduped.map { it.lux }

        // Shared 1 Hz timeline for both signals.
        val (t, _) = buildT(time_stamp)
        // Synthetic samples: linearly interpolated across gaps ≤ interpolationGapThresholdSeconds
        val imputedDistance = DistanceImputationStep.apply(
            timeStamp = time_stamp,
            values = dedupedDistance,
            t = t,
            robustness = config.robustness
        )
        val blockedDistance = InterpolationGapBlocker.apply(
            timeStamp = time_stamp,
            t = t,
            values = imputedDistance,
            gapThresholdSeconds = config.interpolationGapThresholdSeconds.toDouble(),
            robustness = config.robustness
        )
        // Synthetic samples: linearly interpolated across gaps ≤ interpolationGapThresholdSeconds
        val imputedLux = DistanceImputationStep.apply(
            timeStamp = time_stamp,
            values = Illumination,
            t = t,
            robustness = config.robustness
        )
        val blockedLux = InterpolationGapBlocker.apply(
            timeStamp = time_stamp,
            t = t,
            values = imputedLux,
            gapThresholdSeconds = config.interpolationGapThresholdSeconds.toDouble(),
            robustness = config.robustness
        )
        val s_interp = blockedDistance
        val s_interp_lux = AlsSingleSampleSpikeReplacement.apply(blockedLux, config.robustness)
        val b = DoubleArray(config.smoothingWindowSize) { 1.0 / config.smoothingWindowSize.toDouble() }
        val s_filter = movingAverageNaNSafe(s_interp, windowSize = b.size)
        // Illumination smoothing is computed here so callers can choose NONE vs ROLLING_MEAN_60 later.
        val s_filter_lux = movingAverageNaNSafe(s_interp_lux, windowSize = b.size)

        return MatlabSeries(
            t = t,
            s_interp = s_interp,
            s_filter = s_filter,
            s_interp_lux = s_interp_lux,
            s_filter_lux = s_filter_lux,
            duplicateCount = epochMillis.size - deduped.size,
            firstEpochMillis = firstEpoch
        )
    }

    private fun preprocessTimeOfDayInternal(
        Time_stamp: List<String>,
        Distance: List<Double>,
        Lux: List<Double>
    ): MatlabSeries {
        if (Time_stamp.isEmpty()) {
            return MatlabSeries(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                duplicateCount = 0,
                firstEpochMillis = null
            )
        }

        // Convert clock timestamps to cumulative seconds and keep monotonicity across midnight.
        val Date_in_seconds = ArrayList<Double>(Time_stamp.size)
        var dayOffsetSeconds = 0.0
        var previousClockSeconds: Double? = null
        for (raw in Time_stamp) {
            val clockSeconds = parseClockSeconds(raw)
            if (previousClockSeconds != null && clockSeconds < previousClockSeconds) {
                dayOffsetSeconds += 24.0 * 3600.0
            }
            Date_in_seconds.add(clockSeconds + dayOffsetSeconds)
            previousClockSeconds = clockSeconds
        }
        val base = Date_in_seconds.first()
        val time_stamp = Date_in_seconds.map { it - base }

        val deduped = TimestampDeduplicator.dedupeTimeRows(
            timeStamp = time_stamp,
            distance = Distance,
            lux = Lux,
            robustness = config.robustness
        )
        val x = deduped.map { it.timeSec }
        val yDistance = deduped.map { it.distanceCm }
        val Illumination = deduped.map { it.lux }

        // Shared 1 Hz timeline for both Distance and Illumination.
        val (t, _) = buildT(x)
        val imputedDistance = DistanceImputationStep.apply(
            timeStamp = x,
            values = yDistance,
            t = t,
            robustness = config.robustness
        )
        val blockedDistance = InterpolationGapBlocker.apply(
            timeStamp = x,
            t = t,
            values = imputedDistance,
            gapThresholdSeconds = config.interpolationGapThresholdSeconds.toDouble(),
            robustness = config.robustness
        )
        val imputedLux = DistanceImputationStep.apply(
            timeStamp = x,
            values = Illumination,
            t = t,
            robustness = config.robustness
        )
        val blockedLux = InterpolationGapBlocker.apply(
            timeStamp = x,
            t = t,
            values = imputedLux,
            gapThresholdSeconds = config.interpolationGapThresholdSeconds.toDouble(),
            robustness = config.robustness
        )
        val s_interp = blockedDistance
        val s_interp_lux = AlsSingleSampleSpikeReplacement.apply(blockedLux, config.robustness)
        val b = DoubleArray(config.smoothingWindowSize) { 1.0 / config.smoothingWindowSize.toDouble() }
        val s_filter = movingAverageNaNSafe(s_interp, windowSize = b.size)
        val s_filter_lux = movingAverageNaNSafe(s_interp_lux, windowSize = b.size)

        return MatlabSeries(
            t = t,
            s_interp = s_interp,
            s_filter = s_filter,
            s_interp_lux = s_interp_lux,
            s_filter_lux = s_filter_lux,
            duplicateCount = Time_stamp.size - deduped.size,
            firstEpochMillis = null
        )
    }
}

data class PreprocessingTimeOfDayResult(
    val tSeconds: List<Int>,
    val sInterpDistanceCm: List<Double>,
    val sFilterDistanceCm: List<Double>,
    val sInterpIlluminationLux: List<Double>,
    val sFilterIlluminationLux: List<Double>,
    val resolvedIlluminationLux: List<Double>,
    val duplicateCount: Int
)

internal data class EpochRow(
    val epochMillis: Long,
    val distanceCm: Double,
    val lux: Double
)

internal data class TimeRow(
    val timeSec: Double,
    val distanceCm: Double,
    val lux: Double
)

private data class MatlabSeries(
    val t: List<Int>,
    val s_interp: List<Double>,
    val s_filter: List<Double>,
    val s_interp_lux: List<Double>,
    val s_filter_lux: List<Double>,
    val duplicateCount: Int,
    val firstEpochMillis: Long?
)

private fun buildT(time_stamp: List<Double>): Pair<List<Int>, Int> {
    val maxSec = kotlin.math.floor(time_stamp.maxOrNull() ?: 0.0).toInt().coerceAtLeast(0)
    val t = (0..maxSec).toList()
    val L = t.size
    return t to L
}

private fun movingAverageNaNSafe(
    s_interp: List<Double>,
    windowSize: Int
): List<Double> {
    // Causal rolling mean (MATLAB filter-like):
    // - runs in O(n) with rolling sum/count
    // - ignores NaNs inside the window
    // - outputs NaN if the window has no valid samples
    val s_filter = MutableList(s_interp.size) { Double.NaN }
    var rollingSum = 0.0
    var rollingCount = 0

    for (i in s_interp.indices) {
        val inVal = s_interp[i]
        if (inVal.isFinite()) {
            rollingSum += inVal
            rollingCount += 1
        }

        val outIndex = i - windowSize
        if (outIndex >= 0) {
            val outVal = s_interp[outIndex]
            if (outVal.isFinite()) {
                rollingSum -= outVal
                rollingCount -= 1
            }
        }

        s_filter[i] = if (rollingCount > 0) rollingSum / rollingCount.toDouble() else Double.NaN
    }

    return s_filter
}

private fun parseClockSeconds(rawValue: String): Double {
    val trimmed = rawValue.trim()
    val normalized = trimmed.substringAfterLast('T').substringAfterLast(' ')
    val split = normalized.split(':')
    if (split.size < 2) throw IllegalArgumentException("Unsupported time-of-day: $rawValue")

    val hour = split[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid hour in: $rawValue")
    val minute = split[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid minute in: $rawValue")
    val second = if (split.size >= 3) split[2].toDoubleOrNull() ?: 0.0 else 0.0

    if (hour !in 0..23 || minute !in 0..59 || second < 0.0 || second >= 60.0) {
        throw IllegalArgumentException("Unsupported time-of-day: $rawValue")
    }
    return hour * 3600.0 + minute * 60.0 + second
}
