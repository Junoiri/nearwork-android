package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.model.WeeklyDaySummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyScreenHelpersTest {

    @Test
    fun formatHelpers_renderExpectedValues_andFallbacks() {
        val rangeLabel = invokeStatic("weeklyRangeLabel", WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14")), WeekRange::class.java) as String
        val parsed = invokeStatic("parseDayOrNull", "2026-06-15", String::class.java) as LocalDate?
        val invalid = invokeStatic("parseDayOrNull", "bad-day", String::class.java) as LocalDate?

        assertTrue(rangeLabel.contains("Jun") || rangeLabel.contains("cze"))
        assertEquals(LocalDate.parse("2026-06-15"), parsed)
        assertNull(invalid)
        assertEquals("12.3", invokeStatic("format1", 12.34, Double::class.javaPrimitiveType!!) as String)
        assertEquals("12", invokeStatic("format0", 12.34, Double::class.javaPrimitiveType!!) as String)
        assertEquals("12.35", invokeStatic("format2", 12.345, Double::class.javaPrimitiveType!!) as String)
        assertEquals("bad-day", invokeStatic("formatDay", "bad-day", String::class.java) as String)
        assertEquals("bad-day", invokeStatic("formatDayName", "bad-day", String::class.java) as String)
        assertEquals("bad-day", invokeStatic("formatLongDay", "bad-day", String::class.java) as String)
        assertTrue((invokeStatic("formatChartDayLabel", LocalDate.parse("2026-06-15"), LocalDate::class.java) as String).contains("\n"))
    }

    @Test
    fun weeklyHelpers_buildChartDays_windowDays_andWeightedAverage() {
        val range = WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14"))
        val days = listOf(
            weeklyDay("2026-06-08", 2, 30.0, 100.0),
            weeklyDay("2026-06-10", 4, 40.0, 300.0),
            weeklyDay("2026-06-14", 0, 80.0, 500.0)
        )

        val weightedDistance = invokeStatic(
            "weightedAverage",
            days,
            buildWeeklySelector(),
            List::class.java,
            kotlin.jvm.functions.Function1::class.java
        ) as Double?
        val windowDays = invokeStatic("resolveWeeklyWindowDays", range, WeekRange::class.java) as List<*>
        val chartDays = invokeStatic("buildWeeklyChartDays", days, range, List::class.java, WeekRange::class.java) as List<*>

        assertEquals(36.666666666666664, weightedDistance!!, 0.0001)
        assertEquals(7, windowDays.size)
        assertEquals(LocalDate.parse("2026-06-08"), windowDays.first())
        assertEquals(LocalDate.parse("2026-06-14"), windowDays.last())
        assertEquals(7, chartDays.size)
        assertEquals(LocalDate.parse("2026-06-08"), readProperty(chartDays.first()!!, "getDate"))
        assertEquals(LocalDate.parse("2026-06-10"), readProperty(chartDays[2]!!, "getDate"))
        assertEquals(days[1], readProperty(chartDays[2]!!, "getSummary"))
        assertEquals(null, readProperty(chartDays[1]!!, "getSummary"))
    }

    @Test
    fun weeklyHelpers_coverNullWeightedAverage_validFormatting_andValueObjects() {
        val range = WeekRange(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14"))
        val days = listOf(
            weeklyDay("2026-06-08", 0, 30.0, 100.0),
            weeklyDay("bad-day", 0, 40.0, 300.0),
            weeklyDay("2026-06-10", 0, 0.0, 500.0)
        )

        val weightedNullBecauseNoPositiveWeight = invokeStatic(
            "weightedAverage",
            days,
            buildWeeklySelector(),
            List::class.java,
            kotlin.jvm.functions.Function1::class.java
        ) as Double?
        val weightedNullBecauseSelectorReturnsNull = invokeStatic(
            "weightedAverage",
            listOf(weeklyDay("2026-06-08", 2, 30.0, 100.0)),
            buildNullWeeklySelector(),
            List::class.java,
            kotlin.jvm.functions.Function1::class.java
        ) as Double?
        val accentClass = Class.forName("com.example.nearworkthesis.feature.WeeklyAccentColors")
        val accent = accentClass.declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
            .newInstance(0L, 0L)
        val chartDayClass = Class.forName("com.example.nearworkthesis.feature.WeeklyChartDay")
        val chartDay = chartDayClass.getDeclaredConstructor(LocalDate::class.java, WeeklyDaySummary::class.java)
            .apply { isAccessible = true }
            .newInstance(LocalDate.parse("2026-06-08"), days.first())

        assertNull(weightedNullBecauseNoPositiveWeight)
        assertNull(weightedNullBecauseSelectorReturnsNull)
        assertTrue((invokeStatic("formatDay", "2026-06-15", String::class.java) as String).isNotBlank())
        assertTrue((invokeStatic("formatDayName", "2026-06-15", String::class.java) as String).isNotBlank())
        assertTrue((invokeStatic("formatLongDay", "2026-06-15", String::class.java) as String).contains("2026"))
        assertTrue((invokeStatic("formatShort", LocalDate.parse("2026-06-15"), LocalDate::class.java) as String).isNotBlank())
        assertNotNull(accent)
        assertEquals(LocalDate.parse("2026-06-08"), readProperty(chartDay, "getDate"))
        assertEquals(days.first(), readProperty(chartDay, "getSummary"))
        assertEquals(7, (invokeStatic("resolveWeeklyWindowDays", range, WeekRange::class.java) as List<*>).size)
    }

    private fun weeklyDay(day: String, samples: Int, distance: Double, lux: Double) = WeeklyDaySummary(
        day = day,
        sampleCount = samples,
        avgDistanceCm = distance,
        avgLux = lux,
        diopterHoursTotal = 0.5,
        nrs = 1.2,
        lowLightMinutes = 0,
        firstTimestampIso = null,
        lastTimestampIso = null
    )

    private fun buildWeeklySelector(): (WeeklyDaySummary) -> Double? = { summary -> summary.avgDistanceCm }
    private fun buildNullWeeklySelector(): (WeeklyDaySummary) -> Double? = { _ -> null }

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = weeklyScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, type1: Class<*>, type2: Class<*>): Any? {
        val method = weeklyScreenClass.getDeclaredMethod(name, type1, type2)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2)
    }

    private fun readProperty(instance: Any, getterName: String): Any? {
        val method = instance.javaClass.getDeclaredMethod(getterName)
        method.isAccessible = true
        return method.invoke(instance)
    }

    private companion object {
        val weeklyScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.WeeklyScreenKt")
    }
}
