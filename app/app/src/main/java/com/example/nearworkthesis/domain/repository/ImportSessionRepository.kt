package com.example.nearworkthesis.domain.repository

import com.example.nearworkthesis.domain.model.ImportSession

interface ImportSessionRepository {
    suspend fun getSessionsForProfile(profileId: Long): List<ImportSession>
    suspend fun upsertSession(session: ImportSession): Long
}
