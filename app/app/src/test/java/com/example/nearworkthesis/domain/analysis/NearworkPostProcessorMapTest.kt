package com.example.nearworkthesis.domain.analysis

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Post-processing map test:
 * - Step 1 metrics are asserted exactly.
 * - Non-Step1 placeholders are asserted null/empty until implemented.
 * - Outputs (CSV + PNGs) are written so this test remains useful as features evolve.
 */
@RunWith(RobolectricTestRunner::class)
class NearworkPostProcessorMapTest {

    @Test
    fun compute_onRealCsvPreprocessedData_writesPostprocessingArtifacts() {
        val fixture = buildRealFixture()
        val processor = NearworkPostProcessor()
        val result = processor.compute(
            samples = fixture.samples,
            settings = defaultSettings(),
            preprocessing = fixture.preprocessing
        )

        assertTrue("Expected preprocessed timeline to be non-empty", fixture.tSeconds.isNotEmpty())
        assertTrue("Expected real-data timeline longer than 1 hour", (fixture.tSeconds.maxOrNull() ?: 0) >= 3600)
        assertNotNull(result.totalNearTimeUnder40CmSeconds)
        assertNotNull(result.pctTimeNear40Cm)
        assertNotNull(result.meanDiopterDemand)
        assertNotNull(result.timeAbove2_5DiopterSeconds)
        assertNotNull(result.timeAbove3_0DiopterSeconds)
        assertEquals(fixture.tSeconds.size, result.diopterPerSecond.size)
        assertEquals(fixture.tSeconds.size, result.cumulativeDiopterHoursPerSecond.size)
        assertNotNull(result.timeOutdoor1000LuxMinutes)
        assertNotNull(result.pctOutdoorTime)
        assertNotNull(result.meanLuxDuringNearwork)
        assertNull(result.meanLuxDuringBreaks)
        assertNotNull(result.distanceStdCm)
        assertNotNull(result.distanceCv)
        assertNotNull(result.distanceCoveragePct)
        assertNotNull(result.luxCoveragePct)
        assertNotNull(result.diopterCoveragePct)
        assertTrue(result.transitionMatrix.isNotEmpty())
        assertTrue(result.dwellTimesByStateSeconds.isNotEmpty())
        assertNotNull(result.compositeRiskScore)
        assertNotNull(result.insightsSignals)

        assertNotNull(result.sessions)
        assertNotNull(result.flaggedSessions)
        assertTrue(result.totalDiopterHours >= 0.0)
        assertTrue(result.lowLightMinutes >= 0)

        val outDir = Paths.get("build", "test-outputs", "postprocessing")
        Files.createDirectories(outDir)
        writeMetricsSnapshot(outDir.resolve("nearwork_postprocessing_metrics_snapshot.csv"), result)
        writeMetricsSummaryText(outDir.resolve("nearwork_postprocessing_metrics_summary.txt"), result)
        writeDistanceTimelinePlot(
            file = outDir.resolve("nearwork_postprocessing_distance_timeline.png").toFile(),
            tSeconds = fixture.tSeconds,
            distanceCm = fixture.classificationDistance
        )
        writeExposureBandPlot(
            file = outDir.resolve("nearwork_postprocessing_exposure_bands.png").toFile(),
            tSeconds = fixture.tSeconds,
            distanceCm = fixture.classificationDistance
        )
        writeDiopterTimelinePlot(
            file = outDir.resolve("nearwork_postprocessing_diopter_timeline.png").toFile(),
            tSeconds = fixture.tSeconds,
            diopters = result.diopterPerSecond
        )
        writeDiopterHoursTimelinePlot(
            file = outDir.resolve("nearwork_postprocessing_diopter_hours_timeline.png").toFile(),
            tSeconds = fixture.tSeconds,
            cumulativeDiopterHours = result.cumulativeDiopterHoursPerSecond
        )
        writeIlluminationTimelinePlot(
            file = outDir.resolve("nearwork_postprocessing_illumination_timeline.png").toFile(),
            tSeconds = fixture.tSeconds,
            lux = fixture.classificationLux
        )
        writeNotImplementedPlot(
            file = outDir.resolve("nearwork_postprocessing_transition_plot_placeholder.png").toFile(),
            title = "Transitions heatmap",
            message = "PLOT NOT IMPLEMENTED YET"
        )
        writeNotImplementedPlot(
            file = outDir.resolve("nearwork_postprocessing_variability_placeholder.png").toFile(),
            title = "Variability / Transitions",
            message = "NOT IMPLEMENTED YET"
        )

        println("Post-processing metrics summary:\n" + buildMetricsSummaryText(result))
    }
}

private data class RealFixture(
    val preprocessing: PreprocessingResult,
    val samples: List<NearworkSample>,
    val tSeconds: List<Int>,
    val classificationDistance: List<Double>,
    val classificationLux: List<Double>
)

private fun defaultSettings(): NearworkSettings {
    return NearworkSettings(
        lowLightThresholdLux = 300,
        nearworkDistanceThresholdCm = 60,
        breakGapSeconds = 60,
        minSessionDurationSeconds = 60,
        closeDistanceThresholdCm = 30,
        extremeCloseThresholdCm = 20
    )
}

private fun buildRealFixture(): RealFixture {
    val inputFile = resolveInputFile()
    assertTrue(
        "Input CSV missing. Place nearwork_raw_2000-01-02.csv in app/test-inputs/preprocessing/",
        inputFile.exists()
    )

    val rows = parseRows(inputFile)
    val pipeline = PreprocessingPipeline()
    val preprocessing = preprocessWholeCsv(rows, pipeline)

    val classificationDistance = resolveDistanceSeriesForPlots(preprocessing)
    val classificationLux = preprocessing.sInterpIlluminationLux

    return RealFixture(
        preprocessing = preprocessing,
        samples = preprocessing.samples,
        tSeconds = preprocessing.tSeconds,
        classificationDistance = classificationDistance,
        classificationLux = classificationLux
    )
}

private data class PostInputRow(
    val timeToken: String,
    val distanceCm: Double,
    val lux: Double
)

private fun resolveInputFile(): File {
    val candidates = listOf(
        Paths.get("app", "test-inputs", "preprocessing", "nearwork_raw_2000-01-02.csv"),
        Paths.get("test-inputs", "preprocessing", "nearwork_raw_2000-01-02.csv")
    )
    return candidates.asSequence().map(Path::toFile).firstOrNull { it.exists() } ?: candidates.first().toFile()
}

private fun parseRows(file: File): List<PostInputRow> {
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
    val hasHeaderNames = timeIdxByName >= 0 || distanceIdxByName >= 0

    val startIndex = if (hasHeaderNames) 1 else 0
    val timeIdx = if (timeIdxByName >= 0) timeIdxByName else 0
    val distanceIdx = if (distanceIdxByName >= 0) distanceIdxByName else 4
    val luxIdx = if (luxIdxByName >= 0) luxIdxByName else 5

    val rows = ArrayList<PostInputRow>(lines.size - startIndex)
    for (i in startIndex until lines.size) {
        val cols = parseCsvLine(lines[i])
        val timeToken = cols.getOrNull(timeIdx)?.trim().orEmpty()
        val distance = cols.getOrNull(distanceIdx)?.trim()?.toDoubleOrNull()
        val lux = cols.getOrNull(luxIdx)?.trim()?.toDoubleOrNull()
        if (timeToken.isBlank() || distance == null || !distance.isFinite() || lux == null || !lux.isFinite()) continue
        rows.add(PostInputRow(timeToken = timeToken, distanceCm = distance, lux = lux))
    }
    require(rows.isNotEmpty()) { "No valid rows in input: ${file.absolutePath}" }
    return rows
}

private fun preprocessWholeCsv(
    rows: List<PostInputRow>,
    pipeline: PreprocessingPipeline
): PreprocessingResult {
    val epochByRow = rows.map { parseEpochMillisOrNull(it.timeToken) }
    val hasEpochForAll = epochByRow.all { it != null }

    return if (hasEpochForAll) {
        val allSamples = rows.indices.map { idx ->
            NearworkSample(
                timestampMillis = epochByRow[idx]!!,
                distanceCm = rows[idx].distanceCm,
                lux = rows[idx].lux
            )
        }
        pipeline.process(allSamples)
    } else {
        val processed = pipeline.processTimeOfDay(
            Time_stamp = rows.map { it.timeToken },
            Distance = rows.map { it.distanceCm },
            Lux = rows.map { it.lux }
        )
        val samples = processed.tSeconds.indices.map { i ->
            NearworkSample(
                timestampMillis = processed.tSeconds[i].toLong() * 1000L,
                distanceCm = processed.sFilterDistanceCm[i],
                lux = processed.resolvedIlluminationLux[i]
            )
        }
        val interpolated = processed.tSeconds.indices.map { i ->
            NearworkSample(
                timestampMillis = processed.tSeconds[i].toLong() * 1000L,
                distanceCm = processed.sInterpDistanceCm[i],
                lux = processed.sInterpIlluminationLux[i]
            )
        }
        PreprocessingResult(
            tSeconds = processed.tSeconds,
            sInterpDistanceCm = processed.sInterpDistanceCm,
            sFilterDistanceCm = processed.sFilterDistanceCm,
            sInterpIlluminationLux = processed.sInterpIlluminationLux,
            sFilterIlluminationLux = processed.sFilterIlluminationLux,
            samples = samples,
            interpolatedSamples = interpolated,
            stats = PreprocessingStats(
                rawCount = rows.size,
                dedupedCount = processed.duplicateCount,
                rejectedCount = 0,
                outputCount = processed.tSeconds.size,
                smoothingWindowSize = pipeline.config.smoothingWindowSize
            )
        )
    }
}

private fun resolveDistanceSeriesForPlots(preprocessing: PreprocessingResult): List<Double> {
    val smoothed = preprocessing.sFilterDistanceCm
    return if (smoothed.any { it.isFinite() }) smoothed else preprocessing.sInterpDistanceCm
}

private fun parseEpochMillisOrNull(raw: String): Long? {
    val trimmed = raw.trim()
    trimmed.toLongOrNull()?.let { value ->
        return if (value > 10_000_000_000L) value else value * 1000L
    }
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

private fun writeMetricsSnapshot(path: Path, result: NearworkPostProcessingResult) {
    val transitionMatrixValue = result.transitionMatrix.entries.joinToString(";") { "${it.key}:${it.value}" }
    val dwellTimesValue = result.dwellTimesByStateSeconds.entries.joinToString(";") { "${it.key}:${it.value}" }
    val insightsValue = result.insightsSignals?.let {
        "near40=${it.totalNearTimeUnder40CmSeconds};dh=${it.totalDiopterHours};lowLightMin=${it.lowLightMinutes};longestSec=${it.longestSessionSeconds};outdoorMin=${it.outdoorMinutes1000Lux}"
    } ?: ""
    val rows = listOf(
        "metric_name,metric_value",
        "totalDiopterHours,${result.totalDiopterHours}",
        "lowLightMinutes,${result.lowLightMinutes}",
        "sessions_count,${result.sessions.size}",
        "longestSessionSeconds,${result.longestSession?.durationSeconds ?: ""}",
        "flaggedSessions_count,${result.flaggedSessions.size}",
        "flaggedSessionCount,${result.flaggedSessionCount}",
        "totalNearTimeUnder40CmSeconds,${result.totalNearTimeUnder40CmSeconds ?: ""}",
        "pctTimeNear40Cm,${result.pctTimeNear40Cm ?: ""}",
        "timeBelow30CmSeconds,${result.timeBelow30CmSeconds ?: ""}",
        "timeIntermediate40To70CmSeconds,${result.timeIntermediate40To70CmSeconds ?: ""}",
        "timeFarAbove70CmSeconds,${result.timeFarAbove70CmSeconds ?: ""}",
        "meanDiopterDemand,${result.meanDiopterDemand ?: ""}",
        "timeAbove2_5DiopterSeconds,${result.timeAbove2_5DiopterSeconds ?: ""}",
        "timeAbove3_0DiopterSeconds,${result.timeAbove3_0DiopterSeconds ?: ""}",
        "diopterSeriesLength,${result.diopterPerSecond.size}",
        "cumulativeDiopterHoursSeriesLength,${result.cumulativeDiopterHoursPerSecond.size}",
        "timeOutdoor1000LuxMinutes,${result.timeOutdoor1000LuxMinutes ?: ""}",
        "pctOutdoorTime,${result.pctOutdoorTime ?: ""}",
        "meanLuxDuringNearwork,${result.meanLuxDuringNearwork ?: ""}",
        "meanLuxDuringBreaks,${result.meanLuxDuringBreaks ?: ""}",
        "nearworkInLowLightSeconds,${result.nearworkInLowLightSeconds ?: ""}",
        "distanceStdCm,${result.distanceStdCm ?: ""}",
        "distanceCv,${result.distanceCv ?: ""}",
        "distanceCoveragePct,${result.distanceCoveragePct ?: ""}",
        "distanceCoveragePercent,${result.distanceCoveragePct?.times(100.0) ?: ""}",
        "luxCoveragePct,${result.luxCoveragePct ?: ""}",
        "luxCoveragePercent,${result.luxCoveragePct?.times(100.0) ?: ""}",
        "diopterCoveragePct,${result.diopterCoveragePct ?: ""}",
        "diopterCoveragePercent,${result.diopterCoveragePct?.times(100.0) ?: ""}",
        "transitionMatrix,$transitionMatrixValue",
        "dwellTimesByStateSeconds,$dwellTimesValue",
        "compositeRiskScore,${result.compositeRiskScore ?: ""}",
        "insightsSignals,$insightsValue"
    )
    path.toFile().writeText(rows.joinToString("\n") + "\n", Charsets.UTF_8)
}

private fun writeMetricsSummaryText(path: Path, result: NearworkPostProcessingResult) {
    path.toFile().writeText(buildMetricsSummaryText(result), Charsets.UTF_8)
}

private fun buildMetricsSummaryText(result: NearworkPostProcessingResult): String {
    return buildString {
        appendLine("Nearwork Post-Processing Metrics")
        appendLine("totalDiopterHours=${result.totalDiopterHours} // cumulative accommodative demand integral over valid time (D*h)")
        appendLine("lowLightMinutes=${result.lowLightMinutes} // minutes where lux < lowLightThreshold in settings")
        appendLine("sessions_count=${result.sessions.size} // number of nearwork sessions detected by session detector")
        appendLine("flaggedSessions_count=${result.flaggedSessions.size} // number of sessions returned by risk classifier as flagged")
        appendLine("totalNearTimeUnder40CmSeconds=${result.totalNearTimeUnder40CmSeconds} // valid seconds with distance < 40 cm")
        appendLine("pctTimeNear40Cm=${result.pctTimeNear40Cm} // fraction of valid distance seconds spent at < 40 cm")
        appendLine("timeBelow30CmSeconds=${result.timeBelow30CmSeconds} // valid seconds with distance < 30 cm")
        appendLine("timeIntermediate40To70CmSeconds=${result.timeIntermediate40To70CmSeconds} // valid seconds with 40 cm <= distance <= 70 cm")
        appendLine("timeFarAbove70CmSeconds=${result.timeFarAbove70CmSeconds} // valid seconds with distance > 70 cm")
        appendLine("meanDiopterDemand=${result.meanDiopterDemand} // mean diopter value (100/distance_cm) over valid distance seconds")
        appendLine("timeAbove2_5DiopterSeconds=${result.timeAbove2_5DiopterSeconds} // valid seconds where diopter demand > 2.5 D")
        appendLine("timeAbove3_0DiopterSeconds=${result.timeAbove3_0DiopterSeconds} // valid seconds where diopter demand > 3.0 D")
        appendLine("diopterSeriesLength=${result.diopterPerSecond.size} // number of 1 Hz samples in diopter time-series")
        appendLine("cumulativeDiopterHoursSeriesLength=${result.cumulativeDiopterHoursPerSecond.size} // number of 1 Hz samples in cumulative D*h series")
        appendLine("timeOutdoor1000LuxMinutes=${result.timeOutdoor1000LuxMinutes} // minutes with lux >= 1000 (outdoor proxy)")
        appendLine("pctOutdoorTime=${result.pctOutdoorTime} // fraction of valid lux seconds spent at >= 1000 lux")
        appendLine("meanLuxDuringNearwork=${result.meanLuxDuringNearwork} // mean lux during valid nearwork seconds (distance < 40 cm)")
        appendLine("meanLuxDuringBreaks=${result.meanLuxDuringBreaks} // mean lux during breaks (null until explicit break intervals exist)")
        appendLine("nearworkInLowLightSeconds=${result.nearworkInLowLightSeconds} // seconds where distance < 40 cm AND lux < lowLightThreshold")
        appendLine("distanceStdCm=${result.distanceStdCm} // standard deviation of valid distance series used for post-processing")
        appendLine("distanceCv=${result.distanceCv} // coefficient of variation = std/mean for valid distance series")
        appendLine("distanceCoveragePct=${result.distanceCoveragePct} // valid distance samples / total distance timeline samples")
        appendLine("distanceCoveragePercent=${result.distanceCoveragePct?.times(100.0)}% // same as distanceCoveragePct in percent")
        appendLine("luxCoveragePct=${result.luxCoveragePct} // valid lux samples / total lux timeline samples")
        appendLine("luxCoveragePercent=${result.luxCoveragePct?.times(100.0)}% // same as luxCoveragePct in percent")
        appendLine("diopterCoveragePct=${result.diopterCoveragePct} // valid diopter samples / total diopter timeline samples")
        appendLine("diopterCoveragePercent=${result.diopterCoveragePct?.times(100.0)}% // same as diopterCoveragePct in percent")
        appendLine("transitionMatrix=${result.transitionMatrix} // adjacent-state transition counts among NEAR/INTERMEDIATE/FAR")
        appendLine("dwellTimesByStateSeconds=${result.dwellTimesByStateSeconds} // total valid seconds spent in each NEAR/INTERMEDIATE/FAR state")
        appendLine("compositeRiskScore=${result.compositeRiskScore} // aggregated numeric score from risky session reasons and durations")
        appendLine("insightsSignals=${result.insightsSignals} // structured key signals for downstream narrative insights")
    }
}

private fun writeDistanceTimelinePlot(
    file: File,
    tSeconds: List<Int>,
    distanceCm: List<Double>
) {
    val bitmap = drawLineChart(
        title = "Nearwork post-processing: distance timeline",
        xLabel = "Time [h]",
        yLabel = "Distance (cm)",
        xHours = tSeconds.map { it / 3600.0 },
        series = listOf(
            ChartSeries("Classification distance", distanceCm, Color.rgb(33, 113, 181))
        )
    )
    saveBitmap(bitmap, file)
}

private fun writeExposureBandPlot(
    file: File,
    tSeconds: List<Int>,
    distanceCm: List<Double>
) {
    val bandCode = distanceCm.map { value ->
        if (!value.isFinite()) Double.NaN
        else if (value < 40.0) 0.0
        else if (value <= 70.0) 1.0
        else 2.0
    }
    val bitmap = drawLineChart(
        title = "Nearwork post-processing: exposure bands",
        xLabel = "Time [h]",
        yLabel = "Band (0=near,1=intermediate,2=far)",
        xHours = tSeconds.map { it / 3600.0 },
        series = listOf(
            ChartSeries("Band state", bandCode, Color.rgb(203, 24, 29))
        ),
        forceYMin = -0.2,
        forceYMax = 2.2
    )
    saveBitmap(bitmap, file)
}

private fun writeDiopterTimelinePlot(
    file: File,
    tSeconds: List<Int>,
    diopters: List<Double>
) {
    val bitmap = drawLineChart(
        title = "Nearwork post-processing: diopter timeline",
        xLabel = "Time [h]",
        yLabel = "Diopter [D]",
        xHours = tSeconds.map { it / 3600.0 },
        series = listOf(
            ChartSeries("Diopter", diopters, Color.rgb(49, 130, 189)),
            ChartSeries("2.5D", List(diopters.size) { 2.5 }, Color.rgb(117, 107, 177)),
            ChartSeries("3.0D", List(diopters.size) { 3.0 }, Color.rgb(214, 96, 77))
        )
    )
    saveBitmap(bitmap, file)
}

private fun writeDiopterHoursTimelinePlot(
    file: File,
    tSeconds: List<Int>,
    cumulativeDiopterHours: List<Double>
) {
    val bitmap = drawLineChart(
        title = "Nearwork post-processing: dioptre-hours timeline",
        xLabel = "Time [h]",
        yLabel = "Cumulative Diopter-hours [D*h]",
        xHours = tSeconds.map { it / 3600.0 },
        series = listOf(
            ChartSeries("Cumulative D*h", cumulativeDiopterHours, Color.rgb(127, 59, 8))
        )
    )
    saveBitmap(bitmap, file)
}

private fun writeIlluminationTimelinePlot(
    file: File,
    tSeconds: List<Int>,
    lux: List<Double>
) {
    val bitmap = drawLineChart(
        title = "Nearwork post-processing: illumination timeline",
        xLabel = "Time [h]",
        yLabel = "Illumination [lux]",
        xHours = tSeconds.map { it / 3600.0 },
        series = listOf(
            ChartSeries("Lux (interp)", lux, Color.rgb(44, 162, 95))
        )
    )
    saveBitmap(bitmap, file)
}

private fun writeNotImplementedPlot(file: File, title: String, message: String) {
    val width = 1800
    val height = 700
    val pixels = IntArray(width * height) { Color.WHITE }
    drawText(pixels, width, height, title.uppercase(), 60, 120, scale = 5, color = Color.rgb(20, 20, 20))
    drawText(pixels, width, height, message.uppercase(), 60, 190, scale = 4, color = Color.rgb(40, 40, 40))
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    saveBitmap(bitmap, file)
}

private data class ChartSeries(
    val name: String,
    val y: List<Double>,
    val color: Int
)

private fun drawLineChart(
    title: String,
    xLabel: String,
    yLabel: String,
    xHours: List<Double>,
    series: List<ChartSeries>,
    forceYMin: Double? = null,
    forceYMax: Double? = null
) : Bitmap {
    val width = 1800
    val height = 700
    val ss = 2
    val w = width * ss
    val h = height * ss

    val padLeft = 130 * ss
    val padRight = 60 * ss
    val padTop = 90 * ss
    val padBottom = 110 * ss
    val plotW = w - padLeft - padRight
    val plotH = h - padTop - padBottom

    val pixels = IntArray(w * h) { Color.WHITE }

    val allY = series.flatMap { it.y }.filter { it.isFinite() }
    val minYRaw = forceYMin ?: (allY.minOrNull() ?: 0.0)
    val maxYRaw = forceYMax ?: (allY.maxOrNull() ?: 1.0)
    val yPad = if (forceYMin != null || forceYMax != null) 0.0 else ((maxYRaw - minYRaw) * 0.06).coerceAtLeast(1.0)
    val minY = minYRaw - yPad
    val maxY = maxYRaw + yPad
    val ySpan = (maxY - minY).coerceAtLeast(1.0)
    val xMax = (xHours.maxOrNull() ?: 1.0).coerceAtLeast(1e-9)

    fun xPx(x: Double): Int = (padLeft + (x / xMax) * plotW).toInt()
    fun yPx(y: Double): Int = (padTop + ((maxY - y) / ySpan) * plotH).toInt()

    val grid = Color.rgb(225, 229, 235)
    val axis = Color.rgb(30, 30, 30)
    val tick = Color.rgb(80, 80, 80)
    val label = Color.rgb(20, 20, 20)

    val xTicks = 8
    for (i in 0..xTicks) {
        val x = padLeft + (plotW * i / xTicks)
        drawLine(pixels, w, h, x, padTop, x, padTop + plotH, grid, thickness = ss)
    }
    val yTicks = 6
    for (i in 0..yTicks) {
        val y = padTop + (plotH * i / yTicks)
        drawLine(pixels, w, h, padLeft, y, padLeft + plotW, y, grid, thickness = ss)
    }

    drawLine(pixels, w, h, padLeft, padTop, padLeft, padTop + plotH, axis, thickness = 2 * ss)
    drawLine(pixels, w, h, padLeft, padTop + plotH, padLeft + plotW, padTop + plotH, axis, thickness = 2 * ss)

    drawText(pixels, w, h, title.uppercase(), 50 * ss, 52 * ss, scale = 3 * ss, color = label)
    drawText(pixels, w, h, xLabel.uppercase(), padLeft + plotW / 2 - (80 * ss), h - (28 * ss), scale = 2 * ss, color = axis)
    drawVerticalText(pixels, w, h, yLabel.uppercase(), 26 * ss, padTop + plotH / 2 + (120 * ss), scale = 2 * ss, color = axis)

    for (i in 0..xTicks) {
        val x = padLeft + (plotW * i / xTicks)
        val v = xMax * i.toDouble() / xTicks.toDouble()
        drawText(pixels, w, h, String.format("%.2f", v), x - (24 * ss), padTop + plotH + (30 * ss), scale = 2 * ss, color = tick)
    }
    for (i in 0..yTicks) {
        val y = padTop + (plotH * i / yTicks)
        val v = maxY - (ySpan * i.toDouble() / yTicks.toDouble())
        drawText(pixels, w, h, String.format("%.1f", v), padLeft - (70 * ss), y + (8 * ss), scale = 2 * ss, color = tick)
    }

    for ((index, s) in series.withIndex()) {
        drawSeries(
            pixels = pixels,
            width = w,
            height = h,
            x = xHours,
            y = s.y,
            color = s.color,
            xPx = ::xPx,
            yPx = ::yPx,
            thickness = if (index == 0) 2 * ss else 3 * ss
        )
    }

    val legendX = padLeft + plotW - 280
    var legendY = padTop + 24
    for (s in series) {
        drawLine(pixels, w, h, legendX, legendY, legendX + 55 * ss, legendY, s.color, thickness = 3 * ss)
        drawText(pixels, w, h, s.name.uppercase(), legendX + (70 * ss), legendY + (8 * ss), scale = 2 * ss, color = Color.rgb(35, 35, 35))
        legendY += 30
    }

    val finalPixels = downsampleBilinear(pixels, w, h, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(finalPixels, 0, width, 0, 0, width, height)
    return bitmap
}

private fun saveBitmap(bitmap: Bitmap, file: File) {
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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
        'L' -> intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111)
        'M' -> intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001)
        'N' -> intArrayOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001)
        'O' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
        'P' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000)
        'R' -> intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001)
        'S' -> intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110)
        'T' -> intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100)
        'U' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110)
        'V' -> intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100)
        'X' -> intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001)
        'Y' -> intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100)
        '[' -> intArrayOf(0b01110, 0b01000, 0b01000, 0b01000, 0b01000, 0b01000, 0b01110)
        ']' -> intArrayOf(0b01110, 0b00010, 0b00010, 0b00010, 0b00010, 0b00010, 0b01110)
        '(' -> intArrayOf(0b00010, 0b00100, 0b01000, 0b01000, 0b01000, 0b00100, 0b00010)
        ')' -> intArrayOf(0b01000, 0b00100, 0b00010, 0b00010, 0b00010, 0b00100, 0b01000)
        '.' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00110)
        '-' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000)
        '_' -> intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b11111)
        ':' -> intArrayOf(0b00000, 0b00110, 0b00110, 0b00000, 0b00110, 0b00110, 0b00000)
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
