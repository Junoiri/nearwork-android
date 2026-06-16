/** Connects app settings to HowFar config UF2; mirrors howfar/cli/conf.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.device.DeviceConnectionState
import com.example.nearworkthesis.domain.device.DeviceSettings
import com.example.nearworkthesis.domain.device.DeviceSettingsDefaults
import com.example.nearworkthesis.domain.device.DeviceSettingsValidator
import com.example.nearworkthesis.domain.device.DeviceTimeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.howfarConfigStore by preferencesDataStore(name = "howfar_device_config")

class HowfarDeviceConfigRepository(
    private val appContext: Context,
    private val storageRepository: HowfarStorageRepository
) : DeviceConfigRepository {

    override fun observeConnectionState(): Flow<DeviceConnectionState> {
        return storageRepository.state.map { state ->
            when (state) {
                HowfarStorageState.Disconnected -> DeviceConnectionState.Disconnected
                is HowfarStorageState.Ready -> DeviceConnectionState.Connected
                is HowfarStorageState.Error -> DeviceConnectionState.Error(state.message)
            }
        }
    }

    override fun refreshConnection() {
        storageRepository.refresh()
    }

    // Mirrors howfar/cli/conf.py read path: if config UF2 exists, decode settings; else use defaults.
    override suspend fun readSettings(): Result<DeviceSettings> {
        return runCatching {
            val configBytes = storageRepository.readConfigUf2()
            val baseSettings = if (configBytes == null) {
                HowfarSettings.defaults()
            } else {
                val raw = HowfarUf2.convertFromUf2(configBytes)
                HowfarSettingsCodec.fromByteArray(raw)
            }
            mergeWithLocalSettings(baseSettings)
        }
    }

    // Mirrors howfar/cli/conf.py write path: merge UI settings into the device struct and write as UF2.
    // Direct-write path: same UF2 payload, but written to the selected device folder.
    override suspend fun writeSettings(settings: DeviceSettings): Result<Unit> {
        val errors = DeviceSettingsValidator.validate(settings)
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.values.joinToString(separator = "\n")))
        }

        return runCatching {
            persistLocalSettings(settings)
            val baseSettings = resolveBaseSettings()
            val howfarSettings = mapToHowfarSettings(settings, baseSettings)
            val settingsBytes = HowfarSettingsCodec.toByteArray(howfarSettings)
            val uf2Bytes = HowfarUf2.convertToUf2(settingsBytes)
            storageRepository.writeConfigUf2(uf2Bytes)
        }
    }

    override suspend fun clearDeviceData(settings: DeviceSettings): Result<Unit> {
        val errors = DeviceSettingsValidator.validate(settings)
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.values.joinToString(separator = "\n")))
        }

        return runCatching {
            persistLocalSettings(settings)
            val baseSettings = resolveBaseSettings()
            val howfarSettings = mapToHowfarSettings(settings, baseSettings)
            val eraseSettings = howfarSettings.copy(flagEraseDatabase = true)
            val settingsBytes = HowfarSettingsCodec.toByteArray(eraseSettings)
            val uf2Bytes = HowfarUf2.convertToUf2(settingsBytes)
            storageRepository.writeConfigUf2(uf2Bytes)
        }
    }


    // Export path: build optoconf.uf2 bytes without requiring the device to be mounted.
    override suspend fun buildConfigUf2(settings: DeviceSettings): Result<ByteArray> {
        val errors = DeviceSettingsValidator.validate(settings)
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException(errors.values.joinToString(separator = "\n")))
        }

        return runCatching {
            persistLocalSettings(settings)
            val baseSettings = resolveBaseSettings()
            val howfarSettings = mapToHowfarSettings(settings, baseSettings)
            val settingsBytes = HowfarSettingsCodec.toByteArray(howfarSettings)
            HowfarUf2.convertToUf2(settingsBytes)
        }
    }

    override suspend fun resetToDefaults(): Result<DeviceSettings> {
        val defaults = DeviceSettingsDefaults.defaults
        return writeSettings(defaults).map { defaults }
    }

    private suspend fun resolveBaseSettings(): HowfarSettings {
        val configBytes = runCatching { storageRepository.readConfigUf2() }.getOrNull() ?: return HowfarSettings.defaults()
        return runCatching {
            val raw = HowfarUf2.convertFromUf2(configBytes)
            HowfarSettingsCodec.fromByteArray(raw)
        }.getOrElse { HowfarSettings.defaults() }
    }

    private fun mapToHowfarSettings(settings: DeviceSettings, base: HowfarSettings): HowfarSettings {
        val timestamp = when (settings.deviceTimeMode) {
            DeviceTimeMode.USE_PHONE_TIME_UTC -> System.currentTimeMillis() / 1000L
            DeviceTimeMode.KEEP_DEVICE_TIME -> base.timestampSeconds
        }
        return base.copy(
            featureDcdcSleep = settings.enableLowPowerMode,
            measurementIntervalSeconds = settings.samplingIntervalSeconds.toLong(),
            timestampSeconds = timestamp
        )
    }

    private suspend fun mergeWithLocalSettings(howfarSettings: HowfarSettings): DeviceSettings {
        val local = readLocalSettings()
        return DeviceSettings(
            samplingIntervalSeconds = howfarSettings.measurementIntervalSeconds.toInt(),
            autoShutdownMinutes = local.autoShutdownMinutes,
            lowLightLuxThreshold = local.lowLightLuxThreshold,
            enableLowPowerMode = howfarSettings.featureDcdcSleep,
            deviceTimeMode = local.deviceTimeMode
        )
    }

    private suspend fun persistLocalSettings(settings: DeviceSettings) {
        appContext.howfarConfigStore.edit { prefs ->
            prefs[Keys.autoShutdownMinutes] = settings.autoShutdownMinutes
            prefs[Keys.lowLightLuxThreshold] = settings.lowLightLuxThreshold
            prefs[Keys.deviceTimeMode] = settings.deviceTimeMode.name
            prefs[Keys.enableLowPowerMode] = settings.enableLowPowerMode
        }
    }

    private suspend fun readLocalSettings(): LocalSettings {
        val defaults = DeviceSettingsDefaults.defaults
        val prefs = appContext.howfarConfigStore.data.first()

        val timeMode = prefs[Keys.deviceTimeMode]?.let { raw ->
            runCatching { DeviceTimeMode.valueOf(raw) }.getOrNull()
        } ?: defaults.deviceTimeMode

        return LocalSettings(
            autoShutdownMinutes = prefs[Keys.autoShutdownMinutes] ?: defaults.autoShutdownMinutes,
            lowLightLuxThreshold = prefs[Keys.lowLightLuxThreshold] ?: defaults.lowLightLuxThreshold,
            deviceTimeMode = timeMode,
            enableLowPowerMode = prefs[Keys.enableLowPowerMode] ?: defaults.enableLowPowerMode
        )
    }

    private data class LocalSettings(
        val autoShutdownMinutes: Int,
        val lowLightLuxThreshold: Int,
        val deviceTimeMode: DeviceTimeMode,
        val enableLowPowerMode: Boolean
    )

    private object Keys {
        val autoShutdownMinutes = intPreferencesKey("howfar_auto_shutdown_minutes")
        val lowLightLuxThreshold = intPreferencesKey("howfar_low_light_lux_threshold")
        val deviceTimeMode = stringPreferencesKey("howfar_device_time_mode")
        val enableLowPowerMode = booleanPreferencesKey("howfar_enable_low_power_mode")
    }
}







