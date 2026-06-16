package com.example.nearworkthesis.domain.device

sealed interface DeviceConfigResult {
    data class Success(val message: String) : DeviceConfigResult
    data class Error(val message: String) : DeviceConfigResult
}

