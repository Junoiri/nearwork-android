package com.example.nearworkthesis.feature

import android.net.Uri
import com.example.nearworkthesis.importing.howfar.HowfarDeviceInfo
import com.example.nearworkthesis.importing.howfar.HowfarStorageState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelRobolectricTest {

    @Test
    fun readyStorageState_updatesHowfarUiModel_withAndWithoutExpectedNameHint() = runTest {
        val storageRepository = FakeHowfarStorageRepositoryForVm()
        val viewModel = buildViewModel(storageRepository = storageRepository)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiModel.collect()
        }

        storageRepository.emitState(
            HowfarStorageState.Ready(
                HowfarDeviceInfo(
                    treeUri = Uri.parse("content://howfar/optodata"),
                    displayName = "OPTODATA"
                )
            )
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        val expectedName = viewModel.uiModel.value.howfar
        assertEquals("HowFar: Ready", expectedName.statusTitle)
        assertEquals("Storage: OPTODATA", expectedName.statusSubtitle)
        assertEquals(ImportHowfarPrimaryAction.ImportFromDevice, expectedName.primaryAction)
        assertTrue(expectedName.primaryActionEnabled)

        storageRepository.emitState(
            HowfarStorageState.Ready(
                HowfarDeviceInfo(
                    treeUri = Uri.parse("content://howfar/other"),
                    displayName = "HOWFAR"
                )
            )
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()

        val otherName = viewModel.uiModel.value.howfar
        assertEquals("Storage: HOWFAR (expected OPTODATA)", otherName.statusSubtitle)
        collectionJob.cancel()
    }
}
