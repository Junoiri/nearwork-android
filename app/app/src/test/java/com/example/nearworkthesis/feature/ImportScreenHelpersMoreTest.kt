package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportScreenHelpersMoreTest {

    @Test
    fun importSummaryHelpers_formatDuration_andDeviceTimeFlags() {
        val staleSummary = summary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestamp = 1_000L,
            lastTimestamp = 2_000L
        )
        val currentSummary = summary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestamp = System.currentTimeMillis() - 1_000L,
            lastTimestamp = System.currentTimeMillis() - 500L
        )

        assertTrue(invokeStatic("isDeviceTimeUnset", staleSummary, ImportSummary::class.java) as Boolean)
        assertFalse(invokeStatic("isDeviceTimeUnset", currentSummary, ImportSummary::class.java) as Boolean)
        assertEquals("0s", invokeStatic("formatDurationMillis", 0L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("59s", invokeStatic("formatDurationMillis", 59_000L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("2m 5s", invokeStatic("formatDurationMillis", 125_000L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("2h 5m", invokeStatic("formatDurationMillis", 7_500_000L, Long::class.javaPrimitiveType!!) as String)
    }

    @Test
    fun importSummaryHelpers_buildReports_andRtcDriftWarning() {
        val recentUsbSummary = summary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestamp = System.currentTimeMillis() - 10_000L,
            lastTimestamp = System.currentTimeMillis() - 5_000L,
            duplicatesEncountered = 3,
            duplicatesKept = 3
        )
        val staleUsbSummary = summary(
            sourceType = ImportSourceType.HOWFAR_USB,
            firstTimestamp = System.currentTimeMillis() - (72L * 60L * 60L * 1000L),
            lastTimestamp = System.currentTimeMillis() - (72L * 60L * 60L * 1000L),
            duplicatesEncountered = 2,
            duplicatesReplaced = 2,
            policy = DuplicateResolutionPolicy.REPLACE_WITH_NEW
        )

        val report = invokeStatic("formatSummary", recentUsbSummary, ImportSummary::class.java) as String

        assertTrue(report.contains("Source: HOWFAR_USB"))
        assertTrue(report.contains("Duplicates encountered: 3"))
        assertTrue(report.contains("Duplicates kept: 3"))
        assertFalse(invokeStatic("shouldShowRtcDriftWarning", recentUsbSummary, ImportSummary::class.java) as Boolean)
        assertTrue(invokeStatic("shouldShowRtcDriftWarning", staleUsbSummary, ImportSummary::class.java) as Boolean)
    }

    @Test
    fun importSummaryHelpers_coverReplacedDuplicates_andNonUsbNoRtcWarning() {
        val nonUsbSummary = summary(
            sourceType = ImportSourceType.FILE,
            firstTimestamp = System.currentTimeMillis() - (72L * 60L * 60L * 1000L),
            lastTimestamp = System.currentTimeMillis() - (72L * 60L * 60L * 1000L),
            duplicatesEncountered = 2,
            duplicatesReplaced = 2,
            policy = DuplicateResolutionPolicy.REPLACE_WITH_NEW
        )

        val report = invokeStatic("formatSummary", nonUsbSummary, ImportSummary::class.java) as String

        assertTrue(report.contains("Duplicates replaced: 2"))
        assertTrue(report.contains("Largest gap: 2m 5s"))
        assertFalse(invokeStatic("shouldShowRtcDriftWarning", nonUsbSummary, ImportSummary::class.java) as Boolean)
    }

    private fun summary(
        sourceType: ImportSourceType,
        firstTimestamp: Long?,
        lastTimestamp: Long?,
        duplicatesEncountered: Int = 0,
        duplicatesKept: Int = 0,
        duplicatesReplaced: Int = 0,
        policy: DuplicateResolutionPolicy = DuplicateResolutionPolicy.KEEP_EXISTING
    ) = ImportSummary(
        filename = "import.uf2",
        sourceType = sourceType,
        totalRows = 10,
        insertedRows = 8,
        rejectedRows = 2,
        firstTimestampEpochMillis = firstTimestamp,
        lastTimestampEpochMillis = lastTimestamp,
        duplicateResolutionPolicy = policy,
        duplicatesEncounteredCount = duplicatesEncountered,
        duplicatesKeptCount = duplicatesKept,
        duplicatesReplacedCount = duplicatesReplaced,
        gapCount = 1,
        largestGapDurationMillis = 125_000L
    )

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = importScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private fun invokeStatic(name: String, arg: Long, type: Class<*>): Any? {
        val method = importScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private companion object {
        val importScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.ImportScreenKt")
    }
}
