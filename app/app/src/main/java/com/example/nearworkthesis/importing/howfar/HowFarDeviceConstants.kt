package com.example.nearworkthesis.importing.howfar

object HowFarDeviceConstants {
    const val DEFAULT_SAMPLING_INTERVAL_SECONDS = 5

    const val PAGE_SIZE = 256
    const val SECTOR_SIZE = 4096
    const val SECTOR_HEADER_SIZE = 8
    const val SLOT_HEADER_SIZE = 4
    const val PAGES_PER_SECTOR = SECTOR_SIZE / PAGE_SIZE

    const val SECTOR_ERASED = 0xFFFFFFFF.toInt()
    const val SECTOR_FREE = 0xFFFFFF00.toInt()
    const val SECTOR_IN_USE = 0xFFFF0000.toInt()
    const val SECTOR_ERASING = 0xFF000000.toInt()
    const val SECTOR_FORMATTING = 0x00000000

    const val SLOT_ERASED = 0xFFFFFFFF.toInt()
    const val SLOT_RESERVED = 0xFFFFFF00.toInt()
    const val SLOT_VALID = 0xFFFF0000.toInt()
    const val SLOT_GARBAGE = 0xFF000000.toInt()
}
