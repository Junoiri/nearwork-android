package com.example.nearworkthesis.domain.export

import com.example.nearworkthesis.domain.analysis.AnalysisConfig

object ResultsPackManifestBuilder {

    data class Manifest(
        val exportCreatedAtIsoUtc: String,
        val appVersionName: String,
        val appVersionCode: Int,
        val profileId: Long,
        val profileName: String,
        val startDay: String,
        val endDay: String,
        val preprocessingSmoothingWindow: Int,
        val dedupeRule: String,
        val duplicateResolutionPolicy: String,
        val distanceRangeMinCm: Double,
        val distanceRangeMaxCm: Double,
        val luxRangeMin: Double,
        val luxRangeMax: Double,
        val gapDetectionThresholdSeconds: Double,
        val sessionNearworkDistanceThresholdCm: Int,
        val sessionBreakGapSeconds: Int,
        val sessionMinSessionDurationSeconds: Int,
        val sessionCloseDistanceThresholdCm: Int,
        val sessionExtremeCloseThresholdCm: Int,
        val lowLightThresholdLux: Int,
        val timezoneId: String,
        val timeHandlingStatement: String,
        val settingsUsed: AnalysisConfig
    )

    fun build(manifest: Manifest): String {
        val json = StringBuilder(1400)
        json.append('{')
        json.append("\"exportCreatedAtIsoUtc\":").append(q(manifest.exportCreatedAtIsoUtc)).append(',')
        json.append("\"appVersionName\":").append(q(manifest.appVersionName)).append(',')
        json.append("\"versionCode\":").append(manifest.appVersionCode).append(',')
        json.append("\"profile\":{")
            .append("\"profileId\":").append(manifest.profileId).append(',')
            .append("\"profileName\":").append(q(manifest.profileName))
            .append("},")
        json.append("\"dateRange\":{")
            .append("\"startDay\":").append(q(manifest.startDay)).append(',')
            .append("\"endDay\":").append(q(manifest.endDay))
            .append("},")
        json.append("\"settingsUsed\":")
        appendSettingsUsed(json, manifest.settingsUsed)
        json.append(',')
        json.append("\"dataSources\":[\"daily\",\"sessions\",\"import_quality\"],")
        json.append("\"preprocessing\":{")
            .append("\"smoothingWindow\":").append(manifest.preprocessingSmoothingWindow).append(',')
            .append("\"dedupeRule\":").append(q(manifest.dedupeRule)).append(',')
            .append("\"duplicateResolutionPolicy\":").append(q(manifest.duplicateResolutionPolicy)).append(',')
            .append("\"outOfRangeRejection\":{")
            .append("\"distanceCmMin\":").append(formatDouble(manifest.distanceRangeMinCm)).append(',')
            .append("\"distanceCmMax\":").append(formatDouble(manifest.distanceRangeMaxCm)).append(',')
            .append("\"luxMin\":").append(formatDouble(manifest.luxRangeMin)).append(',')
            .append("\"luxMax\":").append(formatDouble(manifest.luxRangeMax))
            .append("},")
            .append("\"gapDetectionThresholdSeconds\":").append(formatDouble(manifest.gapDetectionThresholdSeconds))
            .append("},")
        json.append("\"diopterHours\":{")
            .append("\"dioptersFormula\":").append(q("1/(distanceMeters)")).append(',')
            .append("\"integrationMethod\":").append(q("trapezoidal between consecutive samples"))
            .append("},")
        json.append("\"sessionSegmentation\":{")
            .append("\"nearwork_distance_threshold_cm\":").append(manifest.sessionNearworkDistanceThresholdCm).append(',')
            .append("\"break_gap_seconds\":").append(manifest.sessionBreakGapSeconds).append(',')
            .append("\"min_session_duration_seconds\":").append(manifest.sessionMinSessionDurationSeconds).append(',')
            .append("\"close_distance_threshold_cm\":").append(manifest.sessionCloseDistanceThresholdCm).append(',')
            .append("\"extreme_close_threshold_cm\":").append(manifest.sessionExtremeCloseThresholdCm)
            .append("},")
        json.append("\"exposureThresholds\":{")
            .append("\"low_light_threshold_lux\":").append(manifest.lowLightThresholdLux)
            .append("},")
        json.append("\"timeHandling\":{")
            .append("\"timezoneId\":").append(q(manifest.timezoneId)).append(",")
            .append("\"dayGrouping\":").append(q(manifest.timeHandlingStatement)).append(",")
            .append("\"storedTimestamps\":").append(q("epoch millis UTC")).append(",")
            .append("\"exports\":").append(q("ISO UTC"))
            .append("}")
        json.append('}')
        return json.toString()
    }
}

private fun appendSettingsUsed(json: StringBuilder, settings: AnalysisConfig) {
    json.append('{')
    json.append("\"thresholds\":{")
        .append("\"low_light_threshold_lux\":").append(settings.thresholds.lowLightThresholdLux).append(',')
        .append("\"nearwork_distance_threshold_cm\":").append(settings.thresholds.nearworkDistanceThresholdCm).append(',')
        .append("\"break_gap_seconds\":").append(settings.thresholds.breakGapSeconds).append(',')
        .append("\"min_session_duration_seconds\":").append(settings.thresholds.minSessionDurationSeconds).append(',')
        .append("\"close_distance_threshold_cm\":").append(settings.thresholds.closeDistanceThresholdCm).append(',')
        .append("\"extreme_close_threshold_cm\":").append(settings.thresholds.extremeCloseThresholdCm)
        .append("},")
    json.append("\"preprocessing\":{")
        .append("\"smoothing_window\":").append(settings.pipeline.smoothingWindowSize).append(',')
        .append("\"dedupe_rule\":").append(q(settings.pipeline.dedupeRule)).append(',')
        .append("\"accepted_distance_cm\":{")
        .append("\"min\":").append(formatDouble(settings.pipeline.distanceRangeMinCm)).append(',')
        .append("\"max\":").append(formatDouble(settings.pipeline.distanceRangeMaxCm))
        .append("},")
        .append("\"accepted_lux\":{")
        .append("\"min\":").append(formatDouble(settings.pipeline.luxRangeMin)).append(',')
        .append("\"max\":").append(formatDouble(settings.pipeline.luxRangeMax))
        .append("},")
        .append("\"gap_threshold_seconds\":").append(settings.pipeline.gapThresholdSeconds)
        .append("},")
    json.append("\"timeHandling\":{")
        .append("\"timezoneId\":").append(q(settings.timeHandling.timezoneId)).append(',')
        .append("\"statement\":").append(q(settings.timeHandling.statement))
        .append("}")
    json.append('}')
}

private fun q(value: String): String = "\"" + escapeJson(value) + "\""

private fun escapeJson(value: String): String {
    if (value.isEmpty()) return ""
    val out = StringBuilder(value.length + 8)
    for (c in value) {
        when (c) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> out.append(c)
        }
    }
    return out.toString()
}

private fun formatDouble(value: Double): String {
    if (!value.isFinite()) return "0.0"
    return value.toString()
}
