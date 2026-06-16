package com.example.nearworkthesis.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class PortableAnalysisSettings(
    val lowLightThresholdLux: Int,
    val nearworkDistanceThresholdCm: Int,
    val breakGapSeconds: Int,
    val minSessionDurationSeconds: Int,
    val closeDistanceThresholdCm: Int,
    val extremeCloseThresholdCm: Int,
    val replaceAlsSingleSampleSpikes: Boolean,
    val alsSpikeThresholdLux: Double
)

object AnalysisSettingsExchange {
    const val fileType: String = "howfar_analysis_settings"
    const val formatVersion: Int = 1

    fun suggestedFilename(today: LocalDate = LocalDate.now()): String {
        return "howfar_settings_$today.json"
    }

    fun toJson(
        settings: PortableAnalysisSettings,
        exportedAtIsoUtc: String = Instant.now().toString()
    ): String {
        return buildString {
            appendLine("{")
            appendLine("  \"fileType\": ${q(fileType)},")
            appendLine("  \"formatVersion\": $formatVersion,")
            appendLine("  \"exportedAtIsoUtc\": ${q(exportedAtIsoUtc)},")
            appendLine("  \"lowLightThresholdLux\": ${settings.lowLightThresholdLux},")
            appendLine("  \"nearworkDistanceThresholdCm\": ${settings.nearworkDistanceThresholdCm},")
            appendLine("  \"breakGapSeconds\": ${settings.breakGapSeconds},")
            appendLine("  \"minSessionDurationSeconds\": ${settings.minSessionDurationSeconds},")
            appendLine("  \"closeDistanceThresholdCm\": ${settings.closeDistanceThresholdCm},")
            appendLine("  \"extremeCloseThresholdCm\": ${settings.extremeCloseThresholdCm},")
            appendLine("  \"replaceAlsSingleSampleSpikes\": ${settings.replaceAlsSingleSampleSpikes},")
            appendLine("  \"alsSpikeThresholdLux\": ${formatDouble(settings.alsSpikeThresholdLux)}")
            append('}')
        }
    }

    fun parse(json: String): PortableAnalysisSettings {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("Malformed settings file: expected a JSON object.")
        }

        val parsedFileType = extractString(trimmed, "fileType")
        if (parsedFileType != fileType) {
            throw IllegalArgumentException("Unrecognised HowFar settings file.")
        }

        val parsedVersion = extractInt(trimmed, "formatVersion")
        if (parsedVersion != formatVersion) {
            throw IllegalArgumentException("Unsupported settings file version: $parsedVersion.")
        }

        return PortableAnalysisSettings(
            lowLightThresholdLux = extractInt(trimmed, "lowLightThresholdLux"),
            nearworkDistanceThresholdCm = extractInt(trimmed, "nearworkDistanceThresholdCm"),
            breakGapSeconds = extractInt(trimmed, "breakGapSeconds"),
            minSessionDurationSeconds = extractInt(trimmed, "minSessionDurationSeconds"),
            closeDistanceThresholdCm = extractInt(trimmed, "closeDistanceThresholdCm"),
            extremeCloseThresholdCm = extractInt(trimmed, "extremeCloseThresholdCm"),
            replaceAlsSingleSampleSpikes = extractBoolean(trimmed, "replaceAlsSingleSampleSpikes"),
            alsSpikeThresholdLux = extractDouble(trimmed, "alsSpikeThresholdLux")
        ).validated()
    }

    private fun PortableAnalysisSettings.validated(): PortableAnalysisSettings {
        require(lowLightThresholdLux in 0..50_000) { "Invalid low-light threshold in settings file." }
        require(nearworkDistanceThresholdCm in 10..200) { "Invalid nearwork threshold in settings file." }
        require(breakGapSeconds in 1..(60 * 60)) { "Invalid break-gap setting in settings file." }
        require(minSessionDurationSeconds in 1..(24 * 60 * 60)) { "Invalid minimum session duration in settings file." }
        require(closeDistanceThresholdCm in 10..200) { "Invalid close-distance threshold in settings file." }
        require(extremeCloseThresholdCm in 10..200) { "Invalid extreme-close threshold in settings file." }
        require(extremeCloseThresholdCm < closeDistanceThresholdCm) {
            "Extreme-close threshold must be strictly less than close-distance threshold in settings file."
        }
        require(alsSpikeThresholdLux in 0.0..50_000.0) { "Invalid ALS spike threshold in settings file." }
        return this
    }

    private fun extractString(json: String, field: String): String {
        val match = Regex("\"$field\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(json)
            ?: throw IllegalArgumentException("Malformed settings file: missing $field.")
        return unescape(match.groupValues[1])
    }

    private fun extractInt(json: String, field: String): Int {
        val match = Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(json)
            ?: throw IllegalArgumentException("Malformed settings file: missing $field.")
        return match.groupValues[1].toIntOrNull()
            ?: throw IllegalArgumentException("Malformed settings file: invalid $field.")
    }

    private fun extractDouble(json: String, field: String): Double {
        val match = Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(json)
            ?: throw IllegalArgumentException("Malformed settings file: missing $field.")
        return match.groupValues[1].toDoubleOrNull()
            ?: throw IllegalArgumentException("Malformed settings file: invalid $field.")
    }

    private fun extractBoolean(json: String, field: String): Boolean {
        val match = Regex("\"$field\"\\s*:\\s*(true|false)").find(json)
            ?: throw IllegalArgumentException("Malformed settings file: missing $field.")
        return match.groupValues[1].toBooleanStrict()
    }

    private fun q(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""

    private fun unescape(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun formatDouble(value: Double): String {
        if (!value.isFinite()) return "0.0"
        return value.toString()
    }
}
