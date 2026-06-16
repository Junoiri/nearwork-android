/** Decodes measurement records from raw bytes including lux conversion; mirrors howfar/database.py and howfar/dataconv.py from the HowFar-python library. */
package com.example.nearworkthesis.importing.howfar

private const val RECORD_VERSION_2024091501 = 2024091501
private const val ALS_CALIBRATION_SLOPE = 1.004
private const val ALS_CALIBRATION_INTERCEPT = -0.09

object HowfarDatabase {
    // Mirrors howfar/database.py: map sector header version -> record codec.
    private val codecs: Map<Int, HowfarRecordCodec> = mapOf(
        RECORD_VERSION_2024091501 to HowfarRecord2024091501
    )

    val latestVersion: Int = RECORD_VERSION_2024091501

    fun codecFor(version: Int): HowfarRecordCodec {
        return codecs[version] ?: error("Unsupported HowFar record version: $version")
    }

    fun latestRecordSize(): Int {
        return codecFor(latestVersion).recordSize
    }
}

interface HowfarRecordCodec {
    val version: Int
    val recordSize: Int
    fun decode(bytes: ByteArray): HowfarRecord
}

data class HowfarRecord(
    val timestampSeconds: Long,
    val alsCode: Int,
    val tofDistanceMm: Int
) {
    fun toMeasurement(): HowfarMeasurement {
        // I calibrate the decoded ALS lux here so every downstream consumer sees the thesis-adjusted value.
        val lux = calibrateAlsLux(opt3004CodeToLux(alsCode))
        val distanceCm = tofDistanceMm / 10.0
        return HowfarMeasurement(
            timestampEpochMillis = timestampSeconds * 1000L,
            lux = lux,
            distanceCm = distanceCm
        )
    }

    fun formatLocalDateTime(): String {
        return java.time.Instant.ofEpochSecond(timestampSeconds)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
            .toString()
    }
}

// Mirrors howfar/database.py: record layout for version 2024091501 (<IHH).
object HowfarRecord2024091501 : HowfarRecordCodec {
    override val version: Int = RECORD_VERSION_2024091501
    override val recordSize: Int = 8

    override fun decode(bytes: ByteArray): HowfarRecord {
        require(bytes.size >= recordSize) { "Record payload too small." }
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val timestamp = buffer.int.toLong() and 0xFFFFFFFFL
        val alsCode = buffer.short.toInt() and 0xFFFF
        val tofDistance = buffer.short.toInt() and 0xFFFF
        return HowfarRecord(
            timestampSeconds = timestamp,
            alsCode = alsCode,
            tofDistanceMm = tofDistance
        )
    }
}

data class HowfarMeasurement(
    val timestampEpochMillis: Long,
    val distanceCm: Double,
    val lux: Double
)

// Mirrors howfar/dataconv.py: OPT3004 code -> lux conversion.
fun opt3004CodeToLux(code: Int): Double {
    val mantissa = code and 0x0FFF
    val exponent = (code ushr 12) and 0x000F
    return (mantissa shl exponent) * 0.01
}

fun calibrateAlsLux(luxHowfar: Double): Double {
    // I keep the regression constants named because this is the thesis calibration, not a magic tweak.
    return ((ALS_CALIBRATION_SLOPE * luxHowfar) + ALS_CALIBRATION_INTERCEPT).coerceAtLeast(0.0)
}




