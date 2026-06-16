package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.analysis.NearworkSample
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyScreenHelpersTest {

    @Test
    fun formatHelpers_renderExpectedValues_andFallbacks() {
        assertEquals("12.3", invokeFormat1(12.34))
        assertEquals("12.35", invokeFormat2(12.345))
        assertEquals("12.3", invokeFormatRange(12.34))
        assertEquals("12", invokeFormatValue(12.34))
        assertEquals("—", invokeFormatRange(Double.NaN))
        assertEquals("07:05", invokeFormatTime("2026-06-15T07:05:00"))
        assertEquals("—", invokeFormatTime("bad-value"))
        assertEquals("not-a-day", invokeFormatDate("not-a-day"))
        assertEquals("59m", invokeFormatDuration(3_540))
        assertEquals("2h 5m", invokeFormatDuration(7_500))
        assertEquals("00:00–01:01", invokeFormatRangeHm(0L, 3_660_000L))
    }

    @Test
    fun distributionHelpers_buildExpectedBuckets_andIgnoreNonFiniteSamples() {
        val rows = invokeDistanceZoneRows(
            listOf(
                NearworkSample(1L, 15.0, 20.0),
                NearworkSample(2L, 25.0, 80.0),
                NearworkSample(3L, 40.0, 400.0),
                NearworkSample(4L, 80.0, 5_000.0),
                NearworkSample(5L, Double.NaN, 90.0)
            )
        )
        val lightRows = invokeLightingConditionRows(
            listOf(
                NearworkSample(1L, 20.0, 20.0),
                NearworkSample(2L, 25.0, 200.0),
                NearworkSample(3L, 35.0, 5_000.0),
                NearworkSample(4L, 50.0, Double.POSITIVE_INFINITY)
            )
        )

        assertEquals(4, rows.size)
        assertEquals(3, lightRows.size)
        assertEquals(1.0, rows.sumOf(::rowFraction), 0.0001)
        assertEquals(1.0, lightRows.sumOf(::rowFraction), 0.0001)
        assertTrue(rowLabel(rows[0]).contains("<"))
        assertTrue(rowLabel(lightRows[0]).contains("Low-light"))
    }

    @Test
    fun distributionHelpers_respectBoundaryBuckets_exactly() {
        val distanceRows = invokeDistanceZoneRows(
            listOf(
                NearworkSample(1L, 20.0, 50.0),
                NearworkSample(2L, 30.0, 50.0),
                NearworkSample(3L, 60.0, 50.0),
                NearworkSample(4L, 61.0, 50.0)
            )
        )
        val lightRows = invokeLightingConditionRows(
            listOf(
                NearworkSample(1L, 20.0, 54.0),
                NearworkSample(2L, 20.0, 55.0),
                NearworkSample(3L, 20.0, 56.0),
                NearworkSample(4L, 20.0, 3000.0)
            )
        )

        assertEquals(0.0, rowFraction(distanceRows[0]), 0.0001)
        assertEquals(0.25, rowFraction(distanceRows[1]), 0.0001)
        assertEquals(0.25, rowFraction(distanceRows[2]), 0.0001)
        assertEquals(0.5, rowFraction(distanceRows[3]), 0.0001)
        assertEquals(0.5, rowFraction(lightRows[0]), 0.0001)
        assertEquals(0.25, rowFraction(lightRows[1]), 0.0001)
        assertEquals(0.25, rowFraction(lightRows[2]), 0.0001)
    }

    @Test
    fun distributionHelpers_returnEmptyLists_whenNoFiniteSamplesRemain() {
        assertTrue(invokeDistanceZoneRows(listOf(NearworkSample(1L, Double.NaN, 50.0))).isEmpty())
        assertTrue(invokeLightingConditionRows(listOf(NearworkSample(1L, 20.0, Double.NaN))).isEmpty())
    }

    @Test
    fun formatHelpers_rejectInfiniteValues_andParseSpacedIsoTimes() {
        assertEquals("—", invokeFormatRange(Double.POSITIVE_INFINITY))
        assertEquals("—", invokeFormatValue(Double.NEGATIVE_INFINITY))
        assertEquals("07:05", invokeFormatTime("2026-06-15 07:05:00"))
        assertEquals("0m", invokeFormatDuration(59))
    }

    @Test
    fun screenValueObjects_constructViaReflection() {
        val toneClass = Class.forName("com.example.nearworkthesis.feature.DistributionColorTone")
        val primaryTone = java.lang.Enum.valueOf(toneClass.asSubclass(Enum::class.java), "Primary")
        val rowClass = Class.forName("com.example.nearworkthesis.feature.DistributionRow")
        val row = rowClass.getDeclaredConstructor(String::class.java, Double::class.javaPrimitiveType, toneClass)
            .apply { isAccessible = true }
            .newInstance("Row", 0.5, primaryTone)
        val accentClass = Class.forName("com.example.nearworkthesis.feature.DailyAccentColors")
        val accent = accentClass.declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
            .newInstance(0L, 0L)

        assertEquals("Row", rowLabel(row))
        assertEquals(0.5, rowFraction(row), 0.0)
        assertNotNull(accent)
        assertEquals(4, toneClass.enumConstants.size)
    }

    private fun invokeFormatRange(value: Double?): String = invokeStatic("formatRange", value, Double::class.javaObjectType) as String
    private fun invokeFormatValue(value: Double?): String = invokeStatic("formatValue", value, Double::class.javaObjectType) as String
    private fun invokeFormat1(value: Double): String = invokeStatic("format1", value, Double::class.javaPrimitiveType!!) as String
    private fun invokeFormat2(value: Double): String = invokeStatic("format2", value, Double::class.javaPrimitiveType!!) as String
    private fun invokeFormatTime(value: String): String = invokeStatic("formatTime", value, String::class.java) as String
    private fun invokeFormatDate(value: String): String = invokeStatic("formatDate", value, String::class.java) as String
    private fun invokeFormatDuration(value: Long): String = invokeStatic("formatDuration", value, Long::class.javaPrimitiveType!!) as String
    private fun invokeFormatRangeHm(start: Long, end: Long): String {
        val method = dailyScreenClass.getDeclaredMethod("formatRangeHm", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(null, start, end) as String
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeDistanceZoneRows(samples: List<NearworkSample>): List<Any> =
        invokeStatic("distanceZoneRows", samples, List::class.java) as List<Any>

    @Suppress("UNCHECKED_CAST")
    private fun invokeLightingConditionRows(samples: List<NearworkSample>): List<Any> =
        invokeStatic("lightingConditionRows", samples, List::class.java) as List<Any>

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = dailyScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private fun rowFraction(row: Any): Double {
        val method = row.javaClass.getDeclaredMethod("getFraction")
        method.isAccessible = true
        return method.invoke(row) as Double
    }

    private fun rowLabel(row: Any): String {
        val method = row.javaClass.getDeclaredMethod("getLabel")
        method.isAccessible = true
        return method.invoke(row) as String
    }

    private companion object {
        val dailyScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.DailyScreenKt")
    }
}
