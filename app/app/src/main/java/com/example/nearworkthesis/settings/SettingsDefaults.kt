package com.example.nearworkthesis.settings

import com.example.nearworkthesis.core.util.AppConstants
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy

object SettingsDefaults {
    // I pin this to the thesis default so first-launch UI matches the documented low-light boundary.
    const val LOW_LIGHT_THRESHOLD_LUX: Int = AppConstants.LUX_TIER_1.toInt()
    const val NEARWORK_DISTANCE_THRESHOLD_CM: Int = 60
    const val BREAK_GAP_SECONDS: Int = 60
    const val MIN_SESSION_DURATION_SECONDS: Int = 60
    val CLOSE_DISTANCE_THRESHOLD_CM: Int = AppConstants.DISTANCE_ZONE_2_CM.toInt()
    val EXTREME_CLOSE_THRESHOLD_CM: Int = AppConstants.DISTANCE_ZONE_1_CM.toInt()
    const val REPLACE_ALS_SINGLE_SAMPLE_SPIKES: Boolean = true
    const val ALS_SPIKE_THRESHOLD_LUX: Double = AppConstants.BASELINE_LUX
    const val SHOW_DEBUG_OVERLAY: Boolean = false
    const val DAILY_REMINDER_ENABLED: Boolean = false
    const val DAILY_REMINDER_TIME_LOCAL: String = "19:00"
    const val POST_IMPORT_NOTIFICATION_ENABLED: Boolean = false
    val DUPLICATE_RESOLUTION_POLICY: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING
}

