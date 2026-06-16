package com.example.nearworkthesis.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleImportOptionsTest {

    @Test
    fun sampleImportOptions_exposeExpectedLabelsAndCsvFiles() {
        assertEquals(4, SampleImportOptions.size)
        assertEquals("5 June 2026", SampleImportOptions.first().label)
        assertEquals("optodata_2026-06-08.csv", SampleImportOptions.last().fileName)
        assertTrue(SampleImportOptions.all { it.fileName.endsWith(".csv") })
    }

    @Test
    fun sampleImportOption_dataClassStoresValues() {
        val option = SampleImportOption(label = "Demo", fileName = "demo.csv")

        assertEquals("Demo", option.label)
        assertEquals("demo.csv", option.fileName)
    }
}
