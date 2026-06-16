/** Groups defensive sensor-workaround flags so they cannot leak into core metric calculations. */
package com.example.nearworkthesis.domain.analysis
data class RobustnessConfig(
    val rejectTofZeroDistance: Boolean = true,
    val rejectOutOfRangeDistance: Boolean = true,
    val rejectOutOfRangeLux: Boolean = true,
    val guardNonFiniteValues: Boolean = true,
    val deduplicateTimestamps: Boolean = true,
    val detectImportGaps: Boolean = true,
    val distanceImputation: Boolean = true,
    val blockInterpolationAcrossGaps: Boolean = true,
    val replaceAlsSingleSampleSpikes: Boolean = true,
    val alsSingleSampleSpikeThresholdLux: Double = 300.0
)
