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
class DeviceConfigScreenHelpersTest {

    @Test
    fun writeBytesToUri_persistsBinaryPayload() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("nearwork-device-config", ".uf2")
        val bytes = byteArrayOf(9, 8, 7, 6)

        val result = invokeStatic(
            "writeBytesToUri",
            context,
            Uri.fromFile(file),
            bytes,
            android.content.Context::class.java,
            Uri::class.java,
            ByteArray::class.java
        )

        assertEquals(Unit, result)
        assertTrue(bytes.contentEquals(file.readBytes()))
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, arg3: Any?, type1: Class<*>, type2: Class<*>, type3: Class<*>): Any? {
        val method = deviceConfigScreenClass.getDeclaredMethod(name, type1, type2, type3)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2, arg3)
    }

    private companion object {
        val deviceConfigScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.DeviceConfigScreenKt")
    }
}
