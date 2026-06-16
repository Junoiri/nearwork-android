package com.example.nearworkthesis.domain.analysis

object NearworkRiskClassifier {

    fun classifyFlaggedSessions(
        sessions: List<NearworkSession>,
        closeDistanceThresholdCm: Double,
        extremeCloseThresholdCm: Double
    ): List<NearworkSessionRisk> {
        if (sessions.isEmpty()) return emptyList()

        return sessions.map { session ->
            val reasons = buildSet {
                if (session.minDistanceCm < closeDistanceThresholdCm) add(NearworkRiskReason.CloseDistance)
                if (session.minDistanceCm < extremeCloseThresholdCm) add(NearworkRiskReason.ExtremeClose)
                if (session.lowLightSecondsInSession > 0L) add(NearworkRiskReason.LowLight)
            }
            val score = (if (NearworkRiskReason.ExtremeClose in reasons) 1_000_000 else 0) +
                (if (NearworkRiskReason.CloseDistance in reasons) 10_000 else 0) +
                (if (NearworkRiskReason.LowLight in reasons) 1_000 else 0) +
                (session.durationSeconds.toInt().coerceAtMost(1000))
            NearworkSessionRisk(session = session, reasons = reasons) to score
        }
            .filter { it.first.reasons.isNotEmpty() }
            .sortedWith(compareByDescending<Pair<NearworkSessionRisk, Int>> { it.second }.thenByDescending { it.first.session.durationSeconds })
            .map { it.first }
    }
}
