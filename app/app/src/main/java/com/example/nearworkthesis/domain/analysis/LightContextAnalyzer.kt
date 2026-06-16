package com.example.nearworkthesis.domain.analysis

data class LightContextResult(
    val lowLightMinutes: Int
)

class LightContextAnalyzer {

    fun analyze(
        samples: List<NearworkSample>,
        lowLightThresholdLux: Int,
        robustness: RobustnessConfig = RobustnessConfig()
    ): LightContextResult {
        if (samples.size < 2) return LightContextResult(lowLightMinutes = 0)
        val threshold = lowLightThresholdLux.coerceAtLeast(0).toDouble()

        var lowLightMillis = 0L
        for (i in 0 until samples.lastIndex) {
            val a = samples[i]
            val b = samples[i + 1]
            val dtMillis = b.timestampMillis - a.timestampMillis
            if (dtMillis <= 0L) continue
            if ((!robustness.guardNonFiniteValues || a.lux.isFinite()) && a.lux < threshold) {
                lowLightMillis += dtMillis
            }
        }

        return LightContextResult(
            lowLightMinutes = (lowLightMillis / 60_000L).toInt()
        )
    }
}
