package com.example.nearworkthesis.feature

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExportScreenHelpersTest {

    @Test
    fun formatDayShortAndLong_formatIsoDays_andFallBackForInvalidInput() {
        val shortValue = invokeStringHelper("formatDayShort", "2026-06-15")
        val longValue = invokeStringHelper("formatDayLong", "2026-06-15")

        assertTrue(shortValue.contains("2026") || shortValue.contains("Jun") || shortValue.contains("cze"))
        assertTrue(longValue.contains("2026"))
        assertEquals("not-a-day", invokeStringHelper("formatDayShort", "not-a-day"))
        assertEquals("not-a-day", invokeStringHelper("formatDayLong", "not-a-day"))
    }

    @Test
    fun writeTextToUri_andWriteBytesToUri_persistPayloads() {
        val context = RuntimeEnvironment.getApplication()
        val textFile = File.createTempFile("nearwork-export-text", ".csv")
        val bytesFile = File.createTempFile("nearwork-export-binary", ".bin")
        val text = "header,value\n2026-06-15,1\n"
        val bytes = byteArrayOf(1, 2, 3, 4)

        val textResult = invokeWriteText(Uri.fromFile(textFile), text, context)
        val bytesResult = invokeWriteBytes(Uri.fromFile(bytesFile), bytes, context)

        assertEquals(kotlin.Unit, textResult)
        assertEquals(kotlin.Unit, bytesResult)
        assertEquals(text, textFile.readText(Charsets.UTF_8))
        assertTrue(bytes.contentEquals(bytesFile.readBytes()))
    }

    private fun invokeStringHelper(methodName: String, value: String): String {
        val method = exportScreenHelpersClass.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        return method.invoke(null, value) as String
    }

    private fun invokeWriteText(uri: Uri, text: String, context: android.content.Context): Any? {
        val method = exportScreenHelpersClass.getDeclaredMethod(
            "writeTextToUri",
            android.content.Context::class.java,
            Uri::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, context, uri, text)
    }

    private fun invokeWriteBytes(uri: Uri, bytes: ByteArray, context: android.content.Context): Any? {
        val method = exportScreenHelpersClass.getDeclaredMethod(
            "writeBytesToUri",
            android.content.Context::class.java,
            Uri::class.java,
            ByteArray::class.java
        )
        method.isAccessible = true
        return method.invoke(null, context, uri, bytes)
    }

    private companion object {
        val exportScreenHelpersClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.ExportScreenKt")
    }
}
