/** Flat UI model for a daily session so composables receive pre-assembled metrics. */
package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.analysis.NearworkRiskReason
import com.example.nearworkthesis.domain.analysis.NearworkSession
data class DailySessionUiModel(
    val session: NearworkSession,
    val nrs: Double,
    val meanLux: Double?,
    val reasons: Set<NearworkRiskReason>
)
