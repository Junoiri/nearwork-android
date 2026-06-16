/** Reads the RingFS layout inside HowFar flash; mirrors howfar/ringfs.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder

object RingFs {
    // Mirrors howfar/ringfs.py: scan RingFS sectors/pages and extract valid records.
    fun readRecords(rawFlash: ByteArray, recordSize: Int): List<ByteArray> {
        require(recordSize > 0) { "recordSize must be positive" }
        val records = ArrayList<ByteArray>()
        var sectorOffset = 0
        while (sectorOffset + HowFarDeviceConstants.SECTOR_HEADER_SIZE <= rawFlash.size) {
            val header = readSectorHeader(rawFlash, sectorOffset)
            if (header.status == HowFarDeviceConstants.SECTOR_IN_USE || header.status == HowFarDeviceConstants.SECTOR_FREE) {
                for (pageIndex in 1 until HowFarDeviceConstants.PAGES_PER_SECTOR) {
                    val pageOffset = sectorOffset + (pageIndex * HowFarDeviceConstants.PAGE_SIZE)
                    if (pageOffset + HowFarDeviceConstants.SLOT_HEADER_SIZE > rawFlash.size) continue
                    val slotStatus = readSlotStatus(rawFlash, pageOffset)
                    if (slotStatus != HowFarDeviceConstants.SLOT_VALID) continue
                    val dataOffset = pageOffset + HowFarDeviceConstants.SLOT_HEADER_SIZE
                    val dataLength = HowFarDeviceConstants.PAGE_SIZE - HowFarDeviceConstants.SLOT_HEADER_SIZE
                    val recordCount = dataLength / recordSize
                    for (recordIndex in 0 until recordCount) {
                        val recordOffset = dataOffset + (recordIndex * recordSize)
                        if (recordOffset + recordSize > rawFlash.size) break
                        val record = rawFlash.copyOfRange(recordOffset, recordOffset + recordSize)
                        if (record.all { it == 0xFF.toByte() }) continue
                        records.add(record)
                    }
                }
            }
            sectorOffset += HowFarDeviceConstants.SECTOR_SIZE
        }
        return records
    }

    // Mirrors howfar/ringfs.py: version lives in sector header; mismatches are treated as errors.
    fun readVersion(rawFlash: ByteArray): Int? {
        var sectorOffset = 0
        var version: Int? = null
        var versionMismatch = false
        while (sectorOffset + HowFarDeviceConstants.SECTOR_HEADER_SIZE <= rawFlash.size) {
            val header = readSectorHeader(rawFlash, sectorOffset)
            if (header.status == HowFarDeviceConstants.SECTOR_IN_USE || header.status == HowFarDeviceConstants.SECTOR_FREE) {
                if (version == null) {
                    version = header.version
                } else if (version != header.version) {
                    versionMismatch = true
                }
            }
            sectorOffset += HowFarDeviceConstants.SECTOR_SIZE
        }
        if (versionMismatch) {
            throw IllegalArgumentException("RingFS sector version mismatch detected.")
        }
        return version
    }

    private fun readSectorHeader(bytes: ByteArray, offset: Int): RingFSSectorHeader {
        val buffer = ByteBuffer.wrap(bytes, offset, HowFarDeviceConstants.SECTOR_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        return RingFSSectorHeader(status = buffer.int, version = buffer.int)
    }

    private fun readSlotStatus(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, HowFarDeviceConstants.SLOT_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).int
    }
}

data class RingFSSectorHeader(
    val status: Int,
    val version: Int
)





