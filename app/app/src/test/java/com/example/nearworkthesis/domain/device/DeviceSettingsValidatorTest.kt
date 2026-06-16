package com.example.nearworkthesis.domain.device

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSettingsValidatorTest {

    @Test
    fun samplingIntervalOutOfRange_isInvalid() {
        val invalid = DeviceSettingsDefaults.defaults.copy(samplingIntervalSeconds = 0)
        val errors = DeviceSettingsValidator.validate(invalid)
        assertTrue(DeviceSettingsField.SamplingIntervalSeconds in errors.keys)
    }

    @Test
    fun allOutOfRangeFields_areReported_andValidSettingsPass() {
        val invalid = DeviceSettingsDefaults.defaults.copy(
            samplingIntervalSeconds = DeviceSettingsValidator.samplingIntervalMax + 1,
            autoShutdownMinutes = DeviceSettingsValidator.autoShutdownMin - 1,
            lowLightLuxThreshold = DeviceSettingsValidator.lowLightLuxMax + 1
        )
        val errors = DeviceSettingsValidator.validate(invalid)

        assertEquals(
            setOf(
                DeviceSettingsField.SamplingIntervalSeconds,
                DeviceSettingsField.AutoShutdownMinutes,
                DeviceSettingsField.LowLightLuxThreshold
            ),
            errors.keys
        )
        assertTrue(DeviceSettingsValidator.validate(DeviceSettingsDefaults.defaults).isEmpty())
    }

    @Test
    fun defaultsAndTimeModes_areStable() {
        assertTrue(DeviceSettingsDefaults.defaults.enableLowPowerMode)
        assertEquals(DeviceTimeMode.USE_PHONE_TIME_UTC, DeviceSettingsDefaults.defaults.deviceTimeMode)
        assertEquals(
            listOf(DeviceTimeMode.USE_PHONE_TIME_UTC, DeviceTimeMode.KEEP_DEVICE_TIME),
            DeviceTimeMode.entries
        )
    }
}

