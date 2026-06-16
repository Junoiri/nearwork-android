package com.example.nearworkthesis.domain.analysis

class NearworkSessionDetector(
    private val diopterHoursCalculator: DiopterHoursCalculator = DiopterHoursCalculator()
) {

    // I keep the raw grouped samples around so every session-based metric can share one splitter.
    data class DetectedSessionWindow(
        val startTimestampMillis: Long,
        val endTimestampMillis: Long,
        val durationSeconds: Long,
        val samples: List<NearworkSample>
    )

    data class Config(
        val nearworkDistanceThresholdCm: Double = 60.0,
        val breakGapSeconds: Int = 60,
        val minSessionDurationSeconds: Int = 60
    ) {
        init {
            require(nearworkDistanceThresholdCm > 0.0)
            require(breakGapSeconds >= 1)
            require(minSessionDurationSeconds >= 1)
        }
    }

    fun detectSessions(
        processedSamples: List<NearworkSample>,
        lowLightThresholdLux: Int,
        config: Config = Config(),
        robustness: RobustnessConfig = RobustnessConfig()
    ): List<NearworkSession> {
        // I reuse the same session windows here so summaries and NRS cannot silently diverge.
        return detectSessionWindows(processedSamples, config, robustness).map { window ->
            val distances = window.samples.map { it.distanceCm }
            val avgDistance = distances.average()
            val minDistance = distances.minOrNull() ?: avgDistance
            val diopterHours = diopterHoursCalculator.calculate(window.samples, robustness).totalDiopterHours
            val lowLightSeconds = computeLowLightSeconds(
                samples = window.samples,
                lowLightThresholdLux = lowLightThresholdLux,
                robustness = robustness
            )

            NearworkSession(
                startTimestampMillis = window.startTimestampMillis,
                endTimestampMillis = window.endTimestampMillis,
                durationSeconds = window.durationSeconds,
                avgDistanceCm = avgDistance,
                minDistanceCm = minDistance,
                diopterHoursInSession = diopterHours,
                lowLightSecondsInSession = lowLightSeconds
            )
        }
    }

    fun detectSessionWindows(
        processedSamples: List<NearworkSample>,
        config: Config = Config(),
        robustness: RobustnessConfig = RobustnessConfig()
    ): List<DetectedSessionWindow> {
        if (processedSamples.size < 2) return emptyList()

        val sorted = processedSamples.sortedBy { it.timestampMillis }
        val nearworkThreshold = config.nearworkDistanceThresholdCm
        // I need this in millis because sample timestamps are stored as epoch-millis.
        val breakGapThresholdMillis = config.breakGapSeconds.toLong() * 1000L
        // I convert the minimum once so the session filter stays unit-safe all the way through.
        val minSessionDurationMillis = config.minSessionDurationSeconds.toLong() * 1000L
        val sessions = ArrayList<DetectedSessionWindow>()

        var currentStart: Long? = null
        var lastNearworkTimestamp: Long? = null
        val currentSamples = ArrayList<NearworkSample>(256)

        fun flushIfValid() {
            val start = currentStart
            val end = lastNearworkTimestamp
            if (start == null || end == null) return

            // I keep the raw span in millis here so the duration filter stays in the same unit.
            val durationMillis = (end - start).coerceAtLeast(0L)
            // I reject short spans in millis because the configured minimum is defined in seconds.
            if (durationMillis < minSessionDurationMillis) return

            // I copy the samples here so later resets cannot mutate a stored session by accident.
            sessions.add(
                DetectedSessionWindow(
                    startTimestampMillis = start,
                    endTimestampMillis = end,
                    durationSeconds = durationMillis / 1000L,
                    samples = currentSamples.toList()
                )
            )
        }

        fun reset() {
            currentStart = null
            lastNearworkTimestamp = null
            currentSamples.clear()
        }

        for (sample in sorted) {
            val distanceCm = sample.distanceCm
            val isNearwork = when {
                robustness.guardNonFiniteValues && !distanceCm.isFinite() -> false
                else -> distanceCm <= nearworkThreshold
            }
            if (!isNearwork) {
                if (currentStart != null) {
                    flushIfValid()
                    reset()
                }
                continue
            }

            val start = currentStart
            val lastTs = lastNearworkTimestamp
            if (start == null) {
                currentStart = sample.timestampMillis
            // I split only when the real wall-clock gap exceeds the configured threshold in seconds.
            } else if (lastTs != null && sample.timestampMillis - lastTs > breakGapThresholdMillis) {
                flushIfValid()
                reset()
                currentStart = sample.timestampMillis
            }

            currentSamples.add(sample)
            lastNearworkTimestamp = sample.timestampMillis
        }

        if (currentStart != null) {
            flushIfValid()
        }

        return sessions
    }

    fun findLongestSession(sessions: List<NearworkSession>): NearworkSession? {
        return sessions.maxByOrNull { it.durationSeconds }
    }
}

private fun computeLowLightSeconds(
    samples: List<NearworkSample>,
    lowLightThresholdLux: Int,
    robustness: RobustnessConfig
): Long {
    if (samples.size < 2) return 0L
    val threshold = lowLightThresholdLux.coerceAtLeast(0).toDouble()
    var lowLightSeconds = 0L
    for (i in 0 until samples.lastIndex) {
        val a = samples[i]
        val b = samples[i + 1]
        val dtSeconds = (b.timestampMillis - a.timestampMillis) / 1000L
        if (dtSeconds <= 0L) continue
        if ((!robustness.guardNonFiniteValues || a.lux.isFinite()) && a.lux < threshold) {
            lowLightSeconds += dtSeconds
        }
    }
    return lowLightSeconds
}
