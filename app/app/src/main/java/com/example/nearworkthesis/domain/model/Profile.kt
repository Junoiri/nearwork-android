package com.example.nearworkthesis.domain.model

data class Profile(
    val id: Long = 0,
    val name: String,
    val createdAtEpochMillis: Long,
    val timezoneId: String,
    val dateOfBirth: String?
)

