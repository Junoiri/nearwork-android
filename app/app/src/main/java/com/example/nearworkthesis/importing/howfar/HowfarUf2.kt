/** UF2 wrapper and unwrapper for HowFar files; mirrors howfar/uf2.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

private const val UF2_MAGIC_START0: Int = 0x0A324655
private const val UF2_MAGIC_START1: Int = 0x9E5D5157.toInt()
private const val UF2_MAGIC_END: Int = 0x0AB16F30

private const val UF2_FLAG_NO_FLASH = 0x0001
private const val UF2_FLAG_FAMILY_ID_PRESENT = 0x2000

const val HOWFAR_FAMILY_ID: Int = 0xbabbba4e.toInt()
const val UF2_BLOCK_SIZE = 512
const val UF2_PAYLOAD_SIZE = 256

object HowfarUf2 {
    // Mirrors howfar/uf2.py: wrap raw flash bytes into UF2 blocks (512 bytes each, 256-byte payload).
    fun convertToUf2(rawBytes: ByteArray, familyId: Int = HOWFAR_FAMILY_ID): ByteArray {
        if (rawBytes.isEmpty()) return ByteArray(0)
        val numBlocks = ceil(rawBytes.size / UF2_PAYLOAD_SIZE.toDouble()).toInt()
        val output = ByteArray(numBlocks * UF2_BLOCK_SIZE)

        for (blockIndex in 0 until numBlocks) {
            val blockOffset = blockIndex * UF2_BLOCK_SIZE
            val payloadOffset = blockIndex * UF2_PAYLOAD_SIZE
            val payloadEnd = minOf(payloadOffset + UF2_PAYLOAD_SIZE, rawBytes.size)
            val payloadLength = payloadEnd - payloadOffset

            val block = ByteBuffer.wrap(output, blockOffset, UF2_BLOCK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            block.putInt(UF2_MAGIC_START0)
            block.putInt(UF2_MAGIC_START1)
            block.putInt(UF2_FLAG_FAMILY_ID_PRESENT)
            block.putInt(payloadOffset)
            block.putInt(UF2_PAYLOAD_SIZE)
            block.putInt(blockIndex)
            block.putInt(numBlocks)
            block.putInt(familyId)

            val payloadStartPos = block.position()
            if (payloadLength > 0) {
                block.put(rawBytes, payloadOffset, payloadLength)
            }

            val remaining = UF2_PAYLOAD_SIZE - payloadLength
            if (remaining > 0) {
                block.put(ByteArray(remaining) { 0xFF.toByte() })
            }

            val padding = block.limit() - block.position() - 4
            if (padding > 0) {
                block.put(ByteArray(padding))
            }
            block.putInt(UF2_MAGIC_END)
        }

        return output
    }

    // Mirrors howfar/uf2.py: unwrap UF2 blocks back into raw flash bytes and validate magic/flags.
    fun convertFromUf2(uf2Bytes: ByteArray, familyId: Int = HOWFAR_FAMILY_ID): ByteArray {
        require(uf2Bytes.size % UF2_BLOCK_SIZE == 0) { "UF2 size must be a multiple of $UF2_BLOCK_SIZE bytes." }
        if (uf2Bytes.isEmpty()) return ByteArray(0)

        var maxAddressPlus = 0
        val blocks = ArrayList<Uf2Block>(uf2Bytes.size / UF2_BLOCK_SIZE)

        var offset = 0
        while (offset < uf2Bytes.size) {
            val blockBuf = ByteBuffer.wrap(uf2Bytes, offset, UF2_BLOCK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val magic0 = blockBuf.int
            val magic1 = blockBuf.int
            if (magic0 != UF2_MAGIC_START0 || magic1 != UF2_MAGIC_START1) {
                throw IllegalArgumentException("Invalid UF2 magic at block offset $offset")
            }
            val flags = blockBuf.int
            val targetAddr = blockBuf.int
            val payloadSize = blockBuf.int
            blockBuf.int // blockNo
            blockBuf.int // numBlocks
            val blockFamilyId = blockBuf.int

            if (flags and UF2_FLAG_NO_FLASH != 0) {
                offset += UF2_BLOCK_SIZE
                continue
            }
            if (flags and UF2_FLAG_FAMILY_ID_PRESENT != 0 && familyId != 0 && blockFamilyId != familyId) {
                throw IllegalArgumentException("UF2 family ID mismatch: expected 0x${familyId.toUInt().toString(16)}")
            }
            if (payloadSize <= 0 || payloadSize > UF2_PAYLOAD_SIZE) {
                throw IllegalArgumentException("Unsupported UF2 payload size $payloadSize")
            }

            val payload = ByteArray(payloadSize)
            blockBuf.get(payload)

            val blockEndMagic = ByteBuffer.wrap(uf2Bytes, offset + UF2_BLOCK_SIZE - 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            if (blockEndMagic != UF2_MAGIC_END) {
                throw IllegalArgumentException("Invalid UF2 end magic at block offset $offset")
            }

            val endAddr = targetAddr + payloadSize
            if (endAddr > maxAddressPlus) maxAddressPlus = endAddr
            blocks.add(Uf2Block(targetAddr, payload))

            offset += UF2_BLOCK_SIZE
        }

        val output = ByteArray(maxAddressPlus) { 0xFF.toByte() }
        for (block in blocks) {
            if (block.address + block.payload.size <= output.size) {
                System.arraycopy(block.payload, 0, output, block.address, block.payload.size)
            }
        }
        return output
    }
}

private data class Uf2Block(
    val address: Int,
    val payload: ByteArray
)




