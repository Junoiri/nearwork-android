/** Settings struct and codec for HowFar; mirrors howfar/settings.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import kotlin.math.min

private const val EXAM_ID_LENGTH = 8

// Mirrors howfar/settings.py structure

data class HowfarSettings(
    val settingsVersion: Long,
    val featureAls: Boolean,
    val featureTof: Boolean,
    val featureDcdcSleep: Boolean,
    val featureStorage: Boolean,
    val flagEraseDatabase: Boolean,
    val timestampSeconds: Long,
    val tofDistanceMode: Long,
    val tofTimingBudget: Long,
    val measurementIntervalSeconds: Long,
    val examinationIdentifier: String
) {
    companion object {
        fun defaults(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): HowfarSettings {
            return HowfarSettings(
                settingsVersion = 2024091701,
                featureAls = true,
                featureTof = true,
                featureDcdcSleep = false,
                featureStorage = true,
                flagEraseDatabase = false,
                timestampSeconds = nowEpochSeconds,
                tofDistanceMode = 1,
                tofTimingBudget = 20,
                measurementIntervalSeconds = 10,
                examinationIdentifier = "OPTODATA"
            )
        }
    }
}

object HowfarSettingsCodec {
    private val ascii: Charset = Charsets.US_ASCII
    private const val STRUCT_SIZE = 4 + 2 + 2 + 4 + 4 + 4 + 4 + EXAM_ID_LENGTH

    // Mirrors howfar/settings.py: pack the settings struct into little-endian bytes.
    fun toByteArray(settings: HowfarSettings): ByteArray {
        val buffer = ByteBuffer.allocate(STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(settings.settingsVersion.toInt())
        buffer.putShort(packFeatureFlags(settings).toShort())
        buffer.putShort(packEraseFlags(settings).toShort())
        buffer.putInt(settings.timestampSeconds.toInt())
        buffer.putInt(settings.tofDistanceMode.toInt())
        buffer.putInt(settings.tofTimingBudget.toInt())
        buffer.putInt(settings.measurementIntervalSeconds.toInt())
        buffer.put(encodeExamId(settings.examinationIdentifier))
        return buffer.array()
    }

    // Mirrors howfar/settings.py: unpack bytes into the settings struct.
    fun fromByteArray(bytes: ByteArray): HowfarSettings {
        require(bytes.size >= STRUCT_SIZE) { "Settings payload too small." }
        val buffer = ByteBuffer.wrap(bytes, 0, STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val settingsVersion = buffer.int.toLong() and 0xFFFFFFFFL
        val featureFlags = buffer.short.toInt() and 0xFFFF
        val eraseFlags = buffer.short.toInt() and 0xFFFF
        val timestampSeconds = buffer.int.toLong() and 0xFFFFFFFFL
        val tofDistanceMode = buffer.int.toLong() and 0xFFFFFFFFL
        val tofTimingBudget = buffer.int.toLong() and 0xFFFFFFFFL
        val measurementIntervalSeconds = buffer.int.toLong() and 0xFFFFFFFFL
        val examIdBytes = ByteArray(EXAM_ID_LENGTH)
        buffer.get(examIdBytes)

        return HowfarSettings(
            settingsVersion = settingsVersion,
            featureAls = featureFlags and 0x1 != 0,
            featureTof = featureFlags and 0x2 != 0,
            featureDcdcSleep = featureFlags and 0x4 != 0,
            featureStorage = featureFlags and 0x8 != 0,
            flagEraseDatabase = eraseFlags and 0x1 != 0,
            timestampSeconds = timestampSeconds,
            tofDistanceMode = tofDistanceMode,
            tofTimingBudget = tofTimingBudget,
            measurementIntervalSeconds = measurementIntervalSeconds,
            examinationIdentifier = decodeExamId(examIdBytes)
        )
    }

    private fun packFeatureFlags(settings: HowfarSettings): Int {
        var flags = 0
        if (settings.featureAls) flags = flags or 0x1
        if (settings.featureTof) flags = flags or 0x2
        if (settings.featureDcdcSleep) flags = flags or 0x4
        if (settings.featureStorage) flags = flags or 0x8
        return flags
    }

    private fun packEraseFlags(settings: HowfarSettings): Int {
        return if (settings.flagEraseDatabase) 0x1 else 0
    }

    private fun encodeExamId(value: String): ByteArray {
        val trimmed = value.take(EXAM_ID_LENGTH)
        val raw = trimmed.toByteArray(ascii)
        val out = ByteArray(EXAM_ID_LENGTH) { ' '.code.toByte() }
        val len = min(raw.size, EXAM_ID_LENGTH)
        if (len > 0) {
            System.arraycopy(raw, 0, out, 0, len)
        }
        return out
    }

    private fun decodeExamId(bytes: ByteArray): String {
        return bytes.toString(ascii).trimEnd(' ')
    }
}






