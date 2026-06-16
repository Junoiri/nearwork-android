package com.example.nearworkthesis.importing

import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportStatusRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveProfileImportStatusRepository(
    private val interactor: ImportSamplesInteractor,
    private val csvImporter: CsvBytesImportInteractor,
    private val measurementRepository: MeasurementRepository,
    private val activeProfileStore: ActiveProfileStore
) : ImportStatusRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _hasImportedData = MutableStateFlow(false)
    override val hasImportedData: StateFlow<Boolean> = _hasImportedData.asStateFlow()

    init {
        scope.launch {
            activeProfileStore.observeActiveProfileId()
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { profileId ->
                    measurementRepository.observeMeasurementCount(profileId).map { it > 0 }
                }
                .collect { hasData ->
                    _hasImportedData.value = hasData
                }
        }
    }

    override suspend fun importSample(fileName: String): ImportResult {
        val profileId = activeProfileStore.observeActiveProfileId().first()
            ?: return ImportResult.Error("No active profile selected.")
        val result = interactor.importSample(fileName, profileId)
        if (result is ImportResult.Success && result.summary.insertedRows > 0) {
            _hasImportedData.value = true
        }
        return result
    }

    override suspend fun importCsvBytes(
        filename: String,
        bytes: ByteArray,
        sourceType: ImportSourceType
    ): ImportResult {
        val profileId = activeProfileStore.observeActiveProfileId().first()
            ?: return ImportResult.Error("No active profile selected.")

        val result = csvImporter.importCsvBytes(
            profileId = profileId,
            filename = filename,
            sourceType = sourceType,
            bytes = bytes
        )
        if (result is ImportResult.Success && result.summary.insertedRows > 0) {
            _hasImportedData.value = true
        }
        return result
    }
}

