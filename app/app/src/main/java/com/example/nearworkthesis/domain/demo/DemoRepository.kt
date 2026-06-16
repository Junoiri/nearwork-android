package com.example.nearworkthesis.domain.demo

import com.example.nearworkthesis.domain.ImportResult

data class DemoDataset(
    val filename: String
)

interface DemoRepository {
    suspend fun listDemoDatasets(): List<DemoDataset>

    suspend fun importDemoDataset(profileId: Long, filename: String): ImportResult

    suspend fun clearProfileData(profileId: Long)
}

