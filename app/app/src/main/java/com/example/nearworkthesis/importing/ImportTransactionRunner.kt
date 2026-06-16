package com.example.nearworkthesis.importing

import androidx.room.withTransaction
import com.example.nearworkthesis.data.local.NearworkDatabase

interface ImportTransactionRunner {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

class RoomImportTransactionRunner(
    private val database: NearworkDatabase
) : ImportTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}

