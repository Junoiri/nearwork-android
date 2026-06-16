package com.example.nearworkthesis.domain.analysis

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatlabPreprocessingDropInTest {

    @Test
    fun preprocessCsv_writesProcessedCsvAndPlot() {
        val inputFile = resolveInputFile()
        assertTrue(
            "Input CSV missing. Place nearwork_raw_2000-01-02.csv in app/test-inputs/preprocessing/",
            inputFile.exists()
        )

        val rows = parseRows(inputFile)
        val pipeline = PreprocessingPipeline()

        val output = preprocessWholeCsv(rows, pipeline)

        val outputDir = Paths.get("build", "test-outputs", "preprocessing")
        Files.createDirectories(outputDir)
        val csvOut = outputDir.resolve("nearwork_raw_2000-01-02_processed.csv").toFile()
        val distancePngOut = outputDir.resolve("nearwork_raw_2000-01-02_plot_distance.png").toFile()
        val illumNoSmoothPngOut = outputDir.resolve("nearwork_raw_2000-01-02_plot_illumination_no_smoothing.png").toFile()
        val illumSmoothPngOut = outputDir.resolve("nearwork_raw_2000-01-02_plot_illumination_rolling_mean.png").toFile()

        writeProcessedCsv(csvOut, output)
        writeTimelinePlotPng(
            file = distancePngOut,
            title = "Nearwork timeline",
            yAxisLabel = "Distance (cm)",
            tSeconds = output.tSeconds,
            series = listOf(
                PlotSeries("Raw", output.sInterpDistanceCm, Color.rgb(33, 113, 181)),
                PlotSeries("Smoothed", output.sFilterDistanceCm, Color.rgb(203, 24, 29))
            )
        )
        writeTimelinePlotPng(
            file = illumNoSmoothPngOut,
            title = "Illumination timeline (no smoothing)",
            yAxisLabel = "Illumination [lux]",
            tSeconds = output.tSeconds,
            series = listOf(
                PlotSeries("Raw", output.sInterpIlluminationLux, Color.rgb(33, 113, 181))
            )
        )
        writeTimelinePlotPng(
            file = illumSmoothPngOut,
            title = "Illumination timeline (rolling mean)",
            yAxisLabel = "Illumination [lux]",
            tSeconds = output.tSeconds,
            series = listOf(
                PlotSeries("Raw", output.sInterpIlluminationLux, Color.rgb(33, 113, 181)),
                PlotSeries("Smoothed", output.sFilterIlluminationLux, Color.rgb(203, 24, 29))
            )
        )

        assertTrue(csvOut.exists() && csvOut.length() > 0)
        assertTrue(distancePngOut.exists() && distancePngOut.length() > 0)
        assertTrue(illumNoSmoothPngOut.exists() && illumNoSmoothPngOut.length() > 0)
        assertTrue(illumSmoothPngOut.exists() && illumSmoothPngOut.length() > 0)
    }
}

private data class InputRow(
    val timeToken: String,
    val distanceCm: Double,
    val lux: Double,
    val sessionId: String
)

private data class OutputSeries(
    val tSeconds: List<Int>,
    val sInterpDistanceCm: List<Double>,
    val sFilterDistanceCm: List<Double>,
    val sInterpIlluminationLux: List<Double>,
    val sFilterIlluminationLux: List<Double>
)

private data class PlotSeries(
    val name: String,
    val values: List<Double>,
    val color: Int
)

private fun resolveInputFile(): File {
    val candidates = listOf(
        Paths.get("app", "test-inputs", "preprocessing", "nearwork_raw_2000-01-02.csv"),
        Paths.get("test-inputs", "preprocessing", "nearwork_raw_2000-01-02.csv")
    )
    return candidates.asSequence().map(Path::toFile).firstOrNull { it.exists() } ?: candidates.first().toFile()
}

private fun parseRows(file: File): List<InputRow> {
    val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "Input CSV is empty: ${file.absolutePath}" }

    val header = parseCsvLine(lines.first())
    val timeIdxByName = header.indexOfFirst { it.equals("timestamp", ignoreCase = true) || it.equals("time", ignoreCase = true) }
    val distanceIdxByName = header.indexOfFirst {
        it.equals("distance_cm", ignoreCase = true) ||
            it.equals("distance", ignoreCase = true) ||
            it.equals("distance [cm]", ignoreCase = true)
    }
    val luxIdxByName = header.indexOfFirst {
        it.equals("illumination_lux", ignoreCase = true) ||
            it.equals("lux", ignoreCase = true)
    }
    val sessionIdxByName = header.indexOfFirst { it.equals("session_id", ignoreCase = true) }
    val hasHeaderNames = timeIdxByName >= 0 || distanceIdxByName >= 0

    val startIndex = if (hasHeaderNames) 1 else 0
    val timeIdx = if (timeIdxByName >= 0) timeIdxByName else 0
    val distanceIdx = if (distanceIdxByName >= 0) distanceIdxByName else 4
    val luxIdx = if (luxIdxByName >= 0) luxIdxByName else 5
    val sessionIdx = if (sessionIdxByName >= 0) sessionIdxByName else -1

    val rows = ArrayList<InputRow>(lines.size - startIndex)
    for (i in startIndex until lines.size) {
        val cols = parseCsvLine(lines[i])
        val timeToken = cols.getOrNull(timeIdx)?.trim().orEmpty()
        val distance = cols.getOrNull(distanceIdx)?.trim()?.toDoubleOrNull()
        val lux = cols.getOrNull(luxIdx)?.trim()?.toDoubleOrNull()
        val sessionId = if (sessionIdx >= 0) cols.getOrNull(sessionIdx)?.trim().orEmpty().ifBlank { "default" } else "default"
        if (timeToken.isBlank() || distance == null || !distance.isFinite() || lux == null || !lux.isFinite()) continue
        rows.add(InputRow(timeToken = timeToken, distanceCm = distance, lux = lux, sessionId = sessionId))
    }
    require(rows.isNotEmpty()) { "No valid rows in input: ${file.absolutePath}" }
    return rows
}

private fun preprocessWholeCsv(
    rows: List<InputRow>,
    pipeline: PreprocessingPipeline
): OutputSeries {
    val epochByRow = rows.map { parseEpochMillisOrNull(it.timeToken) }
    val hasEpochForAll = epochByRow.all { it != null }
    // Temporarily run preprocessing on the whole CSV as one timeline.
    // Per-session split is intentionally disabled for this run.
    return if (hasEpochForAll) {
        val allSamples = rows.indices.map { idx ->
            NearworkSample(
                timestampMillis = epochByRow[idx]!!,
                distanceCm = rows[idx].distanceCm,
                lux = rows[idx].lux
            )
        }
        val processed = pipeline.process(allSamples)
        OutputSeries(
            tSeconds = processed.tSeconds,
            sInterpDistanceCm = processed.sInterpDistanceCm,
            sFilterDistanceCm = processed.sFilterDistanceCm,
            sInterpIlluminationLux = processed.sInterpIlluminationLux,
            sFilterIlluminationLux = processed.sFilterIlluminationLux
        )
    } else {
        val processed = pipeline.processTimeOfDay(
            Time_stamp = rows.map { it.timeToken },
            Distance = rows.map { it.distanceCm },
            Lux = rows.map { it.lux }
        )
        OutputSeries(
            tSeconds = processed.tSeconds,
            sInterpDistanceCm = processed.sInterpDistanceCm,
            sFilterDistanceCm = processed.sFilterDistanceCm,
            sInterpIlluminationLux = processed.sInterpIlluminationLux,
            sFilterIlluminationLux = processed.sFilterIlluminationLux
        )
    }
}

private data class OutputPoint(
    val sInterpCm: Double,
    val sFilterCm: Double,
    val sInterpLux: Double,
    val sFilterLux: Double
)

private fun buildOutputSeriesFromSparse(points: Map<Int, OutputPoint>): OutputSeries {
    if (points.isEmpty()) {
        return OutputSeries(
            tSeconds = emptyList(),
            sInterpDistanceCm = emptyList(),
            sFilterDistanceCm = emptyList(),
            sInterpIlluminationLux = emptyList(),
            sFilterIlluminationLux = emptyList()
        )
    }
    val maxT = points.keys.maxOrNull() ?: 0
    val t = (0..maxT).toList()
    val sInterpCm = MutableList(t.size) { Double.NaN }
    val sFilterCm = MutableList(t.size) { Double.NaN }
    val sInterpLux = MutableList(t.size) { Double.NaN }
    val sFilterLux = MutableList(t.size) { Double.NaN }
    for ((sec, value) in points) {
        if (sec < 0 || sec >= t.size) continue
        sInterpCm[sec] = value.sInterpCm
        sFilterCm[sec] = value.sFilterCm
        sInterpLux[sec] = value.sInterpLux
        sFilterLux[sec] = value.sFilterLux
    }
    return OutputSeries(
        tSeconds = t,
        sInterpDistanceCm = sInterpCm,
        sFilterDistanceCm = sFilterCm,
        sInterpIlluminationLux = sInterpLux,
        sFilterIlluminationLux = sFilterLux
    )
}

private fun writeProcessedCsv(file: File, output: OutputSeries) {
    val sb = StringBuilder()
    sb.appendLine("t_sec,s_interp_cm,s_filter_cm,s_interp_lux,s_filter_lux")
    for (i in output.tSeconds.indices) {
        sb.append(output.tSeconds[i]).append(',')
            .append(formatOrNaN(output.sInterpDistanceCm[i])).append(',')
            .append(formatOrNaN(output.sFilterDistanceCm[i])).append(',')
            .append(formatOrNaN(output.sInterpIlluminationLux[i])).append(',')
            .append(formatOrNaN(output.sFilterIlluminationLux[i])).append('\n')
    }
    file.writeText(sb.toString(), Charsets.UTF_8)
}

private fun writeTimelinePlotPng(
    file: File,
    title: String,
    yAxisLabel: String,
    tSeconds: List<Int>,
    series: List<PlotSeries>
) {
    val width = 1800
    val height = 700
    val ss = 2
    val w = width * ss
    val h = height * ss

    val padLeft = 130 * ss
    val padRight = 60 * ss
    val padTop = 95 * ss
    val padBottom = 110 * ss
    val plotW = w - padLeft - padRight
    val plotH = h - padTop - padBottom

    val pixels = IntArray(w * h) { Color.WHITE }

    val xHours = tSeconds.map { it / 3600.0 }
    val yValues = series.flatMap { it.values }.filter { it.isFinite() }
    val minYBase = yValues.minOrNull() ?: 0.0
    val maxYBase = yValues.maxOrNull() ?: 100.0
    val yPad = ((maxYBase - minYBase) * 0.06).coerceAtLeast(2.0)
    val minY = minYBase - yPad
    val maxY = maxYBase + yPad
    val ySpan = (maxY - minY).coerceAtLeast(1.0)
    val xMax = (xHours.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)

    fun xPx(x: Double): Int = (padLeft + (x / xMax) * plotW).toInt()
    fun yPx(y: Double): Int = (padTop + ((maxY - y) / ySpan) * plotH).toInt()

    val grid = Color.rgb(222, 226, 232)
    val axis = Color.rgb(30, 30, 30)
    val tick = Color.rgb(70, 70, 70)

    val xTicks = 8
    for (i in 0..xTicks) {
        val x = padLeft + (plotW * i / xTicks)
        drawLine(pixels, w, h, x, padTop, x, padTop + plotH, grid, thickness = ss)
        val tickValue = xMax * i.toDouble() / xTicks.toDouble()
        drawText(pixels, w, h, "%.2f".format(tickValue), x - (24 * ss), padTop + plotH + (30 * ss), scale = 2 * ss, color = tick)
    }

    val yTicks = 6
    for (i in 0..yTicks) {
        val y = padTop + (plotH * i / yTicks)
        drawLine(pixels, w, h, padLeft, y, padLeft + plotW, y, grid, thickness = ss)
        val tickValue = maxY - (ySpan * i.toDouble() / yTicks.toDouble())
        drawText(pixels, w, h, "%.1f".format(tickValue), padLeft - (70 * ss), y + (7 * ss), scale = 2 * ss, color = tick)
    }

    drawLine(pixels, w, h, padLeft, padTop, padLeft, padTop + plotH, axis, thickness = 2 * ss)
    drawLine(pixels, w, h, padLeft, padTop + plotH, padLeft + plotW, padTop + plotH, axis, thickness = 2 * ss)

    for ((index, item) in series.withIndex()) {
        val thickness = if (index == 0) 2 * ss else 3 * ss
        drawSeries(pixels, w, h, xHours, item.values, item.color, ::xPx, ::yPx, thickness = thickness)
    }

    val titleText = title.uppercase()
    drawText(
        pixels,
        w,
        h,
        titleText,
        w / 2 - ((titleText.length * 12) * ss),
        54 * ss,
        scale = 4 * ss,
        color = Color.rgb(20, 20, 20)
    )
    drawText(pixels, w, h, "TIME [H]", padLeft + plotW / 2 - (70 * ss), h - (28 * ss), scale = 3 * ss, color = axis)
    drawVerticalText(pixels, w, h, yAxisLabel.uppercase(), 32 * ss, padTop + plotH / 2 + (120 * ss), scale = 3 * ss, color = axis)

    val legendX = padLeft + plotW - (240 * ss)
    val legendY = padTop + (28 * ss)
    for ((idx, item) in series.withIndex()) {
        val yLegend = legendY + (idx * 32 * ss)
        drawLine(pixels, w, h, legendX, yLegend, legendX + (52 * ss), yLegend, item.color, thickness = 3 * ss)
        drawText(
            pixels,
            w,
            h,
            item.name.uppercase(),
            legendX + (64 * ss),
            yLegend + (8 * ss),
            scale = 2 * ss,
            color = Color.rgb(35, 35, 35)
        )
    }

    val finalPixels = downsampleBilinear(pixels, w, h, width, height)
    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    image.setPixels(finalPixels, 0, width, 0, 0, width, height)
    FileOutputStream(file).use { out ->
        image.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}

private fun downsampleBilinear(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
    val out = IntArray(dw * dh)
    for (y in 0 until dh) {
        val sy = ((y + 0.5) * sh / dh) - 0.5
        val y0 = sy.toInt().coerceIn(0, sh - 1)
        val y1 = (y0 + 1).coerceIn(0, sh - 1)
        val fy = (sy - y0).coerceIn(0.0, 1.0)
        for (x in 0 until dw) {
            val sx = ((x + 0.5) * sw / dw) - 0.5
            val x0 = sx.toInt().coerceIn(0, sw - 1)
            val x1 = (x0 + 1).coerceIn(0, sw - 1)
            val fx = (sx - x0).coerceIn(0.0, 1.0)

            val c00 = src[y0 * sw + x0]
            val c10 = src[y0 * sw + x1]
            val c01 = src[y1 * sw + x0]
            val c11 = src[y1 * sw + x1]

            val r = bilerp(c00 shr 16 and 0xFF, c10 shr 16 and 0xFF, c01 shr 16 and 0xFF, c11 shr 16 and 0xFF, fx, fy)
            val g = bilerp(c00 shr 8 and 0xFF, c10 shr 8 and 0xFF, c01 shr 8 and 0xFF, c11 shr 8 and 0xFF, fx, fy)
            val b = bilerp(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
            out[y * dw + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return out
}

private fun bilerp(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
    val a = c00 * (1 - fx) + c10 * fx
    val b = c01 * (1 - fx) + c11 * fx
    return (a * (1 - fy) + b * fy).toInt().coerceIn(0, 255)
}

private fun drawSeries(
    pixels: IntArray,
    width: Int,
    height: Int,
    x: List<Double>,
    y: List<Double>,
    color: Int,
    xPx: (Double) -> Int,
    yPx: (Double) -> Int,
    thickness: Int = 2
) {
    var prevX: Int? = null
    var prevY: Int? = null
    for (i in x.indices) {
        val yi = y[i]
        if (!yi.isFinite()) {
            prevX = null
            prevY = null
            continue
        }
        val xiPx = xPx(x[i])
        val yiPx = yPx(yi)
        if (prevX != null && prevY != null) {
            drawLine(pixels, width, height, prevX, prevY, xiPx, yiPx, color, thickness)
        } else {
            drawFilledCircle(pixels, width, height, xiPx, yiPx, radius = thickness, color = color)
        }
        prevX = xiPx
        prevY = yiPx
    }
}

private fun drawLine(
    pixels: IntArray,
    width: Int,
    height: Int,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    color: Int,
    thickness: Int = 1
) {
    var x = x0
    var y = y0
    val dx = kotlin.math.abs(x1 - x0)
    val sx = if (x0 < x1) 1 else -1
    val dy = -kotlin.math.abs(y1 - y0)
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    while (true) {
        for (tx in -thickness..thickness) {
            for (ty in -thickness..thickness) {
                setPixelSafe(pixels, width, height, x + tx, y + ty, color)
            }
        }
        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            err += dx
            y += sy
        }
    }
}

private fun drawFilledCircle(
    pixels: IntArray,
    width: Int,
    height: Int,
    cx: Int,
    cy: Int,
    radius: Int,
    color: Int
) {
    val r2 = radius * radius
    for (dy in -radius..radius) {
        for (dx in -radius..radius) {
            if (dx * dx + dy * dy <= r2) {
                setPixelSafe(pixels, width, height, cx + dx, cy + dy, color)
            }
        }
    }
}

private fun setPixelSafe(
    pixels: IntArray,
    width: Int,
    height: Int,
    x: Int,
    y: Int,
    color: Int
) {
    if (x !in 0 until width || y !in 0 until height) return
    pixels[y * width + x] = color
}

private fun drawText(
    pixels: IntArray,
    width: Int,
    height: Int,
    text: String,
    startX: Int,
    baselineY: Int,
    scale: Int,
    color: Int
) {
    var cursorX = startX
    val upper = text.uppercase()
    for (ch in upper) {
        val glyph = glyph5x7(ch)
        if (glyph == null) {
            cursorX += 4 * scale
            continue
        }
        for (row in glyph.indices) {
            val bits = glyph[row]
            for (col in 0 until 5) {
                if ((bits and (1 shl (4 - col))) != 0) {
                    for (sy in 0 until scale) {
                        for (sx in 0 until scale) {
                            val x = cursorX + col * scale + sx
                            val y = baselineY - (7 - row) * scale + sy
                            setPixelSafe(pixels, width, height, x, y, color)
                        }
                    }
                }
            }
        }
        cursorX += 6 * scale
    }
}

private fun drawVerticalText(
    pixels: IntArray,
    width: Int,
    height: Int,
    text: String,
    startX: Int,
    baselineY: Int,
    scale: Int,
    color: Int
) {
    var cursorY = baselineY
    val upper = text.uppercase()
    for (ch in upper) {
        val glyph = glyph5x7(ch)
        if (glyph == null) {
            cursorY -= 4 * scale
            continue
        }
        for (row in glyph.indices) {
            val bits = glyph[row]
            for (col in 0 until 5) {
                if ((bits and (1 shl (4 - col))) != 0) {
                    for (sy in 0 until scale) {
                        for (sx in 0 until scale) {
                            val x = startX + row * scale + sx
                            val y = cursorY - col * scale + sy
                            setPixelSafe(pixels, width, height, x, y, color)
                        }
                    }
                }
            }
        }
        cursorY -= 6 * scale
    }
}

private fun glyph5x7(ch: Char): IntArray? {
    return when (ch) {
        'A' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001)
        'B' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110)
        'C' -> intArrayOf(0b01110, 0b10001, 0b10000, 0b10000, 0b10000, 0b10001, 0b01110)
        'D' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110)
        'E' -> intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111)
        'F' -> intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000)
        'G' -> intArrayOf(0b01110, 0b10001, 0b10000, 0b10111, 0b10001, 0b10001, 0b01110)
        'H' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001)
        'I' -> intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111)
        'J' -> intArrayOf(0b11111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100)
        'K' -> intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001)
        'L' -> intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111)
        'M' -> intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001)
        'N' -> intArrayOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001)
        'O' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
        'P' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000)
        'Q' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101)
        'R' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001)
        'S' -> intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110)
        'T' -> intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100)
        'U' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
        'V' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100)
        'W' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b10101, 0b01010)
        'X' -> intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001)
        'Y' -> intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100)
        'Z' -> intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111)
        '[' -> intArrayOf(0b01110, 0b01000, 0b01000, 0b01000, 0b01000, 0b01000, 0b01110)
        ']' -> intArrayOf(0b01110, 0b00010, 0b00010, 0b00010, 0b00010, 0b00010, 0b01110)
        '(' -> intArrayOf(0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010)
        ')' -> intArrayOf(0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000)
        '.' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00110)
        '-' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000)
        '0' -> intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110)
        '1' -> intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110)
        '2' -> intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111)
        '3' -> intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110)
        '4' -> intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010)
        '5' -> intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b00001, 0b00001, 0b11110)
        '6' -> intArrayOf(0b01110, 0b10000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110)
        '7' -> intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000)
        '8' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110)
        '9' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110)
        ' ' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000)
        else -> null
    }
}

private fun parseEpochMillisOrNull(raw: String): Long? {
    val trimmed = raw.trim()
    trimmed.toLongOrNull()?.let { value ->
        return if (value > 10_000_000_000L) value else value * 1000L
    }
    runCatching { LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
    return null
}

private fun parseCsvLine(line: String): List<String> {
    if (line.isEmpty()) return emptyList()
    val out = ArrayList<String>(8)
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when (c) {
            '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 1
                } else {
                    inQuotes = !inQuotes
                }
            }
            ',' -> {
                if (inQuotes) current.append(c) else {
                    out.add(current.toString())
                    current.setLength(0)
                }
            }
            else -> current.append(c)
        }
        i += 1
    }
    out.add(current.toString())
    return out
}

private fun formatOrNaN(value: Double): String = if (value.isFinite()) value.toString() else "NaN"
