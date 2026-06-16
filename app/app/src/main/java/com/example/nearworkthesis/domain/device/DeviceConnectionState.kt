package com.example.nearworkthesis.domain.device

sealed interface DeviceConnectionState {
    data object Disconnected : DeviceConnectionState
    data object PermissionRequired : DeviceConnectionState
    data object Connected : DeviceConnectionState
    data object Connecting : DeviceConnectionState
    data class Error(val message: String) : DeviceConnectionState
}

