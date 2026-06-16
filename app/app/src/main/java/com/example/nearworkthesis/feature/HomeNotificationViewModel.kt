package com.example.nearworkthesis.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.nearworkthesis.domain.notifications.LastNotification
import com.example.nearworkthesis.domain.notifications.NotificationHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeNotificationViewModel(
    repository: NotificationHistoryRepository
) : ViewModel() {

    val lastNotification: StateFlow<LastNotification?> = repository.observeLastNotification()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(repository: NotificationHistoryRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeNotificationViewModel(repository) as T
                }
            }
        }
    }
}
