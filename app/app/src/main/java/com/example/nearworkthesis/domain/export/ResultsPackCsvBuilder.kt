package com.example.nearworkthesis.domain.export

import java.util.Locale

object ResultsPackCsvBuilder {

    const val dailyFilename = "nearwork_daily_results.csv"
    const val sessionsFilename = "nearwork_sessions_results.csv"
    const val importQualityFilename = "nearwork_import_quality.csv"
    const val manifestFilename = "manifest.json"

    fun buildDailyResultsCsv(rows: List<DailyResultsRow>): String {
        return buildString {
            // I include the daily session-averaged NRS here so the results pack matches the thesis-facing daily summary metric.
            appendLine("date,sampleCount,diopterHoursTotal,nrsSessionAverage,lowLightMinutes,longestSessionSeconds,riskySessionCount,gapCount,largestGapSeconds")
            for (r in rows) {
                append(r.date)
                append(',')
                append(r.sampleCount)
                append(',')
                append(format2(r.diopterHoursTotal))
                append(',')
                // I format daily NRS to two decimals because the export uses the same presentation precision as the app summary views.
                append(format2(r.nrsSessionAverage))
                append(',')
                append(r.lowLightMinutes)
                append(',')
                append(r.longestSessionSeconds)
                append(',')
                append(r.riskySessionCount)
                append(',')
                append(r.gapCount ?: "")
                append(',')
                append(r.largestGapSeconds ?: "")
                append('\n')
            }
        }
    }

    fun buildSessionsResultsCsv(rows: List<SessionResultsRow>): String {
        return buildString {
            // I add mean lux and NRS to the session header because researchers need both session-level outputs in the results pack.
            appendLine("date,sessionStartIsoUtc,sessionEndIsoUtc,durationSeconds,avgDistanceCm,minDistanceCm,meanLux,diopterHoursInSession,nrs,lowLightSecondsInSession,flags_closeDistance,flags_lowLight,flags_extremeClose")
            for (r in rows) {
                append(r.date)
                append(',')
                append(r.sessionStartIsoUtc)
                append(',')
                append(r.sessionEndIsoUtc)
                append(',')
                append(r.durationSeconds)
                append(',')
                append(format2(r.avgDistanceCm))
                append(',')
                append(format2(r.minDistanceCm))
                append(',')
                // I format mean lux to two decimals so the added descriptive field stays consistent with the other session averages.
                append(format2(r.meanLux))
                append(',')
                append(format4(r.diopterHoursInSession))
                append(',')
                // I format session NRS to two decimals because this is the same normalized score shown elsewhere in the app.
                append(format2(r.nrs))
                append(',')
                append(r.lowLightSecondsInSession)
                append(',')
                append(r.flagsCloseDistance)
                append(',')
                append(r.flagsLowLight)
                append(',')
                append(r.flagsExtremeClose)
                append('\n')
            }
        }
    }

    fun buildImportQualityCsv(rows: List<ImportQualityRow>): String {
        return buildString {
            appendLine(
                "importedAtIsoUtc,sourceType,filename,totalRows,insertedRows,rejectedRows,rejectedTimestampCount,rejectedDistanceCount,rejectedLuxCount,duplicatesRemovedCount,gapCount,largestGapSeconds,smoothingWindow,thresholds_lowLightLux,thresholds_nearworkCm,thresholds_breakGapSec,thresholds_minSessionSec,thresholds_closeDistanceCm,thresholds_extremeCloseCm"
            )
            for (r in rows) {
                append(r.importedAtIsoUtc)
                append(',')
                append(escape(r.sourceType))
                append(',')
                append(escape(r.filename))
                append(',')
                append(r.totalRows ?: "")
                append(',')
                append(r.insertedRows ?: "")
                append(',')
                append(r.rejectedRows ?: "")
                append(',')
                append(r.rejectedTimestampCount ?: "")
                append(',')
                append(r.rejectedDistanceCount ?: "")
                append(',')
                append(r.rejectedLuxCount ?: "")
                append(',')
                append(r.duplicatesRemovedCount ?: "")
                append(',')
                append(r.gapCount ?: "")
                append(',')
                append(r.largestGapSeconds ?: "")
                append(',')
                append(r.smoothingWindow ?: "")
                append(',')
                append(r.thresholdsLowLightLux)
                append(',')
                append(r.thresholdsNearworkCm)
                append(',')
                append(r.thresholdsBreakGapSec)
                append(',')
                append(r.thresholdsMinSessionSec)
                append(',')
                append(r.thresholdsCloseDistanceCm)
                append(',')
                append(r.thresholdsExtremeCloseCm)
                append('\n')
            }
        }
    }
}

private fun escape(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
    if (!needsQuotes) return value
    return buildString {
        append('"')
        for (c in value) {
            if (c == '"') append("\"\"") else append(c)
        }
        append('"')
    }
}

private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun format4(value: Double): String = String.format(Locale.US, "%.4f", value)


