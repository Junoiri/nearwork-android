package com.example.nearworkthesis.domain.analysis

data class DiopterHoursInterval(
    val startMillis: Long,
    val endMillis: Long,
    val diopterHours: Double
)

data class DiopterHoursResult(
    val totalDiopterHours: Double,
    val intervals: List<DiopterHoursInterval>
)

class DiopterHoursCalculator {

    /**
     * Computes Dioptre-hours as a discrete approximation of:
     * D·h = ∫ (1 / d(t)) dt
     *
     * where d(t) is viewing distance in meters and time is in hours.
     *
     * Integration method: trapezoidal rule on diopters between consecutive samples
     * (i.e., average diopters across the interval).
     */
    fun calculate(
        samples: List<NearworkSample>,
        robustness: RobustnessConfig = RobustnessConfig()
    ): DiopterHoursResult {
        if (samples.size < 2) return DiopterHoursResult(totalDiopterHours = 0.0, intervals = emptyList())

        var total = 0.0
        val intervals = ArrayList<DiopterHoursInterval>(samples.size - 1)

        for (i in 0 until samples.lastIndex) {
            val a = samples[i]
            val b = samples[i + 1]
            val dtMillis = b.timestampMillis - a.timestampMillis
            if (dtMillis <= 0L) continue

            val dioptersA = dioptersOrNull(a.distanceCm, robustness) ?: continue
            val dioptersB = dioptersOrNull(b.distanceCm, robustness) ?: continue

            val dtHours = dtMillis / 3_600_000.0
            val intervalDh = ((dioptersA + dioptersB) / 2.0) * dtHours

            if ((!robustness.guardNonFiniteValues || intervalDh.isFinite()) && intervalDh >= 0.0) {
                intervals.add(
                    DiopterHoursInterval(
                        startMillis = a.timestampMillis,
                        endMillis = b.timestampMillis,
                        diopterHours = intervalDh
                    )
                )
                total += intervalDh
            }
        }

        return DiopterHoursResult(totalDiopterHours = total, intervals = intervals)
    }
}

private fun dioptersOrNull(distanceCm: Double, robustness: RobustnessConfig): Double? {
    if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) return null
    val meters = distanceCm / 100.0
    if (robustness.rejectOutOfRangeDistance && meters <= 0.0) return null
    return 1.0 / meters
}
