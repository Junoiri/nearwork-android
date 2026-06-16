package com.example.nearworkthesis.domain.repository

import com.example.nearworkthesis.domain.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun getProfiles(): List<Profile>
    fun observeProfiles(): Flow<List<Profile>>
    suspend fun getProfile(profileId: Long): Profile?
    suspend fun upsertProfile(profile: Profile): Long
    suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String? = null): Long
    suspend fun renameProfile(profileId: Long, name: String)
    suspend fun deleteProfile(profileId: Long)
    suspend fun countMeasurements(profileId: Long): Int
}
