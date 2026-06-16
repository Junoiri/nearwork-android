package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.analysis.NearworkSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataAnalysisScreenHelpersTest {

    @Test
    fun formattingHelpers_ticks_and_zoomSpacing_behaveAsExpected() {
        assertEquals("--", invokeStatic("format1OrDash", null, Double::class.javaObjectType) as String)
        assertEquals("12.3", invokeStatic("format1OrDash", 12.34, Double::class.javaObjectType) as String)
        assertEquals("59m", invokeStatic("formatDuration", 3_540L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("2h 5m", invokeStatic("formatDuration", 7_500L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("1h 0m", invokeStatic("formatDuration", 3_600L, Long::class.javaPrimitiveType!!) as String)
        assertEquals("not-a-day", invokeStatic("formatDate", "not-a-day", String::class.java) as String)
        assertEquals("00:00", invokeStatic("formatHm", 0L, Long::class.javaPrimitiveType!!) as String)

        val ticks = invokeStatic("computeTimeTicks", 65_000L, 3_700_000L, 30, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!) as List<*>
        val singleTick = invokeStatic("computeTimeTicks", 10_000L, 5_000L, 15, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!) as List<*>
        assertTrue(ticks.isNotEmpty())
        assertEquals(0L, ticks.first())
        assertEquals(3_700_000L, ticks.last())
        assertEquals(listOf(0L, 5_000L), singleTick)
        assertEquals(60, invokeStatic("zoomLevelToMinuteStep", -1, Int::class.javaPrimitiveType!!) as Int)
        assertEquals(60, invokeStatic("zoomLevelToMinuteStep", 1, Int::class.javaPrimitiveType!!) as Int)
        assertEquals(1, invokeStatic("zoomLevelToMinuteStep", 20, Int::class.javaPrimitiveType!!) as Int)

    }

    @Test
    fun gapAndDownsampleHelpers_preserveSignal_andMergeIntervals() {
        val samples = listOf(
            NearworkSample(0L, 10.0, 100.0),
            NearworkSample(1_000L, 20.0, 200.0),
            NearworkSample(2_000L, 30.0, 300.0),
            NearworkSample(9_000L, 40.0, 400.0),
            NearworkSample(10_000L, 50.0, 500.0)
        )

        val downsampled = invokeStatic("downsampleByTimeBucket", samples, 2, List::class.java, Int::class.javaPrimitiveType!!) as List<*>
        val gaps = invokeStatic("computeGapIntervals", samples, 3_000L, List::class.java, Long::class.javaPrimitiveType!!) as List<*>
        val merged = invokeStatic(
            "mergeGapIntervals",
            listOf(
                newGapInterval(1_000L, 5_000L),
                newGapInterval(4_500L, 8_000L),
                newGapInterval(10_000L, 12_000L)
            ),
            List::class.java
        ) as List<*>

        assertEquals(2, downsampled.size)
        assertEquals(1, gaps.size)
        assertEquals(2, merged.size)
        assertTrue((invokeStatic("mergeGapIntervals", emptyList<Any>(), List::class.java) as List<*>).isEmpty())
        assertTrue((invokeStatic("computeGapIntervals", listOf(samples.first()), 3_000L, List::class.java, Long::class.javaPrimitiveType!!) as List<*>).isEmpty())
        assertFalse(invokeStatic("hasGapBetween", 1L, 2L, emptyList<Any>(), Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, List::class.java) as Boolean)
        assertFalse(invokeStatic("hasGapBetween", 2_000L, 7_000L, merged, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, List::class.java) as Boolean)
        assertTrue(invokeStatic("hasGapBetween", 7_500L, 8_500L, merged, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, List::class.java) as Boolean)
        assertFalse(invokeStatic("hasGapBetween", 8_100L, 9_900L, merged, Long::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, List::class.java) as Boolean)
    }

    @Test
    fun validFormatting_andScreenValueObjects_areCovered() {
        val formattedDate = invokeStatic("formatDate", "2026-06-15", String::class.java) as String
        val formattedHm = invokeStatic("formatHm", 65_000L, Long::class.javaPrimitiveType!!) as String
        val gap = newGapInterval(100L, 200L)

        assertTrue(formattedDate != "2026-06-15")
        assertEquals("00:01", formattedHm)
        assertNotNull(gap)
    }

    private fun newGapInterval(start: Long, end: Long): Any {
        val cls = Class.forName("com.example.nearworkthesis.feature.GapInterval")
        val ctor = cls.getDeclaredConstructor(Long::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        ctor.isAccessible = true
        return ctor.newInstance(start, end)
    }

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = dataAnalysisScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, type1: Class<*>, type2: Class<*>): Any? {
        val method = dataAnalysisScreenClass.getDeclaredMethod(name, type1, type2)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2)
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, arg3: Any?, type1: Class<*>, type2: Class<*>, type3: Class<*>): Any? {
        val method = dataAnalysisScreenClass.getDeclaredMethod(name, type1, type2, type3)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2, arg3)
    }

    private companion object {
        val dataAnalysisScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.DataAnalysisScreenKt")
    }
}
