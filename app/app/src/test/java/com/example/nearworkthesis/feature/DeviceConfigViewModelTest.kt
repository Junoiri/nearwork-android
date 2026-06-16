// Tests the device config ViewModel flow with a fake repository.
package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.device.DeviceConnectionState
import com.example.nearworkthesis.domain.device.DeviceSettings
import com.example.nearworkthesis.domain.device.DeviceSettingsDefaults
import com.example.nearworkthesis.domain.device.DeviceSettingsField
import com.example.nearworkthesis.domain.device.DeviceTimeMode
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConfigViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun applySettings_success_emitsReadyState() = runTest {
        val repo = FakeDeviceConfigRepository()
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        // init triggers refreshFromDevice() immediately; state should become Ready quickly.
        advanceUntilIdle()
        val ready = vm.uiState.value as DeviceConfigUiState.Ready
        assertTrue(!ready.isBusy)

        vm.applySettings()
        advanceUntilIdle()

        val after = vm.uiState.value as DeviceConfigUiState.Ready
        assertTrue(!after.isBusy)
        assertTrue(repo.lastWritten != null)
    }

    @Test
    fun clearDeviceData_success_callsRepository_andReturnsReadyState() = runTest {
        val repo = FakeDeviceConfigRepository()
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        advanceUntilIdle()
        vm.clearDeviceData()
        advanceUntilIdle()

        val after = vm.uiState.value as DeviceConfigUiState.Ready
        assertTrue(!after.isBusy)
        assertTrue(repo.lastCleared != null)
    }

    @Test
    fun updateMethods_export_reset_and_refreshPaths_work() = runTest {
        val repo = FakeDeviceConfigRepository()
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        var observedEvent: DeviceConfigEvent? = null
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { event ->
                observedEvent = event
            }
        }

        advanceUntilIdle()
        vm.updateSamplingIntervalSeconds(12)
        vm.updateAutoShutdownMinutes(34)
        vm.updateLowLightLuxThreshold(56)
        vm.updateLowPowerMode(false)
        vm.updateTimeMode(DeviceTimeMode.KEEP_DEVICE_TIME)

        val updated = vm.uiState.value as DeviceConfigUiState.Ready
        assertEquals(12, updated.form.samplingIntervalSeconds)
        assertEquals(34, updated.form.autoShutdownMinutes)
        assertEquals(56, updated.form.lowLightLuxThreshold)
        assertEquals(false, updated.form.enableLowPowerMode)
        assertEquals(DeviceTimeMode.KEEP_DEVICE_TIME, updated.form.deviceTimeMode)

        vm.exportConfigUf2()
        waitForDeviceConfigEvent { observedEvent != null }
        val exportEvent = observedEvent as DeviceConfigEvent.LaunchSaveConfig
        assertEquals("optoconf.uf2", exportEvent.filename)
        assertTrue(exportEvent.bytes.contentEquals(byteArrayOf(1, 2, 3, 4)))

        vm.resetToDefaults()
        advanceUntilIdle()
        val resetState = vm.uiState.value as DeviceConfigUiState.Ready
        assertEquals(DeviceSettingsDefaults.defaults.samplingIntervalSeconds, resetState.form.samplingIntervalSeconds)
        assertEquals(1, repo.resetCalls)

        vm.refreshConnection()
        assertEquals(1, repo.refreshConnectionCalls)
        collectionJob.cancel()
    }

    @Test
    fun invalidForm_blocksApplyExportAndClear_untilFixed() = runTest {
        val repo = FakeDeviceConfigRepository()
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        advanceUntilIdle()
        vm.updateSamplingIntervalSeconds(0)

        val invalid = vm.uiState.value as DeviceConfigUiState.Ready
        assertTrue(invalid.validationErrors.containsKey(DeviceSettingsField.SamplingIntervalSeconds))

        vm.applySettings()
        vm.exportConfigUf2()
        vm.clearDeviceData()
        advanceUntilIdle()

        assertEquals(null, repo.lastWritten)
        assertEquals(null, repo.lastCleared)
        assertEquals(0, repo.buildConfigCalls)

        vm.updateSamplingIntervalSeconds(10)
        vm.applySettings()
        advanceUntilIdle()
        assertTrue(repo.lastWritten != null)
    }

    @Test
    fun errorAndRetryPaths_useFallbackForm_andRecover() = runTest {
        val repo = FakeDeviceConfigRepository().apply {
            nextReadResult = Result.failure(IllegalStateException("Read failed"))
        }
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        advanceUntilIdle()
        val error = vm.uiState.value as DeviceConfigUiState.Error
        assertEquals("Read failed", error.message)
        assertEquals(DeviceSettingsDefaults.defaults.samplingIntervalSeconds, error.form?.samplingIntervalSeconds)

        repo.nextReadResult = Result.success(
            DeviceSettingsDefaults.defaults.copy(samplingIntervalSeconds = 22)
        )
        vm.retryLastError()
        advanceUntilIdle()

        val ready = vm.uiState.value as DeviceConfigUiState.Ready
        assertEquals(22, ready.form.samplingIntervalSeconds)
    }

    @Test
    fun applyExportClearAndResetFailures_surfaceErrorStates_andFactoryCreatesViewModel() = runTest {
        val repo = FakeDeviceConfigRepository().apply {
            nextWriteResult = Result.failure(IllegalStateException("Write failed"))
            nextBuildResult = Result.failure(IllegalStateException("Build failed"))
            nextClearResult = Result.failure(IllegalStateException("Clear failed"))
            nextResetResult = Result.failure(IllegalStateException("Reset failed"))
        }
        val factory = DeviceConfigViewModel.factory(repo)
        val created = factory.create(DeviceConfigViewModel::class.java)
        assertTrue(created is DeviceConfigViewModel)
        val vm = DeviceConfigViewModel(repository = repo, ioDispatcher = UnconfinedTestDispatcher(testScheduler))

        advanceUntilIdle()
        vm.applySettings()
        advanceUntilIdle()
        assertEquals("Write failed", (vm.uiState.value as DeviceConfigUiState.Error).message)

        vm.updateSamplingIntervalSeconds(12)
        vm.exportConfigUf2()
        advanceUntilIdle()
        assertEquals("Build failed", (vm.uiState.value as DeviceConfigUiState.Error).message)

        vm.updateSamplingIntervalSeconds(12)
        vm.clearDeviceData()
        advanceUntilIdle()
        assertEquals("Clear failed", (vm.uiState.value as DeviceConfigUiState.Error).message)

        vm.resetToDefaults()
        advanceUntilIdle()
        assertEquals("Reset failed", (vm.uiState.value as DeviceConfigUiState.Error).message)
    }
}

private fun waitForDeviceConfigEvent(predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 2_000L
    while (!predicate() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10L)
    }
    assertTrue("Timed out waiting for device-config event", predicate())
}

private class FakeDeviceConfigRepository : DeviceConfigRepository {
    var lastWritten: DeviceSettings? = null
    var lastCleared: DeviceSettings? = null
    var buildConfigCalls = 0
    var resetCalls = 0
    var refreshConnectionCalls = 0
    var nextReadResult: Result<DeviceSettings> = Result.success(DeviceSettingsDefaults.defaults)
    var nextWriteResult: Result<Unit> = Result.success(Unit)
    var nextClearResult: Result<Unit> = Result.success(Unit)
    var nextBuildResult: Result<ByteArray> = Result.success(byteArrayOf(1, 2, 3, 4))
    var nextResetResult: Result<DeviceSettings> = Result.success(DeviceSettingsDefaults.defaults)

    override fun observeConnectionState(): Flow<DeviceConnectionState> = flowOf(DeviceConnectionState.Connected)
    override fun refreshConnection() {
        refreshConnectionCalls += 1
    }

    override suspend fun readSettings(): Result<DeviceSettings> = nextReadResult

    override suspend fun writeSettings(settings: DeviceSettings): Result<Unit> {
        lastWritten = settings
        return nextWriteResult
    }

    override suspend fun clearDeviceData(settings: DeviceSettings): Result<Unit> {
        lastCleared = settings
        return nextClearResult
    }

    override suspend fun buildConfigUf2(settings: DeviceSettings): Result<ByteArray> {
        buildConfigCalls += 1
        return nextBuildResult
    }

    override suspend fun resetToDefaults(): Result<DeviceSettings> {
        resetCalls += 1
        return nextResetResult
    }
}





