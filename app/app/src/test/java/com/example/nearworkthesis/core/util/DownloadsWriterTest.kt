package com.example.nearworkthesis.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DownloadsWriterTest {

    @Test
    @Config(sdk = [28])
    fun writeBytesToDownloads_preQ_throwsHelpfulError() {
        val error = runCatching {
            writeBytesToDownloads(RuntimeEnvironment.getApplication(), "demo.csv", "text/csv", "x".toByteArray())
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Downloads export requires Android 10 or newer.", error?.message)
    }
}
