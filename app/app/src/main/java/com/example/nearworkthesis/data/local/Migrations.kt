package com.example.nearworkthesis.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object Migrations {
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN invalidTimestampCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN invalidDistanceCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN invalidLuxCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN duplicatesRemovedCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN gapCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN largestGapDurationMillis INTEGER")

            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_measurements_profileId_timestampEpochMillis " +
                    "ON measurements(profileId, timestampEpochMillis)"
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val defaultTimezone = runCatching { ZoneId.systemDefault().id }.getOrElse { "UTC" }

            db.execSQL("ALTER TABLE profiles ADD COLUMN timezoneId TEXT NOT NULL DEFAULT '$defaultTimezone'")

            db.execSQL("ALTER TABLE measurements ADD COLUMN localDay TEXT NOT NULL DEFAULT ''")
            db.beginTransaction()
            try {
                val updateStatement = db.compileStatement(
                    "UPDATE measurements SET localDay = ? WHERE id = ?"
                )
                db.query("SELECT id, timezoneId FROM profiles").use { profileCursor ->
                    val profileIdIndex = profileCursor.getColumnIndexOrThrow("id")
                    val timezoneIdIndex = profileCursor.getColumnIndexOrThrow("timezoneId")
                    while (profileCursor.moveToNext()) {
                        val profileId = profileCursor.getLong(profileIdIndex)
                        val timezoneId = profileCursor.getString(timezoneIdIndex)
                        val zoneId = runCatching { ZoneId.of(timezoneId) }.getOrElse { ZoneOffset.UTC }
                        db.query(
                            "SELECT id, timestampEpochMillis FROM measurements WHERE profileId = ?",
                            arrayOf(profileId.toString())
                        ).use { measurementCursor ->
                            val measurementIdIndex = measurementCursor.getColumnIndexOrThrow("id")
                            val timestampIndex = measurementCursor.getColumnIndexOrThrow("timestampEpochMillis")
                            while (measurementCursor.moveToNext()) {
                                val measurementId = measurementCursor.getLong(measurementIdIndex)
                                val timestampMillis = measurementCursor.getLong(timestampIndex)
                                val localDay = Instant.ofEpochMilli(timestampMillis)
                                    .atZone(zoneId)
                                    .toLocalDate()
                                    .toString()
                                updateStatement.bindString(1, localDay)
                                updateStatement.bindLong(2, measurementId)
                                updateStatement.executeUpdateDelete()
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_measurements_profileId_localDay " +
                    "ON measurements(profileId, localDay)"
            )

            db.execSQL("ALTER TABLE import_sessions ADD COLUMN timezoneId TEXT NOT NULL DEFAULT '$defaultTimezone'")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN settingsSnapshotJson TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE import_sessions ADD COLUMN pipelineSnapshotJson TEXT NOT NULL DEFAULT '{}'")
        }
    }

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // I leave old profiles nullable here because we never had this field before.
            db.execSQL("ALTER TABLE profiles ADD COLUMN dateOfBirth TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val dropped = runCatching {
                db.execSQL("ALTER TABLE import_sessions DROP COLUMN settingsSnapshotJson")
                db.execSQL("ALTER TABLE import_sessions DROP COLUMN pipelineSnapshotJson")
            }.isSuccess

            if (!dropped) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS import_sessions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        importedAtEpochMillis INTEGER NOT NULL,
                        filename TEXT NOT NULL,
                        totalRows INTEGER NOT NULL,
                        insertedRows INTEGER NOT NULL,
                        rejectedRows INTEGER NOT NULL,
                        invalidTimestampCount INTEGER NOT NULL,
                        invalidDistanceCount INTEGER NOT NULL,
                        invalidLuxCount INTEGER NOT NULL,
                        duplicatesRemovedCount INTEGER NOT NULL,
                        gapCount INTEGER NOT NULL,
                        largestGapDurationMillis INTEGER,
                        firstTimestampEpochMillis INTEGER,
                        lastTimestampEpochMillis INTEGER,
                        source TEXT NOT NULL,
                        timezoneId TEXT NOT NULL,
                        note TEXT,
                        status TEXT,
                        appVersion TEXT,
                        schemaVersion TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO import_sessions_new (
                        id,
                        profileId,
                        importedAtEpochMillis,
                        filename,
                        totalRows,
                        insertedRows,
                        rejectedRows,
                        invalidTimestampCount,
                        invalidDistanceCount,
                        invalidLuxCount,
                        duplicatesRemovedCount,
                        gapCount,
                        largestGapDurationMillis,
                        firstTimestampEpochMillis,
                        lastTimestampEpochMillis,
                        source,
                        timezoneId,
                        note,
                        status,
                        appVersion,
                        schemaVersion
                    )
                    SELECT
                        id,
                        profileId,
                        importedAtEpochMillis,
                        filename,
                        totalRows,
                        insertedRows,
                        rejectedRows,
                        invalidTimestampCount,
                        invalidDistanceCount,
                        invalidLuxCount,
                        duplicatesRemovedCount,
                        gapCount,
                        largestGapDurationMillis,
                        firstTimestampEpochMillis,
                        lastTimestampEpochMillis,
                        source,
                        timezoneId,
                        note,
                        status,
                        appVersion,
                        schemaVersion
                    FROM import_sessions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE import_sessions")
                db.execSQL("ALTER TABLE import_sessions_new RENAME TO import_sessions")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_import_sessions_profileId ON import_sessions(profileId)"
                )
            }
        }
    }
}


