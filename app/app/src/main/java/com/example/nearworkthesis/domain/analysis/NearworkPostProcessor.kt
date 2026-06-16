package com.example.nearworkthesis.domain.analysis

import com.example.nearworkthesis.core.util.AppConstants

data class NearworkSettings(
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int,
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistanceThresholdCm: Int,
    val extremeCloseThresholdCm: Int
)

data class NearworkPostProcessingResult(
    val totalDiopterHours: Double,
    val lowLightMinutes: Int,
    val sessions: List<NearworkSession>,
    val longestSession: NearworkSession?,
    val flaggedSessions: List<NearworkSessionRisk>,
    val flaggedSessionCount: Int,
    val totalNearTimeUnder40CmSeconds: Long? = null,
    val pctTimeNear40Cm: Double? = null,
    val timeBelow30CmSeconds: Long? = null,
    val timeIntermediate40To70CmSeconds: Long? = null,
    val timeFarAbove70CmSeconds: Long? = null,
    val meanDiopterDemand: Double? = null,
    val timeAbove2_5DiopterSeconds: Long? = null,
    val timeAbove3_0DiopterSeconds: Long? = null,
    val diopterPerSecond: List<Double> = emptyList(),
    val cumulativeDiopterHoursPerSecond: List<Double> = emptyList(),
    val timeOutdoor1000LuxMinutes: Double? = null,
    val pctOutdoorTime: Double? = null,
    val meanLuxDuringNearwork: Double? = null,
    val meanLuxDuringBreaks: Double? = null,
    val nearworkInLowLightSeconds: Long? = null,
    val distanceStdCm: Double? = null,
    val distanceCv: Double? = null,
    val transitionMatrix: Map<String, Int> = emptyMap(),
    val dwellTimesByStateSeconds: Map<String, Long> = emptyMap(),
    val distanceCoveragePct: Double? = null,
    val luxCoveragePct: Double? = null,
    val diopterCoveragePct: Double? = null,
    val compositeRiskScore: Double? = null,
    val insightsSignals: NearworkInsightsSignals? = null
)

data class NearworkInsightsSignals(
    val totalNearTimeUnder40CmSeconds: Long,
    val totalDiopterHours: Double,
    val lowLightMinutes: Int,
    val longestSessionSeconds: Long,
    val outdoorMinutes1000Lux: Double
)

class NearworkPostProcessor(
    private val diopterHoursCalculator: DiopterHoursCalculator = DiopterHoursCalculator(),
    private val lightContextAnalyzer: LightContextAnalyzer = LightContextAnalyzer(),
    private val sessionDetector: NearworkSessionDetector = NearworkSessionDetector(diopterHoursCalculator)
) {

    fun compute(
        samples: List<NearworkSample>,
        settings: NearworkSettings,
        preprocessing: PreprocessingResult? = null,
        robustness: RobustnessConfig = RobustnessConfig()
    ): NearworkPostProcessingResult {
        val sorted = samples.sortedBy { it.timestampMillis }

        // A) Exposure / threshold metrics
        val classificationSeries = resolveClassificationSeries(preprocessing = preprocessing, fallbackSamples = sorted)
        var totalValidSeconds = 0L
        var totalNearTimeUnder40CmSeconds = 0L
        var timeBelow30CmSeconds = 0L
        var timeIntermediate40To70CmSeconds = 0L
        var timeFarAbove70CmSeconds = 0L
        for (distanceCm in classificationSeries) {
            if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) continue
            totalValidSeconds += 1L
            if (distanceCm < 40.0) totalNearTimeUnder40CmSeconds += 1L
            if (distanceCm < AppConstants.ZONE_CLOSE_CM) timeBelow30CmSeconds += 1L
            if (distanceCm in 40.0..70.0) timeIntermediate40To70CmSeconds += 1L
            if (distanceCm > 70.0) timeFarAbove70CmSeconds += 1L
        }
        val pctTimeNear40Cm = if (totalValidSeconds > 0L) {
            totalNearTimeUnder40CmSeconds.toDouble() / totalValidSeconds.toDouble()
        } else {
            null
        }

        // B) Diopter metrics
        // D-h integrates over ALL valid processed samples (full recording day),
        // not just detected nearwork sessions — intentional, see SessionAveragedNrsCalculator.kt.
        val totalDiopterHours = diopterHoursCalculator.calculate(sorted, robustness).totalDiopterHours
        var diopterSum = 0.0
        var diopterCount = 0L
        var timeAbove2_5DiopterSeconds = 0L
        var timeAbove3_0DiopterSeconds = 0L
        val diopterPerSecond = MutableList(classificationSeries.size) { Double.NaN }
        val cumulativeDiopterHoursPerSecond = MutableList(classificationSeries.size) { Double.NaN }
        var cumulativeDiopterHours = 0.0
        for (i in classificationSeries.indices) {
            val distanceCm = classificationSeries[i]
            val diopter = distanceToDiopter(distanceCm, robustness)
            diopterPerSecond[i] = diopter
            if (!diopter.isFinite()) continue
            diopterSum += diopter
            diopterCount += 1L
            if (diopter > 2.5) timeAbove2_5DiopterSeconds += 1L
            if (diopter > 3.0) timeAbove3_0DiopterSeconds += 1L
        }
        for (i in diopterPerSecond.indices) {
            val diopter = diopterPerSecond[i]
            if (!diopter.isFinite()) {
                cumulativeDiopterHoursPerSecond[i] = Double.NaN
                continue
            }
            cumulativeDiopterHours += diopter / 3600.0
            cumulativeDiopterHoursPerSecond[i] = cumulativeDiopterHours
        }
        val meanDiopterDemand = if (diopterCount > 0L) diopterSum / diopterCount.toDouble() else null

        // C) Bout / break metrics
        val sessionConfig = NearworkSessionDetector.Config(
            nearworkDistanceThresholdCm = settings.nearworkDistanceThresholdCm.toDouble(),
            breakGapSeconds = settings.breakGapSeconds,
            minSessionDurationSeconds = settings.minSessionDurationSeconds
        )
        val sessions = sessionDetector.detectSessions(
            processedSamples = sorted,
            lowLightThresholdLux = settings.lowLightThresholdLux,
            config = sessionConfig,
            robustness = robustness
        )
        val longestSession = sessionDetector.findLongestSession(sessions)
        // D) Illumination metrics
        val luxSeries = resolveLuxSeries(preprocessing = preprocessing, fallbackSamples = sorted)
        var validLuxSeconds = 0L
        var outdoorSeconds = 0L
        var nearworkInLowLightSeconds = 0L
        var nearworkLuxSum = 0.0
        var nearworkLuxCount = 0L
        val jointCount = minOf(classificationSeries.size, luxSeries.size)
        for (i in 0 until jointCount) {
            val distanceCm = classificationSeries[i]
            val lux = luxSeries[i]
            val luxValid = !robustness.guardNonFiniteValues || lux.isFinite()
            val nearValid = (!robustness.guardNonFiniteValues || distanceCm.isFinite()) && distanceCm < 40.0

            if (luxValid) {
                validLuxSeconds += 1L
                if (lux >= 1000.0) {
                    outdoorSeconds += 1L
                }
            }
            if (nearValid && luxValid) {
                nearworkLuxSum += lux
                nearworkLuxCount += 1L
                if (lux < settings.lowLightThresholdLux.toDouble()) {
                    nearworkInLowLightSeconds += 1L
                }
            }
        }
        val lowLightMinutes = lightContextAnalyzer.analyze(
            samples = sorted,
            lowLightThresholdLux = settings.lowLightThresholdLux,
            robustness = robustness
        ).lowLightMinutes
        val timeOutdoor1000LuxMinutes: Double? = if (validLuxSeconds > 0L) outdoorSeconds / 60.0 else null
        val pctOutdoorTime: Double? = if (validLuxSeconds > 0L) outdoorSeconds.toDouble() / validLuxSeconds.toDouble() else null
        val meanLuxDuringNearwork: Double? = if (nearworkLuxCount > 0L) nearworkLuxSum / nearworkLuxCount.toDouble() else null
        // Break interval metrics require explicit break objects from the session detector; not yet implemented.
        val meanLuxDuringBreaks: Double? = null

        // E) Variability / transitions
        val validDistances = classificationSeries.filter { !robustness.guardNonFiniteValues || it.isFinite() }
        val distanceMean = if (validDistances.isNotEmpty()) validDistances.average() else Double.NaN
        val distanceStdCm: Double? = if (validDistances.size >= 2) {
            val variance = validDistances.sumOf { v ->
                val d = v - distanceMean
                d * d
            } / validDistances.size.toDouble()
            kotlin.math.sqrt(variance)
        } else {
            null
        }
        val distanceCv: Double? = if (distanceStdCm != null && distanceMean.isFinite() && distanceMean > 0.0) {
            distanceStdCm / distanceMean
        } else {
            null
        }
        val transitionMatrix = buildTransitionMatrix(classificationSeries, robustness)
        val dwellTimesByStateSeconds = buildDwellTimes(classificationSeries, robustness)
        val distanceCoveragePct = if (classificationSeries.isNotEmpty()) {
            validDistances.size.toDouble() / classificationSeries.size.toDouble()
        } else {
            null
        }
        val luxCoveragePct = if (luxSeries.isNotEmpty()) {
            validLuxSeconds.toDouble() / luxSeries.size.toDouble()
        } else {
            null
        }
        val diopterCoveragePct = if (diopterPerSecond.isNotEmpty()) {
            diopterCount.toDouble() / diopterPerSecond.size.toDouble()
        } else {
            null
        }

        // F) Composite risk / insights
        val flaggedSessions = NearworkRiskClassifier.classifyFlaggedSessions(
            sessions = sessions,
            closeDistanceThresholdCm = settings.closeDistanceThresholdCm.toDouble(),
            extremeCloseThresholdCm = settings.extremeCloseThresholdCm.toDouble()
        )
        val compositeRiskScore = computeCompositeRiskScore(flaggedSessions)
        val insightsSignals = NearworkInsightsSignals(
            totalNearTimeUnder40CmSeconds = totalNearTimeUnder40CmSeconds,
            totalDiopterHours = totalDiopterHours,
            lowLightMinutes = lowLightMinutes,
            longestSessionSeconds = longestSession?.durationSeconds ?: 0L,
            outdoorMinutes1000Lux = timeOutdoor1000LuxMinutes ?: 0.0
        )

        return NearworkPostProcessingResult(
            totalDiopterHours = totalDiopterHours,
            lowLightMinutes = lowLightMinutes,
            sessions = sessions,
            longestSession = longestSession,
            flaggedSessions = flaggedSessions,
            flaggedSessionCount = flaggedSessions.size,
            totalNearTimeUnder40CmSeconds = totalNearTimeUnder40CmSeconds,
            pctTimeNear40Cm = pctTimeNear40Cm,
            timeBelow30CmSeconds = timeBelow30CmSeconds,
            timeIntermediate40To70CmSeconds = timeIntermediate40To70CmSeconds,
            timeFarAbove70CmSeconds = timeFarAbove70CmSeconds,
            meanDiopterDemand = meanDiopterDemand,
            timeAbove2_5DiopterSeconds = timeAbove2_5DiopterSeconds,
            timeAbove3_0DiopterSeconds = timeAbove3_0DiopterSeconds,
            diopterPerSecond = diopterPerSecond,
            cumulativeDiopterHoursPerSecond = cumulativeDiopterHoursPerSecond,
            timeOutdoor1000LuxMinutes = timeOutdoor1000LuxMinutes,
            pctOutdoorTime = pctOutdoorTime,
            meanLuxDuringNearwork = meanLuxDuringNearwork,
            meanLuxDuringBreaks = meanLuxDuringBreaks,
            nearworkInLowLightSeconds = nearworkInLowLightSeconds,
            distanceStdCm = distanceStdCm,
            distanceCv = distanceCv,
            transitionMatrix = transitionMatrix,
            dwellTimesByStateSeconds = dwellTimesByStateSeconds,
            distanceCoveragePct = distanceCoveragePct,
            luxCoveragePct = luxCoveragePct,
            diopterCoveragePct = diopterCoveragePct,
            compositeRiskScore = compositeRiskScore,
            insightsSignals = insightsSignals
        )
    }
}

private fun resolveClassificationSeries(
    preprocessing: PreprocessingResult?,
    fallbackSamples: List<NearworkSample>
): List<Double> {
    if (preprocessing == null) {
        return fallbackSamples.map { it.distanceCm }
    }
    if (preprocessing.tSeconds.isEmpty()) {
        return fallbackSamples.map { it.distanceCm }
    }
    val smoothed = preprocessing.sFilterDistanceCm
    if (smoothed.size == preprocessing.tSeconds.size && smoothed.any { it.isFinite() }) {
        return smoothed
    }
    val interpolated = preprocessing.sInterpDistanceCm
    if (interpolated.size == preprocessing.tSeconds.size) {
        return interpolated
    }
    return fallbackSamples.map { it.distanceCm }
}

private fun resolveLuxSeries(
    preprocessing: PreprocessingResult?,
    fallbackSamples: List<NearworkSample>
): List<Double> {
    if (preprocessing == null) {
        return fallbackSamples.map { it.lux }
    }
    if (preprocessing.tSeconds.isEmpty()) {
        return fallbackSamples.map { it.lux }
    }
    val interpolatedLux = preprocessing.sInterpIlluminationLux
    if (interpolatedLux.size == preprocessing.tSeconds.size) {
        return interpolatedLux
    }
    return fallbackSamples.map { it.lux }
}

private fun distanceToDiopter(distanceCm: Double, robustness: RobustnessConfig): Double {
    if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) return Double.NaN
    if (robustness.rejectOutOfRangeDistance && distanceCm <= 0.0) return Double.NaN
    return 100.0 / distanceCm
}

private enum class DistanceState { NEAR, INTERMEDIATE, FAR }

private fun distanceToState(distanceCm: Double, robustness: RobustnessConfig): DistanceState? {
    if (robustness.guardNonFiniteValues && !distanceCm.isFinite()) return null
    return when {
        distanceCm < 40.0 -> DistanceState.NEAR
        distanceCm <= 70.0 -> DistanceState.INTERMEDIATE
        else -> DistanceState.FAR
    }
}

private fun buildTransitionMatrix(
    distanceSeries: List<Double>,
    robustness: RobustnessConfig
): Map<String, Int> {
    val states = DistanceState.entries
    val matrix = linkedMapOf<String, Int>()
    for (from in states) {
        for (to in states) {
            matrix["${from.name}->${to.name}"] = 0
        }
    }
    for (i in 1 until distanceSeries.size) {
        val from = distanceToState(distanceSeries[i - 1], robustness) ?: continue
        val to = distanceToState(distanceSeries[i], robustness) ?: continue
        val key = "${from.name}->${to.name}"
        matrix[key] = (matrix[key] ?: 0) + 1
    }
    return matrix
}

private fun buildDwellTimes(
    distanceSeries: List<Double>,
    robustness: RobustnessConfig
): Map<String, Long> {
    var near = 0L
    var intermediate = 0L
    var far = 0L
    for (distanceCm in distanceSeries) {
        when (distanceToState(distanceCm, robustness)) {
            DistanceState.NEAR -> near += 1L
            DistanceState.INTERMEDIATE -> intermediate += 1L
            DistanceState.FAR -> far += 1L
            null -> Unit
        }
    }
    return linkedMapOf(
        DistanceState.NEAR.name to near,
        DistanceState.INTERMEDIATE.name to intermediate,
        DistanceState.FAR.name to far
    )
}

private fun computeCompositeRiskScore(flaggedSessions: List<NearworkSessionRisk>): Double? {
    if (flaggedSessions.isEmpty()) return 0.0
    return flaggedSessions.sumOf { risk ->
        val reasonScore = risk.reasons.sumOf { reason ->
            when (reason) {
                NearworkRiskReason.ExtremeClose -> 3.0
                NearworkRiskReason.CloseDistance -> 2.0
                NearworkRiskReason.LowLight -> 1.0
            }
        }
        val durationScore = if (risk.reasons.isEmpty()) 0.0 else {
            (risk.session.durationSeconds.toDouble() / 600.0).coerceAtMost(1.0)
        }
        reasonScore + durationScore
    }
}

private fun classifyRiskReasons(
    session: NearworkSession,
    settings: NearworkSettings
): Set<NearworkRiskReason> {
    return buildSet {
        if (session.minDistanceCm < settings.closeDistanceThresholdCm.toDouble()) add(NearworkRiskReason.CloseDistance)
        if (session.minDistanceCm < settings.extremeCloseThresholdCm.toDouble()) add(NearworkRiskReason.ExtremeClose)
        if (session.lowLightSecondsInSession > 0L) add(NearworkRiskReason.LowLight)
    }
}

