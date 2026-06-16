package com.example.nearworkthesis.core.error

sealed class AppError {
    abstract val reason: String

    data class ImportFailed(override val reason: String) : AppError()
    data class ExportFailed(override val reason: String) : AppError()
    data class DatabaseError(override val reason: String) : AppError()
    data class ParseFailed(override val reason: String) : AppError()
    data class Unknown(override val reason: String) : AppError()
}
