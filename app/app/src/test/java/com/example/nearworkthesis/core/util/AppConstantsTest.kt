package com.example.nearworkthesis.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConstantsTest {

    @Test
    fun thresholdsAndAliases_matchExpectedResearchDefaults() {
        assertEquals(1L, AppConstants.DEFAULT_PROFILE_ID)

        assertEquals(AppConstants.ZONE_EXTREME_CLOSE_CM, AppConstants.DISTANCE_ZONE_1_CM, 0.0)
        assertEquals(AppConstants.ZONE_CLOSE_CM, AppConstants.DISTANCE_ZONE_2_CM, 0.0)

        assertEquals(AppConstants.ZONE_WEIGHT_EXTREME_CLOSE, AppConstants.ZONE_WEIGHT_1, 0.0)
        assertEquals(AppConstants.ZONE_WEIGHT_CLOSE, AppConstants.ZONE_WEIGHT_2, 0.0)
        assertEquals(AppConstants.ZONE_WEIGHT_DEFAULT, AppConstants.ZONE_WEIGHT_3, 0.0)

        assertTrue(AppConstants.LUX_TIER_1 < AppConstants.LUX_TIER_2)
        assertTrue(AppConstants.LUX_TIER_2 < AppConstants.LUX_TIER_3)
        assertTrue(AppConstants.LUX_TIER_3 < AppConstants.LUX_TIER_4)
        assertTrue(AppConstants.LUX_TIER_4 < AppConstants.LUX_TIER_5)

        assertTrue(AppConstants.ZONE_EXTREME_CLOSE_CM < AppConstants.ZONE_CLOSE_CM)
        assertTrue(AppConstants.ZONE_CLOSE_CM < AppConstants.ZONE_MODERATE_CM)
        assertTrue(AppConstants.ZONE_WEIGHT_EXTREME_CLOSE > AppConstants.ZONE_WEIGHT_CLOSE)
        assertTrue(AppConstants.ZONE_WEIGHT_CLOSE > AppConstants.ZONE_WEIGHT_MODERATE)
        assertTrue(AppConstants.MAX_VALID_LUX > AppConstants.BASELINE_LUX)
        assertTrue(AppConstants.NRS_DAY_HOURS > 0.0)
        assertTrue(AppConstants.MAX_SQL_IN_ARGS > 0)
        assertTrue(AppConstants.MIN_IMPORT_DURATION_HOURS > 0)
        assertTrue(AppConstants.INTERPOLATION_GAP_THRESHOLD_SECONDS > 0)
        assertEquals(false, AppConstants.USE_DYNAMIC_COLOUR)
    }
}
