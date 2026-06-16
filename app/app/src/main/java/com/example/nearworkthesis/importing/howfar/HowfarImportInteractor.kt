package com.example.nearworkthesis.importing.howfar

import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.settings.ActiveProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class HowfarImportInteractor(
    private val storageRepository: HowfarStorageRepository,
    private val howfarUf2Archive: HowfarUf2Archive,
    private val dataParser: HowfarDataParser,
    private val activeProfileStore: ActiveProfileStore,
    private val csvImporter: CsvBytesImportInteractor
) {

    // Mirrors howfar/cli/read.py flow: find config UF2, derive exam ID, read data UF2, then parse.
    // One-tap path: read UF2 from the selected device folder and import.
    suspend fun importFromDevice(
        anchorMillis: Long = System.currentTimeMillis(),
        cropStartMillis: Long? = null,
        cropEndMillis: Long? = null,
        forceAnchorShift: Boolean = false
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            val profileId = activeProfileStore.observeActiveProfileId().first()
                ?: return@withContext ImportResult.Error("No active profile selected.")

            val examIdentifier = runCatching {
                storageRepository.readConfigUf2()?.let { configUf2 ->
                    val raw = HowfarUf2.convertFromUf2(configUf2)
                    HowfarSettingsCodec.fromByteArray(raw).examinationIdentifier
                }?.trimEnd(' ').orEmpty().ifBlank { null }
            }.getOrNull()

            val uf2Bytes = runCatching { storageRepository.readDataUf2(examIdentifier) }
                .getOrElse { t -> return@withContext ImportResult.Error(t.message ?: "Unable to read HowFar data.") }

            val parseResult = runCatching {
                dataParser.parseUf2(
                    uf2Bytes,
                    anchorMillis = anchorMillis,
                    cropStartMillis = cropStartMillis,
                    cropEndMillis = cropEndMillis,
                    forceAnchorShift = forceAnchorShift
                )
            }
                .getOrElse { t -> return@withContext ImportResult.Error(t.message ?: "Unable to parse HowFar data.") }

            val filename = buildFilename(storageRepository.state.value)
            howfarUf2Archive.saveLatest(profileId = profileId, filename = filename, bytes = uf2Bytes)

            csvImporter.importParsedMeasurements(
                profileId = profileId,
                filename = filename,
                sourceType = ImportSourceType.HOWFAR_USB,
                parseResult = parseResult.parseResult
            )
        }
    }

    // Manual path: import from a picked UF2 file (matches howfar-read with an explicit input file).
    suspend fun importFromUf2File(
        filename: String,
        uf2Bytes: ByteArray,
        anchorMillis: Long = System.currentTimeMillis(),
        cropStartMillis: Long? = null,
        cropEndMillis: Long? = null,
        forceAnchorShift: Boolean = false
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            val profileId = activeProfileStore.observeActiveProfileId().first()
                ?: return@withContext ImportResult.Error("No active profile selected.")

            val parseResult = runCatching {
                dataParser.parseUf2(
                    uf2Bytes,
                    anchorMillis = anchorMillis,
                    cropStartMillis = cropStartMillis,
                    cropEndMillis = cropEndMillis,
                    forceAnchorShift = forceAnchorShift
                )
            }
                .getOrElse { t -> return@withContext ImportResult.Error(t.message ?: "Unable to parse HowFar data.") }

            howfarUf2Archive.saveLatest(profileId = profileId, filename = filename, bytes = uf2Bytes)

            csvImporter.importParsedMeasurements(
                profileId = profileId,
                filename = filename,
                sourceType = ImportSourceType.FILE,
                parseResult = parseResult.parseResult
            )
        }
    }

    private fun buildFilename(state: HowfarStorageState): String {
        val name = (state as? HowfarStorageState.Ready)?.info?.displayName ?: "howfar-device"
        return "$name:optodata.uf2"
    }
}
