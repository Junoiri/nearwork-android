package com.example.nearworkthesis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = DataStoreSettingsStore(context)

    @Test
    fun setExtremeCloseThresholdCm_clampsToCloseDistanceMinusOne() = runTest {
        store.setCloseDistanceThresholdCm(30)
        store.setExtremeCloseThresholdCm(45)

        assertEquals(30, store.observeCloseDistanceThresholdCm().first())
        assertEquals(29, store.observeExtremeCloseThresholdCm().first())
    }

    @Test
    fun setCloseDistanceThresholdCm_clampsExistingExtremeCloseBelowIt() = runTest {
        store.setCloseDistanceThresholdCm(30)
        store.setExtremeCloseThresholdCm(20)

        store.setCloseDistanceThresholdCm(18)

        assertEquals(18, store.observeCloseDistanceThresholdCm().first())
        assertEquals(17, store.observeExtremeCloseThresholdCm().first())
    }

    @Test
    fun setLowLightThresholdLux_clampsToAllowedRange() = runTest {
        store.setLowLightThresholdLux(-5)
        assertEquals(0, store.observeLowLightThresholdLux().first())

        store.setLowLightThresholdLux(60_000)
        assertEquals(50_000, store.observeLowLightThresholdLux().first())
    }

    @Test
    fun setDailyReminderTimeLocal_normalizesValidAndFallsBackOnInvalidInput() = runTest {
        store.setDailyReminderTimeLocal("07:05")
        assertEquals("07:05", store.observeDailyReminderTimeLocal().first())

        store.setDailyReminderTimeLocal("25:61")
        assertEquals(DataStoreSettingsStore.Defaults.dailyReminderTimeLocal, store.observeDailyReminderTimeLocal().first())
    }

    @Test
    fun setLastDemoProfileId_canStoreAndClearValue() = runTest {
        store.setLastDemoProfileId(42L)
        assertEquals(42L, store.observeLastDemoProfileId().first())

        store.setLastDemoProfileId(null)
        assertNull(store.observeLastDemoProfileId().first())
    }

    @Test
    fun setDuplicateResolutionPolicy_roundTripsStorageValue() = runTest {
        store.setDuplicateResolutionPolicy(DuplicateResolutionPolicy.REPLACE_WITH_NEW)

        assertEquals(
            DuplicateResolutionPolicy.REPLACE_WITH_NEW,
            store.observeDuplicateResolutionPolicy().first()
        )
    }

    @Test
    fun setAlsSpikeThresholdLux_clampsToAllowedRange() = runTest {
        store.setAlsSpikeThresholdLux(-1.0)
        assertEquals(0.0, store.observeAlsSpikeThresholdLux().first(), 0.0)

        store.setAlsSpikeThresholdLux(60_000.0)
        assertEquals(50_000.0, store.observeAlsSpikeThresholdLux().first(), 0.0)
    }

    @Test
    fun setNearworkDistanceThresholdCm_clampsToAllowedRange() = runTest {
        store.setNearworkDistanceThresholdCm(5)
        assertEquals(10, store.observeNearworkDistanceThresholdCm().first())

        store.setNearworkDistanceThresholdCm(250)
        assertEquals(200, store.observeNearworkDistanceThresholdCm().first())
    }

    @Test
    fun setBreakGapSeconds_clampsToAllowedRange() = runTest {
        store.setBreakGapSeconds(0)
        assertEquals(1, store.observeBreakGapSeconds().first())

        store.setBreakGapSeconds(4_000)
        assertEquals(3_600, store.observeBreakGapSeconds().first())
    }

    @Test
    fun setMinSessionDurationSeconds_clampsToAllowedRange() = runTest {
        store.setMinSessionDurationSeconds(0)
        assertEquals(1, store.observeMinSessionDurationSeconds().first())

        store.setMinSessionDurationSeconds(100_000)
        assertEquals(86_400, store.observeMinSessionDurationSeconds().first())
    }

    @Test
    fun setShowDebugOverlay_roundTripsBooleanValue() = runTest {
        store.setShowDebugOverlay(true)
        assertEquals(true, store.observeShowDebugOverlay().first())

        store.setShowDebugOverlay(false)
        assertEquals(false, store.observeShowDebugOverlay().first())
    }

    @Test
    fun reminderAndNotificationFlags_roundTripBooleanValues() = runTest {
        store.setDailyReminderEnabled(true)
        store.setPostImportNotificationEnabled(true)

        assertEquals(true, store.observeDailyReminderEnabled().first())
        assertEquals(true, store.observePostImportNotificationEnabled().first())

        store.setDailyReminderEnabled(false)
        store.setPostImportNotificationEnabled(false)

        assertEquals(false, store.observeDailyReminderEnabled().first())
        assertEquals(false, store.observePostImportNotificationEnabled().first())
    }

    @Test
    fun setReplaceAlsSingleSampleSpikes_roundTripsBooleanValue() = runTest {
        store.setReplaceAlsSingleSampleSpikes(false)
        assertEquals(false, store.observeReplaceAlsSingleSampleSpikes().first())

        store.setReplaceAlsSingleSampleSpikes(true)
        assertEquals(true, store.observeReplaceAlsSingleSampleSpikes().first())
    }
}
