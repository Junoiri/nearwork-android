package com.example.nearworkthesis.domain.device

enum class DeviceSettingsField {
    SamplingIntervalSeconds,
    AutoShutdownMinutes,
    LowLightLuxThreshold
}

object DeviceSettingsValidator {
    const val samplingIntervalMin = 1
    const val samplingIntervalMax = 60

    const val autoShutdownMin = 1
    const val autoShutdownMax = 240

    const val lowLightLuxMin = 0
    const val lowLightLuxMax = 2000

    fun validate(settings: DeviceSettings): Map<DeviceSettingsField, String> {
        val errors = LinkedHashMap<DeviceSettingsField, String>()

        if (settings.samplingIntervalSeconds !in samplingIntervalMin..samplingIntervalMax) {
            errors[DeviceSettingsField.SamplingIntervalSeconds] =
                "Sampling interval must be $samplingIntervalMin–$samplingIntervalMax seconds."
        }
        if (settings.autoShutdownMinutes !in autoShutdownMin..autoShutdownMax) {
            errors[DeviceSettingsField.AutoShutdownMinutes] =
                "Auto shutdown must be $autoShutdownMin–$autoShutdownMax minutes."
        }
        if (settings.lowLightLuxThreshold !in lowLightLuxMin..lowLightLuxMax) {
            errors[DeviceSettingsField.LowLightLuxThreshold] =
                "Low-light lux threshold must be $lowLightLuxMin–$lowLightLuxMax lux."
        }

        return errors
    }
}

