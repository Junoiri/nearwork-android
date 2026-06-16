package com.example.nearworkthesis.feature

import android.net.Uri
import android.app.Application
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.ImportResult
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.settings.SettingsStore
import com.example.nearworkthesis.settings.SettingsDefaults
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsScreenHelpersTest {

    @Test
    fun importResultAndReminderHelpers_formatStableMessages() {
        val summary = ImportSummary(
            filename = "demo.csv",
            totalRows = 1,
            insertedRows = 1,
            rejectedRows = 0,
            firstTimestampEpochMillis = null,
            lastTimestampEpochMillis = null
        )
        assertEquals("Demo imported: demo.csv", invokeImportMessage(ImportResult.Success(summary), "demo.csv"))
        assertEquals("No new data (already imported): demo.csv", invokeImportMessage(ImportResult.NoNewData(summary), "demo.csv"))
        assertEquals("Import failed: boom", invokeImportMessage(ImportResult.Error("boom"), "demo.csv"))
        assertEquals("12.3", invokeStatic("format1", 12.34, Double::class.javaPrimitiveType!!) as String)
        assertEquals("12.35", invokeStatic("format2", 12.345, Double::class.javaPrimitiveType!!) as String)
        assertEquals(LocalTime.parse("07:05"), invokeStatic("parseReminderTime", "07:05", String::class.java))
        assertEquals(
            LocalTime.parse(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL),
            invokeStatic("parseReminderTime", "99:99", String::class.java)
        )
        assertEquals("07:05", invokeStatic("formatReminderLabel", "07:05", String::class.java))
        assertEquals(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL, invokeStatic("formatReminderLabel", "bad", String::class.java))
    }

    @Test
    fun settingsReadWriteHelpers_roundTripTextToFileUri() {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("nearwork-settings", ".json")
        val uri = Uri.fromFile(file)
        val payload = """{"duplicateResolutionPolicy":"${DuplicateResolutionPolicy.KEEP_EXISTING.name}"}"""

        val writeResult = invokeStatic("writeSettingsTextToUri", context, uri, payload, android.content.Context::class.java, Uri::class.java, String::class.java)
        val readResult = invokeStatic("readSettingsTextFromUri", context, uri, android.content.Context::class.java, Uri::class.java) as String

        assertEquals(Unit, writeResult)
        assertEquals(payload, readResult)
        assertTrue(file.readText(Charsets.UTF_8).contains(DuplicateResolutionPolicy.KEEP_EXISTING.name))
    }

    @Test
    @Config(sdk = [32])
    fun notificationPermissionHelper_preTiramisu_returnsFalse() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(invokeStatic("needsNotificationPermission", context, android.content.Context::class.java) as Boolean)
    }

    @Test
    @Config(sdk = [34])
    fun notificationPermissionHelper_tiramisuPlus_reflectsPermissionState() {
        val context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(invokeStatic("needsNotificationPermission", context, android.content.Context::class.java) as Boolean)
    }

    @Test
    fun fakeSettingsStore_updatesAllObservedValues() = runTest {
        val store = Class.forName("com.example.nearworkthesis.feature.FakeSettingsStore")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as SettingsStore

        assertEquals(SettingsDefaults.LOW_LIGHT_THRESHOLD_LUX, store.observeLowLightThresholdLux().first())
        assertEquals(SettingsDefaults.DAILY_REMINDER_TIME_LOCAL, store.observeDailyReminderTimeLocal().first())
        assertNull(store.observeLastDemoProfileId().first())

        store.setLowLightThresholdLux(42)
        store.setNearworkDistanceThresholdCm(55)
        store.setBreakGapSeconds(99)
        store.setMinSessionDurationSeconds(12)
        store.setCloseDistanceThresholdCm(22)
        store.setExtremeCloseThresholdCm(14)
        store.setReplaceAlsSingleSampleSpikes(false)
        store.setAlsSpikeThresholdLux(123.5)
        store.setShowDebugOverlay(true)
        store.setLastDemoProfileId(7L)
        store.setDailyReminderEnabled(false)
        store.setDailyReminderTimeLocal("08:45")
        store.setPostImportNotificationEnabled(false)
        store.setDuplicateResolutionPolicy(DuplicateResolutionPolicy.REPLACE_WITH_NEW)

        assertEquals(42, store.observeLowLightThresholdLux().first())
        assertEquals(55, store.observeNearworkDistanceThresholdCm().first())
        assertEquals(99, store.observeBreakGapSeconds().first())
        assertEquals(12, store.observeMinSessionDurationSeconds().first())
        assertEquals(22, store.observeCloseDistanceThresholdCm().first())
        assertEquals(14, store.observeExtremeCloseThresholdCm().first())
        assertFalse(store.observeReplaceAlsSingleSampleSpikes().first())
        assertEquals(123.5, store.observeAlsSpikeThresholdLux().first(), 0.0)
        assertTrue(store.observeShowDebugOverlay().first())
        assertEquals(7L, store.observeLastDemoProfileId().first())
        assertFalse(store.observeDailyReminderEnabled().first())
        assertEquals("08:45", store.observeDailyReminderTimeLocal().first())
        assertFalse(store.observePostImportNotificationEnabled().first())
        assertEquals(DuplicateResolutionPolicy.REPLACE_WITH_NEW, store.observeDuplicateResolutionPolicy().first())
    }

    @Test
    fun fakeNotificationScheduler_acceptsAllOperations() = runTest {
        val scheduler = Class.forName("com.example.nearworkthesis.feature.FakeNotificationScheduler")
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as NotificationScheduler

        scheduler.ensureChannels()
        scheduler.rescheduleDailyReminder()
        scheduler.cancelDailyReminder()
        scheduler.enqueuePostImportSummary(
            ImportSummary(
                filename = "usb.uf2",
                totalRows = 2,
                insertedRows = 2,
                rejectedRows = 0,
                firstTimestampEpochMillis = 1L,
                lastTimestampEpochMillis = 2L
            )
        )
    }

    @Test
    fun settingsScreenValueObjects_constructViaReflection() {
        val targetClass = Class.forName("com.example.nearworkthesis.feature.NotificationToggleTarget")
        val daily = java.lang.Enum.valueOf(targetClass.asSubclass(Enum::class.java), "DailyReminder")
        val snapshotClass = Class.forName("com.example.nearworkthesis.feature.SettingsSnapshot")
        val snapshot = snapshotClass.declaredConstructors.first { it.parameterCount == 13 }.apply { isAccessible = true }
            .newInstance(1, 2, 3, 4, 5, 6, true, 7.5, false, true, "07:30", false, DuplicateResolutionPolicy.KEEP_EXISTING)
        val accentClass = Class.forName("com.example.nearworkthesis.feature.SettingsAccentColors")
        val accent = accentClass.declaredConstructors.first { it.parameterCount == 2 }.apply { isAccessible = true }
            .newInstance(0L, 0L)

        assertEquals("DailyReminder", (daily as Enum<*>).name)
        assertNotNull(snapshot)
        assertNotNull(accent)
        assertEquals(2, targetClass.enumConstants.size)
    }

    private fun invokeImportMessage(result: ImportResult, filename: String): String {
        val method = settingsScreenClass.getDeclaredMethod("toUserMessage", ImportResult::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(null, result, filename) as String
    }

    private fun invokeStatic(name: String, arg: Any?, type: Class<*>): Any? {
        val method = settingsScreenClass.getDeclaredMethod(name, type)
        method.isAccessible = true
        return method.invoke(null, arg)
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, type1: Class<*>, type2: Class<*>): Any? {
        val method = settingsScreenClass.getDeclaredMethod(name, type1, type2)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2)
    }

    private fun invokeStatic(name: String, arg1: Any?, arg2: Any?, arg3: Any?, type1: Class<*>, type2: Class<*>, type3: Class<*>): Any? {
        val method = settingsScreenClass.getDeclaredMethod(name, type1, type2, type3)
        method.isAccessible = true
        return method.invoke(null, arg1, arg2, arg3)
    }

    private companion object {
        val settingsScreenClass: Class<*> = Class.forName("com.example.nearworkthesis.feature.SettingsScreenKt")
    }
}
