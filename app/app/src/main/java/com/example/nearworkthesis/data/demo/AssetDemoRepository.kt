package com.example.nearworkthesis.data.demo

import android.content.Context
import androidx.room.withTransaction
import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.importing.RoomImportTransactionRunner
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.data.repository.RoomImportSessionRepository
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.demo.DemoDataset
import com.example.nearworkthesis.domain.demo.DemoRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEMO_ASSET_DIRECTORY = "nearwork_samples"

class AssetDemoRepository(
    private val context: Context,
    private val database: NearworkDatabase,
    private val measurementRepository: MeasurementRepository,
    settingsStore: SettingsStore,
    profileRepository: ProfileRepository
) : DemoRepository {

    private val importSessionRepository = RoomImportSessionRepository(database.nearworkDao())
    private val csvImporter = CsvBytesImportInteractor(
        transactionRunner = RoomImportTransactionRunner(database),
        importSessionRepository = importSessionRepository,
        measurementRepository = measurementRepository,
        settingsStore = settingsStore,
        profileRepository = profileRepository
    )

    override suspend fun listDemoDatasets(): List<DemoDataset> {
        return withContext(Dispatchers.IO) {
            val files = context.assets.list(DEMO_ASSET_DIRECTORY)?.toList().orEmpty()
            files
                .filter { it.endsWith(".csv", ignoreCase = true) }
                .map { DemoDataset(filename = it) }
        }
    }

    override suspend fun importDemoDataset(profileId: Long, filename: String): ImportResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.assets.open("$DEMO_ASSET_DIRECTORY/$filename").use { it.readBytes() }
                csvImporter.importCsvBytes(
                    profileId = profileId,
                    filename = filename,
                    sourceType = ImportSourceType.ASSET,
                    bytes = bytes
                )
            }.getOrElse { t ->
                ImportResult.Error(t.message ?: "Unable to import demo dataset.")
            }
        }
    }

    override suspend fun clearProfileData(profileId: Long) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.nearworkDao().deleteMeasurementsForProfile(profileId)
                database.nearworkDao().deleteImportSessionsForProfile(profileId)
            }
        }
    }
}
