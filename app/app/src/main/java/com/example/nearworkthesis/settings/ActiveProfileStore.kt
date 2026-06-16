package com.example.nearworkthesis.settings

import kotlinx.coroutines.flow.Flow

interface ActiveProfileStore {
    fun observeActiveProfileId(): Flow<Long?>
    suspend fun setActiveProfileId(id: Long)
}

