package com.example.nearworkthesis.domain.device

data class DeviceSettings(
    val samplingIntervalSeconds: Int,
    val autoShutdownMinutes: Int,
    val lowLightLuxThreshold: Int,
    val enableLowPowerMode: Boolean,
    val deviceTimeMode: DeviceTimeMode
)

