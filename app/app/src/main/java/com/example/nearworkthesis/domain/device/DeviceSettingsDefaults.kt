package com.example.nearworkthesis.domain.device

import com.example.nearworkthesis.importing.howfar.HowFarDeviceConstants

object DeviceSettingsDefaults {
    val defaults: DeviceSettings =
        DeviceSettings(
            samplingIntervalSeconds = HowFarDeviceConstants.DEFAULT_SAMPLING_INTERVAL_SECONDS,
            autoShutdownMinutes = 30,
            lowLightLuxThreshold = 150,
            enableLowPowerMode = true,
            deviceTimeMode = DeviceTimeMode.USE_PHONE_TIME_UTC
        )
}
