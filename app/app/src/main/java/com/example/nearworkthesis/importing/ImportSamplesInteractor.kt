package com.example.nearworkthesis.importing

import android.content.res.AssetManager
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.repository.ImportSessionRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_ASSET_DIRECTORY = "nearwork_samples"

class ImportSamplesInteractor(
    private val assetManager: AssetManager,
    database: NearworkDatabase,
    importSessionRepository: ImportSessionRepository,
    measurementRepository: MeasurementRepository,
    settingsStore: SettingsStore,
    profileRepository: ProfileRepository,
    parser: SampleCsvParser = SampleCsvParser()
) {

    private val csvImporter = CsvBytesImportInteractor(
        transactionRunner = RoomImportTransactionRunner(database),
        importSessionRepository = importSessionRepository,
        measurementRepository = measurementRepository,
        settingsStore = settingsStore,
        profileRepository = profileRepository,
        parser = parser
    )

    suspend fun importSample(fileName: String, profileId: Long): ImportResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = assetManager.open("$SAMPLE_ASSET_DIRECTORY/$fileName").use { it.readBytes() }
                csvImporter.importCsvBytes(
                    profileId = profileId,
                    filename = fileName,
                    sourceType = ImportSourceType.ASSET,
                    bytes = bytes
                )
            }.getOrElse { throwable ->
                ImportResult.Error(throwable.message ?: "Unable to import sample.")
            }
        }
    }
}

