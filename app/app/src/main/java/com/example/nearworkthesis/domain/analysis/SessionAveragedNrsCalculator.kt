/** Computes NRS per detected session and collapses results so every screen uses a single aggregation scope. */
package com.example.nearworkthesis.domain.analysis

data class SessionAveragedNrsResult(
    val sessionEntries: List<SessionNrsEntry>,
    val sessionResults: List<NrsResult>,
    val aggregatedResult: NrsResult
)

data class SessionNrsEntry(
    val sessionWindow: NearworkSessionDetector.DetectedSessionWindow,
    val nrsResult: NrsResult
)

class SessionAveragedNrsCalculator(
    private val sessionDetector: NearworkSessionDetector = NearworkSessionDetector(),
    private val nearworkRiskScoreCalculator: NearworkRiskScoreCalculator = NearworkRiskScoreCalculator()
) {

    fun calculate(
        samples: List<NearworkSample>,
        thresholds: AnalysisThresholds,
        robustness: RobustnessConfig = RobustnessConfig()
    ): NrsResult {
        // I keep the old return shape here so callers that only need one number stay simple.
        return calculateWithSessions(samples, thresholds, robustness).aggregatedResult
    }

    fun calculateWithSessions(
        samples: List<NearworkSample>,
        thresholds: AnalysisThresholds,
        robustness: RobustnessConfig = RobustnessConfig()
    ): SessionAveragedNrsResult {
        // Intentional asymmetry: NRS is computed exclusively over samples
        // within detected nearwork sessions (distanceCm <= nearworkDistanceThreshold,
        // default 60 cm). Daily D-h, by contrast, integrates over all valid
        // processed samples for the day, including non-nearwork periods.
        // D-h = cumulative accommodative load; NRS = within-session exposure severity.
        // See NearworkPostProcessor.kt for the D-h computation path.
        // I derive the groups from the detector so the split logic only lives in one place.
        val sessionWindows = sessionDetector.detectSessionWindows(
            processedSamples = samples,
            config = thresholds.toSessionDetectorConfig(),
            robustness = robustness
        )
        if (sessionWindows.isEmpty()) {
            return SessionAveragedNrsResult(
                sessionEntries = emptyList(),
                sessionResults = emptyList(),
                aggregatedResult = NrsResult(nrs = 0.0, sampleCount = 0, meanLuxDuringNearwork = null)
            )
        }

        // I keep the per-session pairing so later UI can show NRS beside the exact session window.
        val sessionEntries = sessionWindows.mapNotNull { window ->
            val durationHours = (window.endTimestampMillis - window.startTimestampMillis) / MILLIS_PER_HOUR
            val result = nearworkRiskScoreCalculator.calculate(
                samples = window.samples,
                durationHours = durationHours,
                robustness = robustness
            )
            if (result.sampleCount <= 0) null else SessionNrsEntry(
                sessionWindow = window,
                nrsResult = result
            )
        }
        // I flatten the results here because the aggregate still works over plain NRS payloads.
        val sessionResults = sessionEntries.map { it.nrsResult }
        if (sessionEntries.isEmpty()) {
            return SessionAveragedNrsResult(
                sessionEntries = emptyList(),
                sessionResults = emptyList(),
                aggregatedResult = NrsResult(nrs = 0.0, sampleCount = 0, meanLuxDuringNearwork = null)
            )
        }

        // Each session NRS already includes its own duration, so daily NRS is the sum across sessions.
        val totalSessionNrs = sessionResults.sumOf { it.nrs }
        // I still report the total valid samples so the caller keeps the same scale for context.
        val totalValidSamples = sessionResults.sumOf { it.sampleCount }
        // I recompute the nearwork lux mean from the kept sessions so the companion field matches the new scope.
        val meanLuxDuringNearwork = meanLuxAcrossSessions(sessionWindows, robustness)

        return SessionAveragedNrsResult(
            sessionEntries = sessionEntries,
            sessionResults = sessionResults,
            aggregatedResult = NrsResult(
                nrs = totalSessionNrs,
                sampleCount = totalValidSamples,
                meanLuxDuringNearwork = meanLuxDuringNearwork
            )
        )
    }

    private fun meanLuxAcrossSessions(
        sessionWindows: List<NearworkSessionDetector.DetectedSessionWindow>,
        robustness: RobustnessConfig
    ): Double? {
        var nearworkLuxSum = 0.0
        var nearworkLuxCount = 0

        for (window in sessionWindows) {
            for (sample in window.samples) {
                val distanceCm = sample.distanceCm
                val luxInput = sample.lux
                if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) {
                    continue
                }
                if (robustness.rejectOutOfRangeDistance &&
                    (distanceCm <= NearworkRiskScoreCalculator.MIN_VALID_DISTANCE_CM ||
                        distanceCm > NearworkRiskScoreCalculator.MAX_VALID_DISTANCE_CM)
                ) {
                    continue
                }
                if (distanceCm >= NearworkRiskScoreCalculator.NEARWORK_DISTANCE_CM ||
                    (robustness.guardNonFiniteValues && !luxInput.isFinite())
                ) {
                    continue
                }

                // I clamp the stored lux the same way as the core calculator so the side metric stays aligned.
                val luxForMean = if (robustness.rejectOutOfRangeLux) {
                    luxInput.coerceAtLeast(NearworkRiskScoreCalculator.MIN_LUX)
                } else {
                    luxInput
                }
                nearworkLuxSum += luxForMean
                nearworkLuxCount += 1
            }
        }

        return if (nearworkLuxCount > 0) nearworkLuxSum / nearworkLuxCount.toDouble() else null
    }
}

private const val MILLIS_PER_HOUR = 3_600_000.0

// I keep the threshold mapping next to the aggregator so the view models stay dumb.
private fun AnalysisThresholds.toSessionDetectorConfig(): NearworkSessionDetector.Config {
    return NearworkSessionDetector.Config(
        nearworkDistanceThresholdCm = nearworkDistanceThresholdCm.toDouble(),
        breakGapSeconds = breakGapSeconds,
        minSessionDurationSeconds = minSessionDurationSeconds
    )
}
