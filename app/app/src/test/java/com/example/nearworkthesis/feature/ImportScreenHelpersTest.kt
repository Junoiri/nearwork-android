package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportSummary
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportScreenHelpersTest {

    @Test
    fun formatDurationMillis_formatsSecondsMinutesAndHours() {
        assertEquals("0s", invokeFormatDurationMillis(0L))
        assertEquals("45s", invokeFormatDurationMillis(45_000L))
        assertEquals("5m 7s", invokeFormatDurationMillis(307_000L))
        assertEquals("2h 3m", invokeFormatDurationMillis(7_380_000L))
    }

    @Test
    fun isDeviceTimeUnset_flagsVeryOldTimestampsOnly() {
        val oldSummary = sampleSummary(firstTimestampEpochMillis = 1_000L, lastTimestampEpochMillis = 2_000L)
        val recentSummary = sampleSummary(
            firstTimestampEpochMillis = 1_780_704_600_000L,
            lastTimestampEpochMillis = 1_780_708_200_000L
        )
        val unknownSummary = sampleSummary(
            firstTimestampEpochMillis = null,
            lastTimestampEpochMillis = 1_780_708_200_000L
        )

        assertTrue(invokeIsDeviceTimeUnset(oldSummary))
        assertFalse(invokeIsDeviceTimeUnset(recentSummary))
        assertFalse(invokeIsDeviceTimeUnset(unknownSummary))
    }

    @Test
    fun shouldShowRtcDriftWarning_requiresUsbSourceAndLargeTimeDrift() {
        val now = System.currentTimeMillis()
        val staleUsbSummary = sampleSummary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestampEpochMillis = now - 5L * 24L * 60L * 60L * 1000L,
            lastTimestampEpochMillis = now - 3L * 24L * 60L * 60L * 1000L
        )
        val recentUsbSummary = sampleSummary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestampEpochMillis = now - 3_600_000L,
            lastTimestampEpochMillis = now - 1_800_000L
        )
        val staleFileSummary = sampleSummary(
            sourceType = ImportSourceType.FILE,
            firstTimestampEpochMillis = now - 5L * 24L * 60L * 60L * 1000L,
            lastTimestampEpochMillis = now - 3L * 24L * 60L * 60L * 1000L
        )

        assertTrue(invokeShouldShowRtcDriftWarning(staleUsbSummary))
        assertFalse(invokeShouldShowRtcDriftWarning(recentUsbSummary))
        assertFalse(invokeShouldShowRtcDriftWarning(staleFileSummary))
        assertFalse(
            invokeShouldShowRtcDriftWarning(
                sampleSummary(
                    sourceType = ImportSourceType.HOWFAR_USB,
                    firstTimestampEpochMillis = now - 10_000L,
                    lastTimestampEpochMillis = null
                )
            )
        )
        assertFalse(
            invokeShouldShowRtcDriftWarning(
                sampleSummary(
                    sourceType = ImportSourceType.HOWFAR_USB,
                    firstTimestampEpochMillis = 1_000L,
                    lastTimestampEpochMillis = 2_000L
                )
            )
        )
    }

    @Test
    fun formatSummary_includesThresholdsDuplicatesAndGapInformation() {
        val summary = sampleSummary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestampEpochMillis = 1_780_704_600_000L,
            lastTimestampEpochMillis = 1_780_708_200_000L,
            duplicateResolutionPolicy = DuplicateResolutionPolicy.REPLACE_WITH_NEW,
            duplicatesEncounteredCount = 2,
            duplicatesReplacedCount = 2,
            invalidTimestampCount = 1,
            invalidDistanceCount = 2,
            invalidLuxCount = 3,
            croppedByTimeWindowCount = 4,
            croppedByEndWindowCount = 5,
            gapCount = 1,
            largestGapDurationMillis = 125_000L
        )

        val report = invokeFormatSummary(summary)

        assertTrue(report.contains("Source: HOWFAR_USB"))
        assertTrue(report.contains("Duplicate policy: Replace with new"))
        assertTrue(report.contains("Duplicates replaced: 2"))
        assertTrue(report.contains("Rejection reasons:"))
        assertTrue(report.contains("Largest gap: 2m 5s"))
        assertTrue(report.contains("First timestamp:"))
        assertTrue(report.contains("Last timestamp:"))
    }

    @Test
    fun formatSummary_includesUnsetTimeAndKeepExistingDuplicateBranch() {
        val summary = sampleSummary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestampEpochMillis = 1_000L,
            lastTimestampEpochMillis = 2_000L,
            duplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING,
            duplicatesEncounteredCount = 3,
            duplicatesReplacedCount = 0
        ).copy(duplicatesKeptCount = 3)

        val report = invokeFormatSummary(summary)

        assertTrue(report.contains("Duplicate policy: Keep existing"))
        assertTrue(report.contains("Duplicates kept: 3"))
        assertTrue(report.contains("device time looks unset"))
    }

    @Test
    fun formatSummary_omitsOptionalSections_whenThereAreNoDuplicatesOrGaps() {
        val report = invokeFormatSummary(
            sampleSummary(
                sourceType = ImportSourceType.FILE,
                firstTimestampEpochMillis = null,
                lastTimestampEpochMillis = null,
                duplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING,
                duplicatesEncounteredCount = 0,
                duplicatesReplacedCount = 0,
                gapCount = 0,
                largestGapDurationMillis = null
            )
        )

        assertTrue(report.contains("Duplicate policy: Keep existing"))
        assertFalse(report.contains("Duplicates encountered:"))
        assertFalse(report.contains("Gaps detected:"))
        assertFalse(report.contains("First timestamp:"))
        assertFalse(report.contains("Last timestamp:"))
    }

    private fun sampleSummary(
        sourceType: ImportSourceType = ImportSourceType.ASSET,
        firstTimestampEpochMillis: Long?,
        lastTimestampEpochMillis: Long?,
        duplicateResolutionPolicy: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING,
        duplicatesEncounteredCount: Int = 0,
        duplicatesReplacedCount: Int = 0,
        invalidTimestampCount: Int = 0,
        invalidDistanceCount: Int = 0,
        invalidLuxCount: Int = 0,
        croppedByTimeWindowCount: Int = 0,
        croppedByEndWindowCount: Int = 0,
        gapCount: Int = 0,
        largestGapDurationMillis: Long? = null
    ): ImportSummary {
        return ImportSummary(
            filename = "sample.csv",
            sourceType = sourceType,
            totalRows = 20,
            insertedRows = 18,
            rejectedRows = 2,
            invalidTimestampCount = invalidTimestampCount,
            invalidDistanceCount = invalidDistanceCount,
            invalidLuxCount = invalidLuxCount,
            croppedByTimeWindowCount = croppedByTimeWindowCount,
            croppedByEndWindowCount = croppedByEndWindowCount,
            gapCount = gapCount,
            largestGapDurationMillis = largestGapDurationMillis,
            firstTimestampEpochMillis = firstTimestampEpochMillis,
            lastTimestampEpochMillis = lastTimestampEpochMillis,
            timezoneId = ZoneId.systemDefault().id,
            duplicateResolutionPolicy = duplicateResolutionPolicy,
            duplicatesEncounteredCount = duplicatesEncounteredCount,
            duplicatesReplacedCount = duplicatesReplacedCount
        )
    }

    private fun invokeFormatDurationMillis(durationMillis: Long): String {
        val method = importScreenHelpersClass.getDeclaredMethod("formatDurationMillis", Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(null, durationMillis) as String
    }

    private fun invokeIsDeviceTimeUnset(summary: ImportSummary): Boolean {
        val method = importScreenHelpersClass.getDeclaredMethod("isDeviceTimeUnset", ImportSummary::class.java)
        method.isAccessible = true
        return method.invoke(null, summary) as Boolean
    }

    private fun invokeShouldShowRtcDriftWarning(summary: ImportSummary): Boolean {
        val method = importScreenHelpersClass.getDeclaredMethod("shouldShowRtcDriftWarning", ImportSummary::class.java)
        method.isAccessible = true
        return method.invoke(null, summary) as Boolean
    }

    private fun invokeFormatSummary(summary: ImportSummary): String {
        val method = importScreenHelpersClass.getDeclaredMethod("formatSummary", ImportSummary::class.java)
        method.isAccessible = true
        return method.invoke(null, summary) as String
    }

    private companion object {
        val importScreenHelpersClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.ImportScreenKt")
    }
}
