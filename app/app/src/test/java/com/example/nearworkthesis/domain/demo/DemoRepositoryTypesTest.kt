package com.example.nearworkthesis.domain.demo

import com.example.nearworkthesis.domain.ImportResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoRepositoryTypesTest {

    @Test
    fun demoDataset_holdsFilename() {
        assertEquals("sample.csv", DemoDataset("sample.csv").filename)
    }

    @Test
    fun demoRepository_contract_canBeImplementedDeterministically() = runTest {
        val repository: DemoRepository = object : DemoRepository {
            override suspend fun listDemoDatasets(): List<DemoDataset> = listOf(DemoDataset("one.csv"))
            override suspend fun importDemoDataset(profileId: Long, filename: String): ImportResult =
                ImportResult.Error("$profileId:$filename")
            override suspend fun clearProfileData(profileId: Long) = Unit
        }

        assertEquals(listOf(DemoDataset("one.csv")), repository.listDemoDatasets())
        assertEquals(ImportResult.Error("7:one.csv"), repository.importDemoDataset(7L, "one.csv"))
    }
}
