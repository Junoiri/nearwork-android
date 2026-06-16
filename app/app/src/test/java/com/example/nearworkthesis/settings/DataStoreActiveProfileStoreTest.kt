package com.example.nearworkthesis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreActiveProfileStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun observeAndSetActiveProfileId_roundTripValue() = runTest {
        val store = DataStoreActiveProfileStore(context)

        assertNull(store.observeActiveProfileId().first())

        store.setActiveProfileId(42L)

        assertEquals(42L, store.observeActiveProfileId().first())
    }
}
