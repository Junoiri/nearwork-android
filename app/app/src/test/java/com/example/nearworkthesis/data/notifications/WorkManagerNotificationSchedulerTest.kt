package com.example.nearworkthesis.data.notifications

import com.example.nearworkthesis.domain.ImportGap
import com.example.nearworkthesis.domain.ImportSourceType
import com.example.nearworkthesis.domain.ImportSummary
import com.example.nearworkthesis.domain.DuplicateResolutionPolicy
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsStore
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerNotificationSchedulerTest {

    @Test
    fun ensureChannels_invokesInitializer() {
        var calls = 0
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "08:30"),
            activeProfileStore = FakeActiveProfileStore(1L),
            profileRepository = FakeProfileRepository(timezoneId = "UTC"),
            workController = FakeWorkController(),
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = { calls += 1 }
        )

        scheduler.ensureChannels()

        assertEquals(1, calls)
    }

    @Test
    fun rescheduleDailyReminder_disabledCancels() = runTest {
        val settings = FakeSettingsStore(dailyReminderEnabled = false, dailyReminderTime = "07:00")
        val activeProfile = FakeActiveProfileStore(1L)
        val profiles = FakeProfileRepository(timezoneId = "UTC")
        val controller = FakeWorkController()
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = settings,
            activeProfileStore = activeProfile,
            profileRepository = profiles,
            workController = controller,
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = {}
        )

        scheduler.rescheduleDailyReminder()

        assertEquals(1, controller.cancelCount)
        assertTrue(controller.scheduleCalls.isEmpty())
    }

    @Test
    fun rescheduleDailyReminder_enabledSchedulesWithProfileTimezone() = runTest {
        val settings = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "08:30")
        val activeProfile = FakeActiveProfileStore(2L)
        val profiles = FakeProfileRepository(timezoneId = "Europe/Paris")
        val controller = FakeWorkController()
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = settings,
            activeProfileStore = activeProfile,
            profileRepository = profiles,
            workController = controller,
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = {}
        )

        scheduler.rescheduleDailyReminder()

        assertEquals(0, controller.cancelCount)
        assertEquals(1, controller.scheduleCalls.size)
        val call = controller.scheduleCalls.single()
        assertEquals("08:30", call.timeLocal)
        assertEquals(ZoneId.of("Europe/Paris"), call.zoneId)
    }

    @Test
    fun rescheduleDailyReminder_missingActiveProfile_doesNothing() = runTest {
        val controller = FakeWorkController()
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "08:30"),
            activeProfileStore = FakeActiveProfileStore(null),
            profileRepository = FakeProfileRepository(timezoneId = "UTC"),
            workController = controller,
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = {}
        )

        scheduler.rescheduleDailyReminder()

        assertTrue(controller.scheduleCalls.isEmpty())
        assertEquals(0, controller.cancelCount)
    }

    @Test
    fun rescheduleDailyReminder_invalidTimezone_fallsBackToSystemDefault() = runTest {
        val controller = FakeWorkController()
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "06:45"),
            activeProfileStore = FakeActiveProfileStore(5L),
            profileRepository = FakeProfileRepository(timezoneId = "bad/tz"),
            workController = controller,
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = {}
        )

        scheduler.rescheduleDailyReminder()

        assertEquals(1, controller.scheduleCalls.size)
        assertEquals(ZoneId.systemDefault(), controller.scheduleCalls.single().zoneId)
    }

    @Test
    fun enqueuePostImportSummary_respectsPermissionAndSettings() = runTest {
        val settings = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "08:30")
        val activeProfile = FakeActiveProfileStore(1L)
        val profiles = FakeProfileRepository(timezoneId = "UTC")
        val controller = FakeWorkController()
        val permission = FakePermissionChecker(false)
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = settings,
            activeProfileStore = activeProfile,
            profileRepository = profiles,
            workController = controller,
            permissionChecker = permission,
            channelInitializer = {}
        )

        val summary = ImportSummary(
            filename = "demo.csv",
            sourceType = ImportSourceType.FILE,
            totalRows = 10,
            insertedRows = 8,
            rejectedRows = 2,
            invalidTimestampCount = 0,
            invalidDistanceCount = 0,
            invalidLuxCount = 0,
            duplicatesRemovedCount = 0,
            gapCount = 0,
            largestGapDurationMillis = null,
            gaps = emptyList<ImportGap>(),
            firstTimestampEpochMillis = 1_735_000_000_000,
            lastTimestampEpochMillis = 1_735_086_400_000,
            timezoneId = "UTC"
        )

        // Post-import notifications disabled -> no work
        settings.postImportEnabled.value = false
        scheduler.enqueuePostImportSummary(summary)
        assertTrue(controller.postImportCalls.isEmpty())

        // Enabled but permission denied -> still no work
        settings.postImportEnabled.value = true
        scheduler.enqueuePostImportSummary(summary)
        assertTrue(controller.postImportCalls.isEmpty())

        // Enabled and permission granted -> enqueues work with derived days
        permission.allowed = true
        scheduler.enqueuePostImportSummary(summary)
        assertEquals(1, controller.postImportCalls.size)
        val call = controller.postImportCalls.single()
        assertEquals("2024-12-24", call.firstDay)
        assertEquals("2024-12-25", call.lastDay)
        assertEquals("2024-12-25", call.openDay)
        assertEquals(summary.insertedRows, call.summary.insertedRows)
    }

    @Test
    fun enqueuePostImportSummary_blankTimezone_andSingleDayFallback_useSystemDefault() = runTest {
        val controller = FakeWorkController()
        val scheduler = WorkManagerNotificationScheduler(
            context = RuntimeEnvironment.getApplication(),
            settingsStore = FakeSettingsStore(dailyReminderEnabled = true, dailyReminderTime = "08:30"),
            activeProfileStore = FakeActiveProfileStore(1L),
            profileRepository = FakeProfileRepository(timezoneId = "UTC"),
            workController = controller,
            permissionChecker = FakePermissionChecker(true),
            channelInitializer = {}
        )

        val summary = ImportSummary(
            filename = "single.csv",
            sourceType = ImportSourceType.FILE,
            totalRows = 3,
            insertedRows = 3,
            rejectedRows = 0,
            firstTimestampEpochMillis = 1_735_000_000_000,
            lastTimestampEpochMillis = null,
            timezoneId = ""
        )

        scheduler.enqueuePostImportSummary(summary)

        assertEquals(1, controller.postImportCalls.size)
        val call = controller.postImportCalls.single()
        assertEquals(call.firstDay, call.openDay)
        assertEquals(null, call.lastDay)
    }
}

// region fakes

private class FakeSettingsStore(
    dailyReminderEnabled: Boolean,
    dailyReminderTime: String
) : SettingsStore {
    val dailyReminderEnabledFlow = MutableStateFlow(dailyReminderEnabled)
    val dailyReminderTimeFlow = MutableStateFlow(dailyReminderTime)
    val postImportEnabled = MutableStateFlow(true)

    override fun observeLowLightThresholdLux(): Flow<Int> = MutableStateFlow(0)
    override suspend fun setLowLightThresholdLux(lux: Int) = Unit
    override fun observeNearworkDistanceThresholdCm(): Flow<Int> = MutableStateFlow(0)
    override suspend fun setNearworkDistanceThresholdCm(value: Int) = Unit
    override fun observeBreakGapSeconds(): Flow<Int> = MutableStateFlow(0)
    override suspend fun setBreakGapSeconds(value: Int) = Unit
    override fun observeMinSessionDurationSeconds(): Flow<Int> = MutableStateFlow(0)
    override suspend fun setMinSessionDurationSeconds(value: Int) = Unit
    override fun observeCloseDistanceThresholdCm(): Flow<Int> = MutableStateFlow(30)
    override suspend fun setCloseDistanceThresholdCm(value: Int) = Unit
    override fun observeExtremeCloseThresholdCm(): Flow<Int> = MutableStateFlow(20)
    override suspend fun setExtremeCloseThresholdCm(value: Int) = Unit
    override fun observeReplaceAlsSingleSampleSpikes(): Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setReplaceAlsSingleSampleSpikes(enabled: Boolean) = Unit
    override fun observeAlsSpikeThresholdLux(): Flow<Double> = MutableStateFlow(300.0)
    override suspend fun setAlsSpikeThresholdLux(value: Double) = Unit
    override fun observeShowDebugOverlay(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setShowDebugOverlay(enabled: Boolean) = Unit
    override fun observeLastDemoProfileId(): Flow<Long?> = MutableStateFlow(null)
    override suspend fun setLastDemoProfileId(profileId: Long?) = Unit
    override fun observeDailyReminderEnabled(): Flow<Boolean> = dailyReminderEnabledFlow
    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dailyReminderEnabledFlow.value = enabled
    }
    override fun observeDailyReminderTimeLocal(): Flow<String> = dailyReminderTimeFlow
    override suspend fun setDailyReminderTimeLocal(value: String) {
        dailyReminderTimeFlow.value = value
    }
    override fun observePostImportNotificationEnabled(): Flow<Boolean> = postImportEnabled
    override suspend fun setPostImportNotificationEnabled(enabled: Boolean) {
        postImportEnabled.value = enabled
    }
    override fun observeDuplicateResolutionPolicy(): Flow<DuplicateResolutionPolicy> = MutableStateFlow(DuplicateResolutionPolicy.KEEP_EXISTING)
    override suspend fun setDuplicateResolutionPolicy(policy: DuplicateResolutionPolicy) = Unit
}

private class FakeActiveProfileStore(initial: Long?) : ActiveProfileStore {
    val id = MutableStateFlow(initial)
    override fun observeActiveProfileId(): Flow<Long?> = id
    override suspend fun setActiveProfileId(id: Long) {
        this.id.value = id
    }
}

private class FakeProfileRepository(
    private val timezoneId: String
) : ProfileRepository {
    override suspend fun getProfiles(): List<Profile> = emptyList()
    override fun observeProfiles(): Flow<List<Profile>> = emptyFlow()
    override suspend fun getProfile(profileId: Long): Profile? {
        return Profile(id = profileId, name = "Test", createdAtEpochMillis = 0, timezoneId = timezoneId, dateOfBirth = null)
    }
    override suspend fun upsertProfile(profile: Profile): Long = profile.id
    override suspend fun insertProfile(name: String, createdAtEpochMillis: Long, dateOfBirth: String?): Long = 0
    override suspend fun renameProfile(profileId: Long, name: String) = Unit
    override suspend fun deleteProfile(profileId: Long) = Unit
    override suspend fun countMeasurements(profileId: Long): Int = 0
}

private class FakeWorkController : NotificationWorkController {
    data class DailyCall(val timeLocal: String, val zoneId: ZoneId)
    data class PostImportCall(val summary: ImportSummary, val firstDay: String?, val lastDay: String?, val openDay: String?)

    val scheduleCalls = mutableListOf<DailyCall>()
    val postImportCalls = mutableListOf<PostImportCall>()
    var cancelCount = 0

    override fun scheduleDailyReminder(timeLocal: String, zoneId: ZoneId) {
        scheduleCalls += DailyCall(timeLocal, zoneId)
    }

    override fun cancelDailyReminder() {
        cancelCount++
    }

    override fun enqueuePostImportSummary(summary: ImportSummary, firstDay: String?, lastDay: String?, openDay: String?) {
        postImportCalls += PostImportCall(summary, firstDay, lastDay, openDay)
    }
}

private class FakePermissionChecker(var allowed: Boolean) : NotificationPermissionChecker {
    override fun canPostNotifications(): Boolean = allowed
}
// endregion






