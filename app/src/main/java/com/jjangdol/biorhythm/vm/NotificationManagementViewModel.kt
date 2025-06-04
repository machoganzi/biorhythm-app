// app/src/main/java/com/jjangdol/biorhythm/vm/NotificationManagementViewModel.kt
package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjangdol.biorhythm.data.model.Notification
import com.jjangdol.biorhythm.data.model.NotificationPriority
import com.jjangdol.biorhythm.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationManagementViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _filterPriority = MutableStateFlow<NotificationPriority?>(null)

    private val allNotifications = notificationRepository.getAllNotificationsForAdmin()

    val notifications = combine(
        allNotifications,
        _filterPriority
    ) { notifications, filter ->
        if (filter == null) {
            notifications
        } else {
            notifications.filter { it.priority == filter }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun createNotification(
        title: String,
        content: String,
        priority: NotificationPriority
    ) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            notificationRepository.createNotification(title, content, priority)
                .onSuccess {
                    _uiState.value = UiState.Success("알림이 성공적으로 등록되었습니다")
                }
                .onFailure { exception ->
                    _uiState.value = UiState.Error("알림 등록에 실패했습니다: ${exception.message}")
                }
        }
    }

    fun updateNotification(
        notificationId: String,
        title: String,
        content: String,
        priority: NotificationPriority
    ) {
        viewModelScope.launch {
            notificationRepository.updateNotification(notificationId, title, content, priority)
                .onSuccess {
                    _uiState.value = UiState.Success("알림이 수정되었습니다")
                }
                .onFailure { exception ->
                    _uiState.value = UiState.Error("알림 수정에 실패했습니다: ${exception.message}")
                }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
                .onSuccess {
                    _uiState.value = UiState.Success("알림이 삭제되었습니다")
                }
                .onFailure { exception ->
                    _uiState.value = UiState.Error("알림 삭제에 실패했습니다: ${exception.message}")
                }
        }
    }

    fun toggleNotificationStatus(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.toggleNotificationStatus(notificationId)
                .onSuccess {
                    _uiState.value = UiState.Success("알림 상태가 변경되었습니다")
                }
                .onFailure { exception ->
                    _uiState.value = UiState.Error("상태 변경에 실패했습니다: ${exception.message}")
                }
        }
    }

    fun setFilter(priority: NotificationPriority?) {
        _filterPriority.value = priority
    }

    fun refreshNotifications() {
        // 새로고침 로직 (Repository에서 자동으로 실시간 업데이트됨)
        _uiState.value = UiState.Success("목록을 새로고침했습니다")
    }
}
