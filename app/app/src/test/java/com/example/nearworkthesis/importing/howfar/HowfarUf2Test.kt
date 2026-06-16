package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HowfarUf2Test {

    @Test
    fun convertToUf2_emptyInput_returnsEmptyOutput() {
        assertEquals(0, HowfarUf2.convertToUf2(ByteArray(0)).size)
    }

    @Test
    fun convertToUf2_roundTripsPayload_andPadsTrailingBytesWithFF() {
        val raw = ByteArray(300) { index -> (index and 0xFF).toByte() }

        val uf2 = HowfarUf2.convertToUf2(raw)
        val restored = HowfarUf2.convertFromUf2(uf2)

        assertEquals(UF2_BLOCK_SIZE * 2, uf2.size)
        assertArrayEquals(raw, restored.copyOfRange(0, raw.size))
        assertTrue(restored.drop(raw.size).all { it == 0xFF.toByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun convertFromUf2_rejectsByteArraysWhoseSizeIsNotABlockMultiple() {
        HowfarUf2.convertFromUf2(ByteArray(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun convertFromUf2_rejectsFamilyIdMismatch() {
        val uf2 = HowfarUf2.convertToUf2(byteArrayOf(1, 2, 3), familyId = HOWFAR_FAMILY_ID)

        HowfarUf2.convertFromUf2(uf2, familyId = 0x12345678.toInt())
    }

    @Test
    fun convertFromUf2_skipsBlocksMarkedAsNoFlash() {
        val raw = ByteArray(300) { index -> (index + 1).toByte() }
        val uf2 = HowfarUf2.convertToUf2(raw).copyOf()

        val firstBlock = ByteBuffer.wrap(uf2, 0, UF2_BLOCK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        firstBlock.position(8)
        firstBlock.putInt(0x2001)

        val restored = HowfarUf2.convertFromUf2(uf2)

        assertTrue(restored.take(UF2_PAYLOAD_SIZE).all { it == 0xFF.toByte() })
        assertArrayEquals(
            raw.copyOfRange(UF2_PAYLOAD_SIZE, raw.size),
            restored.copyOfRange(UF2_PAYLOAD_SIZE, raw.size)
        )
    }
}
