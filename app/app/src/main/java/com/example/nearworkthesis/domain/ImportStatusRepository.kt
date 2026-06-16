package com.example.nearworkthesis.domain

import kotlinx.coroutines.flow.StateFlow

sealed class ImportResult {
    data class Success(val summary: ImportSummary) : ImportResult()
    data class Error(val message: String) : ImportResult()
    data class NoNewData(val summary: ImportSummary) : ImportResult()
}

interface ImportStatusRepository {
    val hasImportedData: StateFlow<Boolean>

    suspend fun importSample(fileName: String): ImportResult

    suspend fun importCsvBytes(
        filename: String,
        bytes: ByteArray,
        sourceType: ImportSourceType
    ): ImportResult
}
