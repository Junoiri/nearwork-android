package com.example.nearworkthesis.importing.howfar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidHowfarUf2ArchiveTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun loadLatest_withoutSavedSnapshot_returnsNull() = runBlocking {
        clearArchiveDir()
        val archive = AndroidHowfarUf2Archive(appContext)

        val snapshot = archive.loadLatest(profileId = 10L)

        assertNull(snapshot)
    }

    @Test
    fun saveLatest_persistsBytesFilenameAndTimestamp() = runBlocking {
        clearArchiveDir()
        val archive = AndroidHowfarUf2Archive(appContext)
        val bytes = byteArrayOf(1, 2, 3, 4)

        archive.saveLatest(profileId = 11L, filename = "device.uf2", bytes = bytes)

        val snapshot = archive.loadLatest(profileId = 11L)
        assertNotNull(snapshot)
        assertEquals("device.uf2", snapshot?.filename)
        assertArrayEquals(bytes, snapshot?.bytes)
        assertTrue((snapshot?.savedAtEpochMillis ?: 0L) > 0L)
    }

    @Test
    fun loadLatest_withoutMetadata_usesDefaultFilenameAndFileTimestamp() = runBlocking {
        clearArchiveDir()
        val archive = AndroidHowfarUf2Archive(appContext)
        val dir = appContext.filesDir.resolve("howfar").apply { mkdirs() }
        val dataFile = dir.resolve("howfar_12_latest.uf2")
        val expectedTime = 54_321L
        dataFile.writeBytes(byteArrayOf(9, 8, 7))
        dataFile.setLastModified(expectedTime)

        val snapshot = archive.loadLatest(profileId = 12L)

        assertNotNull(snapshot)
        assertEquals("howfar_latest.uf2", snapshot?.filename)
        assertArrayEquals(byteArrayOf(9, 8, 7), snapshot?.bytes)
        assertEquals(expectedTime, snapshot?.savedAtEpochMillis)
    }

    @Test
    fun saveLatest_keepsProfilesIsolated() = runBlocking {
        clearArchiveDir()
        val archive = AndroidHowfarUf2Archive(appContext)

        archive.saveLatest(profileId = 1L, filename = "one.uf2", bytes = byteArrayOf(1))
        archive.saveLatest(profileId = 2L, filename = "two.uf2", bytes = byteArrayOf(2, 2))

        val first = archive.loadLatest(profileId = 1L)
        val second = archive.loadLatest(profileId = 2L)

        assertEquals("one.uf2", first?.filename)
        assertArrayEquals(byteArrayOf(1), first?.bytes)
        assertEquals("two.uf2", second?.filename)
        assertArrayEquals(byteArrayOf(2, 2), second?.bytes)
    }

    private fun clearArchiveDir() {
        appContext.filesDir.resolve("howfar").deleteRecursively()
    }
}
