package com.example.nearworkthesis.core.util

object AppConstants {
    const val DEFAULT_PROFILE_ID: Long = 1L

    const val LUX_TIER_1 = 55.0
    const val LUX_TIER_2 = 300.0
    const val LUX_TIER_3 = 1000.0
    const val LUX_TIER_4 = 3000.0
    const val LUX_TIER_5 = 5000.0

    const val ZONE_EXTREME_CLOSE_CM = 20.0
    const val ZONE_CLOSE_CM = 30.0
    const val ZONE_MODERATE_CM = 50.0

    const val ZONE_WEIGHT_EXTREME_CLOSE = 5.0
    const val ZONE_WEIGHT_CLOSE = 2.5
    const val ZONE_WEIGHT_MODERATE = 1.5
    const val ZONE_WEIGHT_DEFAULT = 1.0

    const val DISTANCE_ZONE_1_CM = ZONE_EXTREME_CLOSE_CM
    const val DISTANCE_ZONE_2_CM = ZONE_CLOSE_CM

    const val ZONE_WEIGHT_1 = ZONE_WEIGHT_EXTREME_CLOSE
    const val ZONE_WEIGHT_2 = ZONE_WEIGHT_CLOSE
    const val ZONE_WEIGHT_3 = ZONE_WEIGHT_DEFAULT

    const val MAX_VALID_LUX = 50_000.0
    const val BASELINE_LUX = 300.0
    const val NRS_DAY_HOURS = 24.0

    const val MAX_SQL_IN_ARGS = 900
    const val MIN_IMPORT_DURATION_HOURS = 4
    const val INTERPOLATION_GAP_THRESHOLD_SECONDS = 10
    const val USE_DYNAMIC_COLOUR = false
}
