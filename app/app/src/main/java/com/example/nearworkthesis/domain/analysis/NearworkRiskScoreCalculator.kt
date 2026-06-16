package com.example.nearworkthesis.domain.analysis

import com.example.nearworkthesis.core.util.AppConstants
import kotlin.math.log10

data class NrsResult(
    val nrs: Double,
    val sampleCount: Int,
    val meanLuxDuringNearwork: Double?
)

class NearworkRiskScoreCalculator {

    fun calculate(
        samples: List<NearworkSample>,
        durationHours: Double,
        robustness: RobustnessConfig = RobustnessConfig()
    ): NrsResult {
        if (samples.isEmpty() || durationHours <= 0.0) {
            return NrsResult(nrs = 0.0, sampleCount = 0, meanLuxDuringNearwork = null)
        }

        var totalInstantNrs = 0.0
        var validSampleCount = 0
        var nearworkLuxSum = 0.0
        var nearworkLuxCount = 0

        for (sample in samples) {
            val distanceCm = sample.distanceCm
            if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) {
                continue
            }
            if (robustness.rejectOutOfRangeDistance && (distanceCm <= MIN_VALID_DISTANCE_CM || distanceCm > MAX_VALID_DISTANCE_CM)) {
                continue
            }

            val luxInput = sample.lux
            if (robustness.rejectOutOfRangeLux && luxInput > MAX_VALID_LUX) {
                continue
            }
            val luxForWeight = when {
                robustness.guardNonFiniteValues && !luxInput.isFinite() -> DEFAULT_BASELINE_LUX
                robustness.rejectOutOfRangeLux && luxInput < MIN_LUX -> MIN_LUX
                else -> luxInput
            }

            val diopters = CM_PER_METER / distanceCm
            val distanceWeight = distanceWeight(distanceCm)
            val lightWeight = lightWeight(luxForWeight)
            val instantNrs = diopters * distanceWeight * lightWeight

            if ((robustness.guardNonFiniteValues && !instantNrs.isFinite()) || instantNrs < 0.0) {
                continue
            }

            totalInstantNrs += instantNrs
            validSampleCount += 1

            if (distanceCm < NEARWORK_DISTANCE_CM && NonFiniteValueGuard.isAcceptableLux(luxInput, robustness)) {
                nearworkLuxSum += luxForWeight
                nearworkLuxCount += 1
            }
        }

        if (validSampleCount == 0) {
            return NrsResult(nrs = 0.0, sampleCount = 0, meanLuxDuringNearwork = null)
        }

        val meanInstantNrs = totalInstantNrs / validSampleCount.toDouble()
        val meanLuxDuringNearwork = if (nearworkLuxCount > 0) nearworkLuxSum / nearworkLuxCount.toDouble() else null

        return NrsResult(
            nrs = meanInstantNrs * durationHours,
            sampleCount = validSampleCount,
            meanLuxDuringNearwork = meanLuxDuringNearwork
        )
    }

    private fun distanceWeight(distanceCm: Double): Double = when {
        distanceCm < DIST_THRESHOLD_20 -> DIST_WEIGHT_UNDER_20
        distanceCm < DIST_THRESHOLD_30 -> DIST_WEIGHT_20_TO_30
        distanceCm < DIST_THRESHOLD_50 -> DIST_WEIGHT_30_TO_50
        else -> DIST_WEIGHT_50_PLUS
    }

    private fun lightWeight(lux: Double): Double {
        return (1.0 / log10(lux + LUX_LOG_OFFSET)) * tierMultiplier(lux)
    }

    private fun tierMultiplier(lux: Double): Double {
        return when {
            lux <= LUX_TIER_DIM_MAX -> LUX_TIER_MULTIPLIER_DIM
            lux <= LUX_TIER_INDOOR_MAX -> LUX_TIER_MULTIPLIER_INDOOR
            lux <= LUX_TIER_BRIGHT_MAX -> LUX_TIER_MULTIPLIER_BRIGHT
            lux <= LUX_TIER_VERY_BRIGHT_MAX -> LUX_TIER_MULTIPLIER_VERY_BRIGHT
            lux <= LUX_TIER_EXTREME_MAX -> LUX_TIER_MULTIPLIER_EXTREME
            else -> LUX_TIER_MULTIPLIER_OUTDOOR
        }
    }

    companion object {
        const val CM_PER_METER = 100.0
        const val MIN_VALID_DISTANCE_CM = 0.0
        const val MAX_VALID_DISTANCE_CM = 200.0
        const val NEARWORK_DISTANCE_CM = 40.0
        const val MIN_LUX = 0.0
        const val MAX_VALID_LUX = AppConstants.MAX_VALID_LUX
        const val DEFAULT_BASELINE_LUX = AppConstants.BASELINE_LUX
        const val LUX_LOG_OFFSET = 10.0

        const val DIST_WEIGHT_UNDER_20 = AppConstants.ZONE_WEIGHT_EXTREME_CLOSE
        const val DIST_WEIGHT_20_TO_30 = AppConstants.ZONE_WEIGHT_CLOSE
        const val DIST_WEIGHT_30_TO_50 = AppConstants.ZONE_WEIGHT_MODERATE
        const val DIST_WEIGHT_50_PLUS = AppConstants.ZONE_WEIGHT_DEFAULT
        const val DIST_THRESHOLD_20 = AppConstants.ZONE_EXTREME_CLOSE_CM
        const val DIST_THRESHOLD_30 = AppConstants.ZONE_CLOSE_CM
        const val DIST_THRESHOLD_50 = AppConstants.ZONE_MODERATE_CM

        const val LUX_TIER_DIM_MAX = AppConstants.LUX_TIER_1
        const val LUX_TIER_INDOOR_MAX = AppConstants.LUX_TIER_2
        const val LUX_TIER_BRIGHT_MAX = AppConstants.LUX_TIER_3
        const val LUX_TIER_VERY_BRIGHT_MAX = AppConstants.LUX_TIER_4
        const val LUX_TIER_EXTREME_MAX = AppConstants.LUX_TIER_5

        const val LUX_TIER_MULTIPLIER_DIM = 1.5
        const val LUX_TIER_MULTIPLIER_INDOOR = 1.0
        const val LUX_TIER_MULTIPLIER_BRIGHT = 0.8
        const val LUX_TIER_MULTIPLIER_VERY_BRIGHT = 0.5
        const val LUX_TIER_MULTIPLIER_EXTREME = 0.35
        const val LUX_TIER_MULTIPLIER_OUTDOOR = 0.2
    }
}
