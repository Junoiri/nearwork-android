package com.example.nearworkthesis.domain

enum class DuplicateResolutionPolicy(
    val storageValue: String,
    val manifestValue: String,
    val displayLabel: String
) {
    KEEP_EXISTING(
        storageValue = "keep_existing",
        manifestValue = "keep_existing",
        displayLabel = "Keep existing"
    ),
    REPLACE_WITH_NEW(
        storageValue = "replace_with_new",
        manifestValue = "replace_with_new",
        displayLabel = "Replace with new"
    );

    companion object {
        fun fromStorage(value: String?): DuplicateResolutionPolicy {
            if (value.isNullOrBlank()) return KEEP_EXISTING
            return entries.firstOrNull { it.storageValue == value } ?: KEEP_EXISTING
        }
    }
}
