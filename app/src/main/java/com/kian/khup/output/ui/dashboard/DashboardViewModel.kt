package com.kian.khup.output.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.collection.usage.AppUsageSummary
import com.kian.khup.core.data.db.entities.DailyTask
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.repository.DailyTaskRepository
import com.kian.khup.core.data.repository.EventRepository
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: EventRepository,
    private val usageStatsRepository: UsageStatsRepository,
    private val dailyTaskRepository: DailyTaskRepository,
) : ViewModel() {

    val recentEvents: StateFlow<List<Event>> = repository.observeRecent(limit = 100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _usageUiState = MutableStateFlow(UsageUiState())
    val usageUiState: StateFlow<UsageUiState> = _usageUiState.asStateFlow()

    val todayTasks: StateFlow<List<DailyTask>> = dailyTaskRepository.observeTodayTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val overdueTasks: StateFlow<List<DailyTask>> = dailyTaskRepository.observeOverdueUnfinishedTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            usageStatsRepository.observeTodayTopApps()
                .collect { topApps ->
                    _usageUiState.value = _usageUiState.value.copy(topApps = topApps)
                }
        }
        refreshUsageStats()
    }

    fun refreshUsageStats() {
        viewModelScope.launch {
            val hasPermission = NotificationPermissions.hasUsageAccess(context)
            if (hasPermission) usageStatsRepository.syncToday()
            _usageUiState.value = _usageUiState.value.copy(
                hasPermission = hasPermission,
                topApps = if (hasPermission) _usageUiState.value.topApps else emptyList(),
            )
        }
    }

    fun addTask(title: String) {
        viewModelScope.launch {
            dailyTaskRepository.addTodayTask(title)
        }
    }

    fun setTaskDone(taskId: Long, isDone: Boolean) {
        viewModelScope.launch {
            dailyTaskRepository.setDone(taskId, isDone)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            dailyTaskRepository.delete(taskId)
        }
    }
}

data class UsageUiState(
    val hasPermission: Boolean = false,
    val topApps: List<AppUsageSummary> = emptyList(),
)
