package com.example.nearworkthesis.feature

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryScreenHelpersTest {

    @Test
    fun calendarCells_andFormattingHelpers_behaveAsExpected() {
        val month = YearMonth.of(2026, 6)
        val cells = invokeStatic("rememberCalendarCells", month, YearMonth::class.java) as List<*>

        assertEquals(42, cells.size)
        assertEquals(LocalDate.parse("2026-06-01"), cells[0])
        assertEquals(LocalDate.parse("2026-06-30"), cells.filterNotNull().last())
        assertEquals("bad-day", invokeStatic("formatDate", "bad-day", String::class.java) as String)
        assertEquals("bad-day", invokeStatic("formatDayName", "bad-day", String::class.java) as String)
        assertEquals("bad-day", invokeStatic("formatFullDate", "bad-day", String::class.java) as String)
        assertEquals("—", invokeStatic("formatTime", "bad-iso", String::class.java) as String)
    }

    @Test
    fun metricFormatter_returnsDashForNonPositiveOrInvalidValues() {
        assertEquals("—", invokeStatic("formatMetricOrDash", 0.0, Double::class.javaPrimitiveType!!) as String)
        assertEquals("—", invokeStatic("formatMetricOrDash", Double.NaN, Double::class.javaPrimitiveType!!) as String)
        assertEquals("1.23", invokeStatic("formatMetricOrDash", 1.234, Double::class.javaPrimitiveType!!) as String)
        val formattedTime = invokeStatic("formatTime", "2026-06-15T07:05:00", String::class.java) as String
        assertTrue(formattedTime.contains("07:05"))
    }

    @Test
    fun validFormattingPaths_andAccentValueObject_areCovered() {
        val formattedDate = invokeStatic("formatDate", "2026-06-15", String::class.java) as String
        val formattedDayName = invokeStatic("formatDayName", "2026-06-15", String::class.java) as String
        val formattedFullDate = invokeStatic("formatFullDate", "2026-06-15", String::class.java) as String
        val formattedTime = invokeStatic("formatTime", "2026-06-15 07:05:00", String::class.java) as String
        val accentClass = Class.forName("com.example.nearworkthesis.feature.HistoryAccentColors")
        val accent = accentClass.declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
            .newInstance(0L, 0L)
        val dayStatusClass = Class.forName("com.example.nearworkthesis.feature.DayStatus")
        val dayStatus = dayStatusClass.declaredConstructors.first { it.parameterCount == 3 }.apply { isAccessible = true }
            .newInstance("Flag", 0L, 0L)

        assertTrue(formattedDate != "2026-06-15")
        assertTrue(formattedDayName.isNotBlank())
        assertTrue(formattedFullDate.contains("2026"))
        assertTrue(formattedTime.contains("07:05"))
        assertNotNull(accent)
        assertNotNull(dayStatus)
    }

    @Test
    fun calendarCells_shiftOffset_forMonthsThatDoNotStartOnMonday() {
        val month = YearMonth.of(2026, 2)
        val cells = invokeStatic("rememberCalendarCells", month, YearMonth::class.java) as List<*>

        assertEquals(42, cells.size)
        assertEquals(null, cells[0])
        assertEquals(null, cells[5])
        assertEquals(LocalDate.parse("2026-02-01"), cells[6])
        assertEquals(LocalDate.parse("2026-02-28"), cells.filterNotNull().last())
    }

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = historyScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private companion object {
        val historyScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.HistoryScreenKt")
    }
}
