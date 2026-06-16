package com.example.nearworkthesis.domain.device

import kotlinx.coroutines.flow.Flow

interface DeviceConfigRepository {
    fun observeConnectionState(): Flow<DeviceConnectionState>
    fun refreshConnection()

    suspend fun readSettings(): Result<DeviceSettings>
    suspend fun writeSettings(settings: DeviceSettings): Result<Unit>
    suspend fun clearDeviceData(settings: DeviceSettings): Result<Unit>
    suspend fun buildConfigUf2(settings: DeviceSettings): Result<ByteArray>
    suspend fun resetToDefaults(): Result<DeviceSettings>
}



