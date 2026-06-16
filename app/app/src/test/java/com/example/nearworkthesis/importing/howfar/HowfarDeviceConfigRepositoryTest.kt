package com.example.nearworkthesis.importing.howfar

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.device.DeviceConnectionState
import com.example.nearworkthesis.domain.device.DeviceSettings
import com.example.nearworkthesis.domain.device.DeviceSettingsDefaults
import com.example.nearworkthesis.domain.device.DeviceTimeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HowfarDeviceConfigRepositoryTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun observeConnectionState_mapsStorageStates() = runBlocking {
        clearTestAppState()
        val storage = FakeHowfarStorageRepo()
        val repository = HowfarDeviceConfigRepository(appContext, storage)

        storage.stateFlow.value = HowfarStorageState.Disconnected
        assertEquals(DeviceConnectionState.Disconnected, repository.observeConnectionState().first())

        storage.stateFlow.value = HowfarStorageState.Ready(
            HowfarDeviceInfo(Uri.parse("content://howfar/device"), "Drive")
        )
        assertEquals(DeviceConnectionState.Connected, repository.observeConnectionState().first())

        storage.stateFlow.value = HowfarStorageState.Error("broken")
        assertEquals(DeviceConnectionState.Error("broken"), repository.observeConnectionState().first())
    }

    @Test
    fun refreshConnection_delegatesToStorageRepository() {
        clearTestAppState()
        val storage = FakeHowfarStorageRepo()
        val repository = createRepository(storage)

        repository.refreshConnection()

        assertTrue(storage.refreshCalled)
    }

    @Test
    fun writeSettings_withInvalidValues_returnsFailure_andSkipsWrite() = runBlocking {
        clearTestAppState()
        val storage = FakeHowfarStorageRepo()
        val repository = createRepository(storage)

        val result = repository.writeSettings(
            DeviceSettings(
                samplingIntervalSeconds = 0,
                autoShutdownMinutes = 30,
                lowLightLuxThreshold = 150,
                enableLowPowerMode = true,
                deviceTimeMode = DeviceTimeMode.USE_PHONE_TIME_UTC
            )
        )

        assertTrue(result.isFailure)
        assertEquals(null, storage.writtenConfigUf2)
    }

    @Test
    fun buildConfigUf2_encodesSettingsIntoHowfarPayload() = runBlocking {
        clearTestAppState()
        val storage = FakeHowfarStorageRepo()
        val repository = createRepository(storage)
        val settings = DeviceSettings(
            samplingIntervalSeconds = 17,
            autoShutdownMinutes = 45,
            lowLightLuxThreshold = 250,
            enableLowPowerMode = false,
            deviceTimeMode = DeviceTimeMode.USE_PHONE_TIME_UTC
        )

        val uf2 = repository.buildConfigUf2(settings).getOrThrow()
        val decoded = HowfarSettingsCodec.fromByteArray(HowfarUf2.convertFromUf2(uf2))

        assertEquals(17L, decoded.measurementIntervalSeconds)
        assertFalse(decoded.featureDcdcSleep)
    }

    @Test
    fun clearDeviceData_setsEraseFlagInWrittenConfig() = runBlocking {
        clearTestAppState()
        val baseSettings = HowfarSettings.defaults().copy(timestampSeconds = 123_456L)
        val storage = FakeHowfarStorageRepo(
            configUf2 = HowfarUf2.convertToUf2(HowfarSettingsCodec.toByteArray(baseSettings))
        )
        val repository = createRepository(storage)

        repository.clearDeviceData(DeviceSettingsDefaults.defaults).getOrThrow()

        val written = storage.writtenConfigUf2 ?: error("Expected config write")
        val decoded = HowfarSettingsCodec.fromByteArray(HowfarUf2.convertFromUf2(written))
        assertTrue(decoded.flagEraseDatabase)
        assertEquals(DeviceSettingsDefaults.defaults.samplingIntervalSeconds.toLong(), decoded.measurementIntervalSeconds)
        assertTrue(decoded.timestampSeconds > 0L)
    }

    @Test
    fun readSettings_withoutConfig_usesHowfarDefaultsAndStoredLocalValues() = runBlocking {
        clearTestAppState()
        val localSettings = DeviceSettings(
            samplingIntervalSeconds = 22,
            autoShutdownMinutes = 55,
            lowLightLuxThreshold = 333,
            enableLowPowerMode = true,
            deviceTimeMode = DeviceTimeMode.KEEP_DEVICE_TIME
        )
        createRepository(FakeHowfarStorageRepo()).writeSettings(localSettings).getOrThrow()

        val loaded = createRepository(FakeHowfarStorageRepo()).readSettings().getOrThrow()

        assertEquals(HowfarSettings.defaults().measurementIntervalSeconds.toInt(), loaded.samplingIntervalSeconds)
        assertEquals(55, loaded.autoShutdownMinutes)
        assertEquals(333, loaded.lowLightLuxThreshold)
        assertFalse(loaded.enableLowPowerMode)
        assertEquals(DeviceTimeMode.KEEP_DEVICE_TIME, loaded.deviceTimeMode)
    }

    @Test
    fun readSettings_withConfig_mergesDeviceValuesWithStoredLocalPreferences() = runBlocking {
        clearTestAppState()
        createRepository(FakeHowfarStorageRepo()).writeSettings(
            DeviceSettings(
                samplingIntervalSeconds = 15,
                autoShutdownMinutes = 61,
                lowLightLuxThreshold = 444,
                enableLowPowerMode = true,
                deviceTimeMode = DeviceTimeMode.KEEP_DEVICE_TIME
            )
        ).getOrThrow()

        val deviceSettings = HowfarSettings.defaults().copy(
            measurementIntervalSeconds = 27,
            featureDcdcSleep = true
        )
        val storage = FakeHowfarStorageRepo(
            configUf2 = HowfarUf2.convertToUf2(HowfarSettingsCodec.toByteArray(deviceSettings))
        )

        val loaded = createRepository(storage).readSettings().getOrThrow()

        assertEquals(27, loaded.samplingIntervalSeconds)
        assertEquals(61, loaded.autoShutdownMinutes)
        assertEquals(444, loaded.lowLightLuxThreshold)
        assertTrue(loaded.enableLowPowerMode)
        assertEquals(DeviceTimeMode.KEEP_DEVICE_TIME, loaded.deviceTimeMode)
    }

    @Test
    fun writeSettings_withKeepDeviceTime_preservesTimestampFromExistingConfig() = runBlocking {
        clearTestAppState()
        val baseSettings = HowfarSettings.defaults().copy(timestampSeconds = 7_654_321L)
        val storage = FakeHowfarStorageRepo(
            configUf2 = HowfarUf2.convertToUf2(HowfarSettingsCodec.toByteArray(baseSettings))
        )
        val repository = createRepository(storage)

        repository.writeSettings(
            DeviceSettings(
                samplingIntervalSeconds = 19,
                autoShutdownMinutes = 40,
                lowLightLuxThreshold = 180,
                enableLowPowerMode = true,
                deviceTimeMode = DeviceTimeMode.KEEP_DEVICE_TIME
            )
        ).getOrThrow()

        val written = storage.writtenConfigUf2 ?: error("Expected config write")
        val decoded = HowfarSettingsCodec.fromByteArray(HowfarUf2.convertFromUf2(written))
        assertEquals(7_654_321L, decoded.timestampSeconds)
        assertEquals(19L, decoded.measurementIntervalSeconds)
        assertTrue(decoded.featureDcdcSleep)
    }

    @Test
    fun resetToDefaults_returnsAndWritesDefaultSettings() = runBlocking {
        clearTestAppState()
        val storage = FakeHowfarStorageRepo()
        val repository = createRepository(storage)

        val reset = repository.resetToDefaults().getOrThrow()

        assertEquals(DeviceSettingsDefaults.defaults, reset)

        val written = storage.writtenConfigUf2 ?: error("Expected config write")
        val decoded = HowfarSettingsCodec.fromByteArray(HowfarUf2.convertFromUf2(written))
        assertEquals(DeviceSettingsDefaults.defaults.samplingIntervalSeconds.toLong(), decoded.measurementIntervalSeconds)
        assertTrue(decoded.featureDcdcSleep)
        assertTrue(decoded.timestampSeconds > 0L)
    }

    private fun createRepository(storage: HowfarStorageRepository): DeviceConfigRepository {
        return HowfarDeviceConfigRepository(appContext, storage)
    }

    private fun clearTestAppState() {
        appContext.filesDir.resolve("howfar").deleteRecursively()
        appContext.filesDir.parentFile?.resolve("datastore")?.deleteRecursively()
    }
}

private class FakeHowfarStorageRepo(
    private var configUf2: ByteArray? = null
) : HowfarStorageRepository {
    val stateFlow = MutableStateFlow<HowfarStorageState>(HowfarStorageState.Disconnected)
    override val state = stateFlow
    var refreshCalled = false
    var writtenConfigUf2: ByteArray? = null

    override fun refresh() {
        refreshCalled = true
    }

    override fun setDeviceTreeUri(uri: Uri?) = Unit
    override fun deviceTreeUri(): Uri? = null
    override suspend fun readDataUf2(examIdentifier: String?): ByteArray = ByteArray(0)
    override suspend fun readConfigUf2(): ByteArray? = configUf2
    override suspend fun writeConfigUf2(bytes: ByteArray) {
        configUf2 = bytes
        writtenConfigUf2 = bytes
    }
    override fun close() = Unit
}
