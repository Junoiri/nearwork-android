package com.example.nearworkthesis.feature

import com.example.nearworkthesis.domain.notifications.LastNotification
import com.example.nearworkthesis.domain.notifications.NotificationHistoryRepository
import com.example.nearworkthesis.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeNotificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun lastNotification_tracksRepositoryFlow_andFactoryCreatesViewModel() = runTest {
        val repository = FakeNotificationHistoryRepository()
        val viewModel = HomeNotificationViewModel(repository)
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.lastNotification.collect { }
        }

        assertEquals(null, viewModel.lastNotification.value)

        val notification = LastNotification("Imported", "2 rows", 123L)
        repository.last.value = notification
        advanceUntilIdle()
        assertEquals(notification, viewModel.lastNotification.value)

        val factory = HomeNotificationViewModel.factory(repository)
        val created = factory.create(HomeNotificationViewModel::class.java)
        assertEquals(HomeNotificationViewModel::class.java, created::class.java)
        collectionJob.cancel()
    }
}

private class FakeNotificationHistoryRepository : NotificationHistoryRepository {
    val last = MutableStateFlow<LastNotification?>(null)
    override fun observeLastNotification(): Flow<LastNotification?> = last
    override suspend fun setLastNotification(notification: LastNotification) {
        last.value = notification
    }
}
