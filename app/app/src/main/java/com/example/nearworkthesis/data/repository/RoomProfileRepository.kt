package com.example.nearworkthesis.data.repository

import com.example.nearworkthesis.data.local.NearworkDao
import com.example.nearworkthesis.data.local.ProfileEntity
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId

class RoomProfileRepository(
    private val nearworkDao: NearworkDao
) : ProfileRepository {

    override suspend fun getProfiles(): List<Profile> {
        return nearworkDao.getProfiles().map { it.toDomain() }
    }

    override fun observeProfiles(): Flow<List<Profile>> {
        return nearworkDao.observeProfiles().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getProfile(profileId: Long): Profile? {
        return nearworkDao.getProfile(profileId)?.toDomain()
    }

    override suspend fun upsertProfile(profile: Profile): Long {
        return nearworkDao.upsertProfile(profile.toEntity())
    }

    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long {
        return nearworkDao.insertProfile(
            ProfileEntity(
                name = name,
                createdAtEpochMillis = createdAtEpochMillis,
                timezoneId = ZoneId.systemDefault().id,
                // I keep this nullable so older profiles and quick adds do not need a fake date.
                dateOfBirth = dateOfBirth
            )
        )
    }

    override suspend fun renameProfile(profileId: Long, name: String) {
        val existing = nearworkDao.getProfile(profileId) ?: return
        nearworkDao.updateProfile(existing.copy(name = name))
    }

    override suspend fun deleteProfile(profileId: Long) {
        nearworkDao.deleteMeasurementsForProfile(profileId)
        nearworkDao.deleteImportSessionsForProfile(profileId)
        nearworkDao.deleteProfile(profileId)
    }

    override suspend fun countMeasurements(profileId: Long): Int {
        return nearworkDao.countMeasurements(profileId)
    }
}

private fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    name = name,
    createdAtEpochMillis = createdAtEpochMillis,
    timezoneId = timezoneId,
    dateOfBirth = dateOfBirth
)

private fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    createdAtEpochMillis = createdAtEpochMillis,
    timezoneId = timezoneId,
    dateOfBirth = dateOfBirth
)

