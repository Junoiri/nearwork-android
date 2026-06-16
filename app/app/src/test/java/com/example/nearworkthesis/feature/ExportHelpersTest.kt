package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.export.ResultsPackCsvBuilder
import com.example.nearworkthesis.domain.export.ResultsPackCsvs
import com.example.nearworkthesis.domain.model.DailySummary
import java.io.ByteArrayInputStream
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportHelpersTest {

    @Test
    fun buildResultsPackZip_includesAllExpectedEntries() {
        val zipBytes = invokeBuildResultsPackZip(
            ResultsPackCsvs(
                manifestJson = """{"profile":"demo"}""",
                dailyResultsCsv = "date,total\n2026-06-05,1\n",
                sessionsResultsCsv = "date,session\n2026-06-05,1\n",
                importQualityCsv = "importedAtIsoUtc,filename\n2026-06-05T00:00:00Z,sample.csv\n",
                daysWithSamples = 1
            )
        )

        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        assertEquals(
            listOf(
                ResultsPackCsvBuilder.manifestFilename,
                ResultsPackCsvBuilder.dailyFilename,
                ResultsPackCsvBuilder.sessionsFilename,
                ResultsPackCsvBuilder.importQualityFilename
            ),
            entries.keys.toList()
        )
        assertEquals("""{"profile":"demo"}""", entries[ResultsPackCsvBuilder.manifestFilename])
        assertTrue(entries[ResultsPackCsvBuilder.dailyFilename]!!.startsWith("date,total"))
        assertTrue(entries[ResultsPackCsvBuilder.sessionsFilename]!!.startsWith("date,session"))
        assertTrue(entries[ResultsPackCsvBuilder.importQualityFilename]!!.startsWith("importedAtIsoUtc,filename"))
    }

    @Test
    fun validateResultsPackCsvs_rejectsMissingHeaders() {
        val error = invokeResultsPackFailure(
            "validateResultsPackCsvs",
            ResultsPackCsvs(
                manifestJson = "{}",
                dailyResultsCsv = "",
                sessionsResultsCsv = "date,session\n2026-06-05,1\n",
                importQualityCsv = "importedAtIsoUtc,filename\n2026-06-05T00:00:00Z,sample.csv\n",
                daysWithSamples = 1
            )
        )

        assertEquals("Daily results CSV is missing its header.", error.message)
    }

    @Test
    fun validateResultsPackCsvs_rejectsMissingSessionAndImportQualityHeaders() {
        val sessionError = invokeResultsPackFailure(
            "validateResultsPackCsvs",
            ResultsPackCsvs(
                manifestJson = "{}",
                dailyResultsCsv = "date,total\n2026-06-05,1\n",
                sessionsResultsCsv = "",
                importQualityCsv = "importedAtIsoUtc,filename\n2026-06-05T00:00:00Z,sample.csv\n",
                daysWithSamples = 1
            )
        )
        val importError = invokeResultsPackFailure(
            "validateResultsPackCsvs",
            ResultsPackCsvs(
                manifestJson = "{}",
                dailyResultsCsv = "date,total\n2026-06-05,1\n",
                sessionsResultsCsv = "date,session\n2026-06-05,1\n",
                importQualityCsv = "",
                daysWithSamples = 1
            )
        )

        assertEquals("Sample results CSV is missing its header.", sessionError.message)
        assertEquals("Import quality CSV is missing its header.", importError.message)
    }

    @Test
    fun hasHeader_distinguishesBlankAndNonBlankCsv() {
        assertEquals(true, invokeHasHeader("header\nvalue\n"))
        assertEquals(false, invokeHasHeader(""))
        assertEquals(false, invokeHasHeader("\nvalue\n"))
    }

    private fun invokeBuildResultsPackZip(csvs: ResultsPackCsvs): ByteArray {
        val method = exportHelpersClass.getDeclaredMethod("buildResultsPackZip", ResultsPackCsvs::class.java)
        method.isAccessible = true
        return method.invoke(null, csvs) as ByteArray
    }

    private fun invokeHasHeader(csv: String): Boolean {
        val method = exportHelpersClass.getDeclaredMethod("hasHeader", String::class.java)
        method.isAccessible = true
        return method.invoke(null, csv) as Boolean
    }

    private fun invokeResultsPackFailure(methodName: String, csvs: ResultsPackCsvs): IllegalArgumentException {
        val method = exportHelpersClass.getDeclaredMethod(methodName, ResultsPackCsvs::class.java)
        method.isAccessible = true
        val thrown = try {
            method.invoke(null, csvs)
            throw AssertionError("Expected invocation to fail")
        } catch (error: InvocationTargetException) {
            error.targetException
        }
        return thrown as IllegalArgumentException
    }

    private companion object {
        val exportHelpersClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.ExportViewModelKt")
    }
}
