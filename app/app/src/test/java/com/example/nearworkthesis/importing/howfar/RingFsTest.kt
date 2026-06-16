package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RingFsTest {

    @Test
    fun readRecords_requiresPositiveRecordSize() {
        try {
            RingFs.readRecords(ByteArray(HowFarDeviceConstants.SECTOR_SIZE), 0)
            fail("Expected recordSize validation to reject non-positive sizes.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("recordSize must be positive", expected.message)
        }
    }

    @Test
    fun readRecords_returnsOnlyNonErasedValidRecords() {
        val flash = blankFlash(1)
        writeSectorHeader(
            flash = flash,
            sectorIndex = 0,
            status = HowFarDeviceConstants.SECTOR_IN_USE,
            version = HowfarDatabase.latestVersion
        )
        writeSlotStatus(flash = flash, sectorIndex = 0, pageIndex = 1, status = HowFarDeviceConstants.SLOT_VALID)
        val expectedRecord = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        writeRecord(flash = flash, sectorIndex = 0, pageIndex = 1, recordIndex = 0, record = expectedRecord)
        writeRecord(
            flash = flash,
            sectorIndex = 0,
            pageIndex = 1,
            recordIndex = 1,
            record = ByteArray(expectedRecord.size) { 0xFF.toByte() }
        )

        val records = RingFs.readRecords(flash, expectedRecord.size)

        assertEquals(1, records.size)
        assertArrayEquals(expectedRecord, records.single())
    }

    @Test
    fun readVersion_returnsNullWhenNoValidSectorHeadersExist() {
        val flash = blankFlash(1)

        val version = RingFs.readVersion(flash)

        assertNull(version)
    }

    @Test
    fun readVersion_returnsVersionWhenSectorsAgree() {
        val flash = blankFlash(2)
        writeSectorHeader(
            flash = flash,
            sectorIndex = 0,
            status = HowFarDeviceConstants.SECTOR_IN_USE,
            version = HowfarDatabase.latestVersion
        )
        writeSectorHeader(
            flash = flash,
            sectorIndex = 1,
            status = HowFarDeviceConstants.SECTOR_FREE,
            version = HowfarDatabase.latestVersion
        )

        val version = RingFs.readVersion(flash)

        assertEquals(HowfarDatabase.latestVersion, version)
    }

    @Test
    fun readVersion_throwsWhenSectorsDisagreeOnVersion() {
        val flash = blankFlash(2)
        writeSectorHeader(
            flash = flash,
            sectorIndex = 0,
            status = HowFarDeviceConstants.SECTOR_IN_USE,
            version = HowfarDatabase.latestVersion
        )
        writeSectorHeader(
            flash = flash,
            sectorIndex = 1,
            status = HowFarDeviceConstants.SECTOR_FREE,
            version = HowfarDatabase.latestVersion + 1
        )

        try {
            RingFs.readVersion(flash)
            fail("Expected version mismatch to fail.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("RingFS sector version mismatch detected.", expected.message)
        }
    }

    private fun blankFlash(sectorCount: Int): ByteArray {
        return ByteArray(sectorCount * HowFarDeviceConstants.SECTOR_SIZE) { 0xFF.toByte() }
    }

    private fun writeSectorHeader(
        flash: ByteArray,
        sectorIndex: Int,
        status: Int,
        version: Int
    ) {
        val offset = sectorIndex * HowFarDeviceConstants.SECTOR_SIZE
        ByteBuffer.wrap(flash, offset, HowFarDeviceConstants.SECTOR_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(status)
            .putInt(version)
    }

    private fun writeSlotStatus(
        flash: ByteArray,
        sectorIndex: Int,
        pageIndex: Int,
        status: Int
    ) {
        val offset = sectorIndex * HowFarDeviceConstants.SECTOR_SIZE +
            pageIndex * HowFarDeviceConstants.PAGE_SIZE
        ByteBuffer.wrap(flash, offset, HowFarDeviceConstants.SLOT_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(status)
    }

    private fun writeRecord(
        flash: ByteArray,
        sectorIndex: Int,
        pageIndex: Int,
        recordIndex: Int,
        record: ByteArray
    ) {
        val offset = sectorIndex * HowFarDeviceConstants.SECTOR_SIZE +
            pageIndex * HowFarDeviceConstants.PAGE_SIZE +
            HowFarDeviceConstants.SLOT_HEADER_SIZE +
            recordIndex * record.size
        System.arraycopy(record, 0, flash, offset, record.size)
    }
}
