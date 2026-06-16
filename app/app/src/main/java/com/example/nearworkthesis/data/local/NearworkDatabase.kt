package com.example.nearworkthesis.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        ImportSessionEntity::class,
        MeasurementEntity::class
    ],
    // I bump the schema here because import sessions no longer persist snapshot JSON fields.
    version = 6,
    exportSchema = false
)
abstract class NearworkDatabase : RoomDatabase() {
    abstract fun nearworkDao(): NearworkDao

    companion object {
        const val DB_VERSION = 6
    }
}


