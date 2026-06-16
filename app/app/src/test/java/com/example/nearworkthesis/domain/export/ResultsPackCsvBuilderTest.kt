package com.example.nearworkthesis.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsPackCsvBuilderTest {

    @Test
    fun dailyHeader_isStable() {
        val csv = ResultsPackCsvBuilder.buildDailyResultsCsv(emptyList())
        val header = csv.lineSequence().first()
        assertEquals(
            // I pin the daily header here so future export changes cannot silently drop the added NRS column.
            "date,sampleCount,diopterHoursTotal,nrsSessionAverage,lowLightMinutes,longestSessionSeconds,riskySessionCount,gapCount,largestGapSeconds",
            header
        )
    }

    @Test
    fun sessionsHeader_isStable() {
        val csv = ResultsPackCsvBuilder.buildSessionsResultsCsv(emptyList())
        val header = csv.lineSequence().first()
        assertEquals(
            // I pin the session header here so future export changes cannot silently drop the added meanLux or NRS columns.
            "date,sessionStartIsoUtc,sessionEndIsoUtc,durationSeconds,avgDistanceCm,minDistanceCm,meanLux,diopterHoursInSession,nrs,lowLightSecondsInSession,flags_closeDistance,flags_lowLight,flags_extremeClose",
            header
        )
    }

    @Test
    fun importQualityHeader_isStable() {
        val csv = ResultsPackCsvBuilder.buildImportQualityCsv(emptyList())
        val header = csv.lineSequence().first()
        assertEquals(
            "importedAtIsoUtc,sourceType,filename,totalRows,insertedRows,rejectedRows,rejectedTimestampCount,rejectedDistanceCount,rejectedLuxCount,duplicatesRemovedCount,gapCount,largestGapSeconds,smoothingWindow,thresholds_lowLightLux,thresholds_nearworkCm,thresholds_breakGapSec,thresholds_minSessionSec,thresholds_closeDistanceCm,thresholds_extremeCloseCm",
            header
        )
    }

    @Test
    fun dailyRow_isWritten() {
        val csv = ResultsPackCsvBuilder.buildDailyResultsCsv(
            listOf(
                DailyResultsRow(
                    date = "2025-12-16",
                    sampleCount = 3,
                    diopterHoursTotal = 1.23,
                    // I include a non-zero daily NRS here so the row assertion proves the new column is emitted in-order.
                    nrsSessionAverage = 4.56,
                    lowLightMinutes = 4,
                    longestSessionSeconds = 60,
                    riskySessionCount = 1,
                    gapCount = 0,
                    largestGapSeconds = null
                )
            )
        )
        // I assert the new NRS slot here so the row-level test guards the daily export payload as well as the header.
        assertTrue(csv.contains("2025-12-16,3,1.23,4.56,4,60,1,0,"))
    }

    @Test
    fun sessionRow_isWritten() {
        val csv = ResultsPackCsvBuilder.buildSessionsResultsCsv(
            listOf(
                SessionResultsRow(
                    date = "2025-12-16",
                    sessionStartIsoUtc = "2025-12-16T07:00:00",
                    sessionEndIsoUtc = "2025-12-16T07:10:00",
                    durationSeconds = 600,
                    avgDistanceCm = 28.5,
                    minDistanceCm = 18.0,
                    // I include a finite session mean lux here so the row assertion proves the new descriptive light column is emitted.
                    meanLux = 512.34,
                    diopterHoursInSession = 1.2345,
                    // I include a non-zero session NRS here so the row assertion proves the new normalized score column is emitted.
                    nrs = 37.89,
                    lowLightSecondsInSession = 120,
                    flagsCloseDistance = 1,
                    flagsLowLight = 0,
                    flagsExtremeClose = 1
                )
            )
        )
        // I assert the ordered payload here so the test fails if meanLux or NRS shifts position in the exported row.
        assertTrue(csv.contains("2025-12-16,2025-12-16T07:00:00,2025-12-16T07:10:00,600,28.50,18.00,512.34,1.2345,37.89,120,1,0,1"))
    }

    @Test
    fun importQualityRow_escapesCsvFields_andLeavesMissingNumbersBlank() {
        val csv = ResultsPackCsvBuilder.buildImportQualityCsv(
            listOf(
                ImportQualityRow(
                    importedAtIsoUtc = "2025-12-18T00:00:00",
                    sourceType = "HOWFAR,\"USB\"",
                    filename = "demo,\nraw.csv",
                    totalRows = null,
                    insertedRows = 8,
                    rejectedRows = null,
                    rejectedTimestampCount = 1,
                    rejectedDistanceCount = null,
                    rejectedLuxCount = 2,
                    duplicatesRemovedCount = null,
                    gapCount = 3,
                    largestGapSeconds = null,
                    smoothingWindow = null,
                    thresholdsLowLightLux = 300,
                    thresholdsNearworkCm = 60,
                    thresholdsBreakGapSec = 90,
                    thresholdsMinSessionSec = 120,
                    thresholdsCloseDistanceCm = 30,
                    thresholdsExtremeCloseCm = 20
                )
            )
        )

        assertTrue(csv.contains("\"HOWFAR,\"\"USB\"\"\""))
        assertTrue(csv.contains("\"demo,\nraw.csv\""))
        assertTrue(csv.contains("2025-12-18T00:00:00,\"HOWFAR,\"\"USB\"\"\",\"demo,\nraw.csv\",,8,,1,,2,,3,,"))
    }

    @Test
    fun csvBuilder_constants_and_privateFormattingHelpers_remainStable() {
        assertEquals("nearwork_daily_results.csv", ResultsPackCsvBuilder.dailyFilename)
        assertEquals("nearwork_sessions_results.csv", ResultsPackCsvBuilder.sessionsFilename)
        assertEquals("nearwork_import_quality.csv", ResultsPackCsvBuilder.importQualityFilename)
        assertEquals("manifest.json", ResultsPackCsvBuilder.manifestFilename)

        val helperClass = Class.forName("com.example.nearworkthesis.domain.export.ResultsPackCsvBuilderKt")
        val escape = helperClass.getDeclaredMethod("escape", String::class.java).apply { isAccessible = true }
        val format2 = helperClass.getDeclaredMethod("format2", Double::class.javaPrimitiveType).apply { isAccessible = true }
        val format4 = helperClass.getDeclaredMethod("format4", Double::class.javaPrimitiveType).apply { isAccessible = true }

        assertEquals("", escape.invoke(null, null))
        assertEquals("", escape.invoke(null, ""))
        assertEquals("plain", escape.invoke(null, "plain"))
        assertEquals("\"a,b\"", escape.invoke(null, "a,b"))
        assertEquals("12.35", format2.invoke(null, 12.345))
        assertEquals("12.3457", format4.invoke(null, 12.34567))
    }
}
