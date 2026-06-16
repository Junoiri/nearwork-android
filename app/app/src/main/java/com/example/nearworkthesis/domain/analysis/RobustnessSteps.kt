package com.example.nearworkthesis.domain.analysis

internal object TofZeroDistanceRejector {
    fun shouldReject(distanceCm: Double, robustness: RobustnessConfig): Boolean {
        return robustness.rejectTofZeroDistance && distanceCm == 0.0
    }
}

internal object NonFiniteValueGuard {
    fun shouldRejectTimestamp(timestampMillis: Long, robustness: RobustnessConfig): Boolean {
        if (!robustness.guardNonFiniteValues) return false
        return timestampMillis == Long.MIN_VALUE || timestampMillis == Long.MAX_VALUE
    }

    fun shouldRejectNumber(value: Double, robustness: RobustnessConfig): Boolean {
        return robustness.guardNonFiniteValues && !value.isFinite()
    }

    fun isAcceptableDistance(distanceCm: Double, robustness: RobustnessConfig): Boolean {
        return !robustness.guardNonFiniteValues || distanceCm.isFinite()
    }

    fun isAcceptableLux(lux: Double, robustness: RobustnessConfig): Boolean {
        return !robustness.guardNonFiniteValues || lux.isFinite()
    }
}

internal object AcceptedDistanceRangeRejector {
    fun shouldReject(
        distanceCm: Double,
        minDistanceCm: Double,
        maxDistanceCm: Double,
        robustness: RobustnessConfig
    ): Boolean {
        if (!robustness.rejectOutOfRangeDistance) return false
        return distanceCm < minDistanceCm || distanceCm > maxDistanceCm
    }
}

internal object AcceptedLuxRangeRejector {
    fun shouldReject(
        lux: Double,
        minLux: Double,
        maxLux: Double,
        robustness: RobustnessConfig
    ): Boolean {
        if (!robustness.rejectOutOfRangeLux) return false
        return lux < minLux || lux > maxLux
    }
}

internal object TimestampDeduplicator {
    fun dedupeEpochRows(
        epochMillis: List<Long>,
        distance: List<Double>,
        lux: List<Double>,
        robustness: RobustnessConfig
    ): List<EpochRow> {
        if (!robustness.deduplicateTimestamps) {
            return epochMillis.indices.map { index ->
                EpochRow(epochMillis[index], distance[index], lux[index])
            }.sortedBy { it.epochMillis }
        }

        val keyed = LinkedHashMap<Long, EpochRow>(epochMillis.size)
        for (i in epochMillis.indices) {
            keyed[epochMillis[i]] = EpochRow(epochMillis[i], distance[i], lux[i])
        }
        return keyed.values.sortedBy { it.epochMillis }
    }

    fun dedupeTimeRows(
        timeStamp: List<Double>,
        distance: List<Double>,
        lux: List<Double>,
        robustness: RobustnessConfig
    ): List<TimeRow> {
        if (!robustness.deduplicateTimestamps) {
            return timeStamp.indices.map { index ->
                TimeRow(timeStamp[index], distance[index], lux[index])
            }.sortedBy { it.timeSec }
        }

        val keyed = LinkedHashMap<Double, TimeRow>(timeStamp.size)
        for (i in timeStamp.indices) {
            keyed[timeStamp[i]] = TimeRow(timeStamp[i], distance[i], lux[i])
        }
        return keyed.values.sortedBy { it.timeSec }
    }
}

internal object DistanceImputationStep {
    fun apply(
        timeStamp: List<Double>,
        values: List<Double>,
        t: List<Int>,
        robustness: RobustnessConfig
    ): List<Double> {
        if (timeStamp.isEmpty()) return List(t.size) { Double.NaN }
        if (!robustness.distanceImputation) {
            val exact = MutableList(t.size) { Double.NaN }
            val exactBySecond = HashMap<Int, Double>(timeStamp.size)
            for (index in timeStamp.indices) {
                val second = timeStamp[index].toInt()
                if (timeStamp[index] == second.toDouble()) {
                    exactBySecond[second] = values[index]
                }
            }
            for (i in t.indices) {
                exact[i] = exactBySecond[t[i]] ?: Double.NaN
            }
            return exact
        }

        return linearInterpolation(timeStamp = timeStamp, values = values, t = t)
    }

    private fun linearInterpolation(
        timeStamp: List<Double>,
        values: List<Double>,
        t: List<Int>
    ): List<Double> {
        val interpolated = MutableList(t.size) { Double.NaN }
        var j = 0
        for (i in t.indices) {
            val target = t[i].toDouble()
            while (j + 1 < timeStamp.size && timeStamp[j + 1] <= target) {
                j += 1
            }
            if (j >= timeStamp.size) break

            val x0 = timeStamp[j]
            val y0 = values[j]
            if (target == x0) {
                interpolated[i] = y0
                continue
            }
            if (j + 1 >= timeStamp.size) continue

            val x1 = timeStamp[j + 1]
            val y1 = values[j + 1]
            if (target < x0 || target > x1) continue

            val gap = x1 - x0
            if (gap <= 0.0) continue

            val alpha = (target - x0) / gap
            interpolated[i] = y0 + (y1 - y0) * alpha
        }
        return interpolated
    }
}

internal object InterpolationGapBlocker {
    fun apply(
        timeStamp: List<Double>,
        t: List<Int>,
        values: List<Double>,
        gapThresholdSeconds: Double,
        robustness: RobustnessConfig
    ): List<Double> {
        if (!robustness.blockInterpolationAcrossGaps || timeStamp.size < 2) return values

        val blocked = values.toMutableList()
        for (index in 0 until timeStamp.lastIndex) {
            val x0 = timeStamp[index]
            val x1 = timeStamp[index + 1]
            if (x1 - x0 <= gapThresholdSeconds) continue
            for (targetIndex in t.indices) {
                val target = t[targetIndex].toDouble()
                if (target > x0 && target < x1) {
                    blocked[targetIndex] = Double.NaN
                }
            }
        }
        return blocked
    }
}

internal object AlsSingleSampleSpikeReplacement {
    fun apply(luxSeries: List<Double>, robustness: RobustnessConfig): List<Double> {
        if (!robustness.replaceAlsSingleSampleSpikes) return luxSeries
        if (luxSeries.size < 3) return luxSeries

        val threshold = robustness.alsSingleSampleSpikeThresholdLux
        if (threshold < 0.0 || (robustness.guardNonFiniteValues && !threshold.isFinite())) return luxSeries

        val corrected = luxSeries.toMutableList()
        for (index in 1 until luxSeries.lastIndex) {
            val previous = luxSeries[index - 1]
            val current = luxSeries[index]
            val next = luxSeries[index + 1]

            if (robustness.guardNonFiniteValues &&
                (!previous.isFinite() || !current.isFinite() || !next.isFinite())
            ) {
                continue
            }

            if (current - previous > threshold && current - next > threshold) {
                corrected[index] = (previous + next) / 2.0
            }
        }
        return corrected
    }
}
