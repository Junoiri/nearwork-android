package com.example.nearworkthesis.importing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class ImportFileUtilsTest {

    private val appContext: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ShadowContentResolver.reset()
    }

    @Test
    fun queryDisplayName_returnsDisplayNameWhenColumnExists() {
        registerQueryProvider(
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf("samples.csv"))
            }
        )

        val name = queryDisplayName(appContext, TEST_URI)

        assertEquals("samples.csv", name)
    }

    @Test
    fun queryDisplayName_returnsNullWhenCursorHasNoRows() {
        registerQueryProvider(MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)))

        val name = queryDisplayName(appContext, TEST_URI)

        assertNull(name)
    }

    @Test
    fun queryDisplayName_returnsNullWhenResolverThrows() {
        registerQueryProvider(queryError = IllegalStateException("broken"))

        val name = queryDisplayName(appContext, TEST_URI)

        assertNull(name)
    }

    @Test
    fun readAllBytesWithLimit_readsWholeStreamWithinLimit() {
        val bytes = "abcdef".toByteArray()
        shadowOf(appContext.contentResolver).registerInputStream(TEST_URI, ByteArrayInputStream(bytes))

        val loaded = readAllBytesWithLimit(appContext, TEST_URI, maxBytes = 6)

        assertArrayEquals(bytes, loaded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readAllBytesWithLimit_rejectsNonPositiveLimit() {
        readAllBytesWithLimit(appContext, TEST_URI, 0)
    }

    @Test
    fun readAllBytesWithLimit_throwsWhenFileExceedsLimit() {
        shadowOf(appContext.contentResolver).registerInputStream(
            TEST_URI,
            ByteArrayInputStream(ByteArray(9) { it.toByte() })
        )

        val error = kotlin.runCatching {
            readAllBytesWithLimit(appContext, TEST_URI, maxBytes = 8)
        }.exceptionOrNull()

        assertEquals("File too large.", error?.message)
    }

    private fun registerQueryProvider(
        cursor: Cursor? = null,
        queryError: Throwable? = null
    ) {
        val provider = TestQueryProvider(cursor, queryError)
        provider.attachInfo(
            appContext,
            ProviderInfo().apply { authority = TEST_AUTHORITY }
        )
        ShadowContentResolver.registerProviderInternal(TEST_AUTHORITY, provider)
    }

    private class TestQueryProvider(
        private val cursor: Cursor?,
        private val queryError: Throwable?
    ) : ContentProvider() {

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            if (queryError != null) throw queryError
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val TEST_AUTHORITY = "com.example.nearworkthesis.test.importfileutils"
        val TEST_URI: Uri = Uri.parse("content://$TEST_AUTHORITY/file")
    }
}
