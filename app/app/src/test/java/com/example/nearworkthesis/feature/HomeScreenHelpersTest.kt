package com.example.nearworkthesis.feature

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeScreenHelpersTest {

    @Test
    fun homeHelpers_formatTimestampAndDashboardValues() {
        val epochMillis = 1_718_431_200_000L
        val expected = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

        assertEquals(expected, invokeStatic("formatTimestamp", epochMillis, Long::class.javaPrimitiveType!!) as String)
        assertEquals("12.35", invokeStatic("formatDashboardDouble", 12.345, Double::class.javaPrimitiveType!!) as String)
    }

    @Test
    fun homeAccentValueObject_constructsViaReflection() {
        val accentClass = Class.forName("com.example.nearworkthesis.feature.HomeScreenAccentColors")
        val accent = accentClass.declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
            .newInstance(0L, 0L)

        assertNotNull(accent)
    }

    private fun invokeStatic(name: String, arg: Any, type: Class<*>): Any? {
        val method = homeScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private companion object {
        val homeScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.HomeScreenKt")
    }
}
