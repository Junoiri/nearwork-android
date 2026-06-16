package com.example.nearworkthesis.settings

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activeProfileDataStore by preferencesDataStore(name = "active_profile")

class DataStoreActiveProfileStore(
    private val context: Context
) : ActiveProfileStore {

    override fun observeActiveProfileId(): Flow<Long?> {
        return context.activeProfileDataStore.data.map { prefs ->
            prefs[Keys.activeProfileId]
        }
    }

    override suspend fun setActiveProfileId(id: Long) {
        context.activeProfileDataStore.edit { prefs ->
            prefs[Keys.activeProfileId] = id
        }
    }

    private object Keys {
        val activeProfileId = longPreferencesKey("active_profile_id")
    }
}

