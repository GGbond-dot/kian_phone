package com.kian.khup.output.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.collection.usage.AppUsageSummary
import com.kian.khup.core.anomaly.AttentionAnomalyDetector
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.TriggerTagTotal
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.DailyReview
import com.kian.khup.core.data.db.entities.HourlySummary
import com.kian.khup.core.data.repository.DailyTaskRepository
import com.kian.khup.core.data.repository.InterventionRepository
import com.kian.khup.core.data.repository.UsageStatsRepository
import com.kian.khup.core.summary.DailyReviewGenerator
import com.kian.khup.core.trigger.TriggerTagger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
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
    private val usageStatsRepository: UsageStatsRepository,
    private val dailyTaskRepository: DailyTaskRepository,
    private val interventionRepository: InterventionRepository,
    private val dailyReviewGenerator: DailyReviewGenerator,
    private val attentionAnomalyDetector: AttentionAnomalyDetector,
    private val triggerTagger: TriggerTagger,
    hourlySummaryDao: HourlySummaryDao,
    dailyReviewDao: DailyReviewDao,
    attentionAnomalyDao: AttentionAnomalyDao,
    triggerTagDao: TriggerTagDao,
) : ViewModel() {

    val latestHourlySummary: StateFlow<HourlySummary?> = hourlySummaryDao.observeLatest()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val todayReview: StateFlow<DailyReview?> = dailyReviewDao.observeForDay(startOfTodayMs())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val todayAnomalies: StateFlow<List<AttentionAnomaly>> = attentionAnomalyDao.observeForDay(startOfTodayMs())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val todayTriggerTags: StateFlow<List<TriggerTagTotal>> = triggerTagDao.observeTagTotalsForDay(startOfTodayMs())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _usageUiState = MutableStateFlow(UsageUiState())
    val usageUiState: StateFlow<UsageUiState> = _usageUiState.asStateFlow()

    private val _dailyReviewUiState = MutableStateFlow(DailyReviewUiState())
    val dailyReviewUiState: StateFlow<DailyReviewUiState> = _dailyReviewUiState.asStateFlow()

    val todayTasks: StateFlow<List<AnomalySuggestion>> = dailyTaskRepository.observeTodayTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val overdueTasks: StateFlow<List<AnomalySuggestion>> = dailyTaskRepository.observeOverdueUnfinishedTasks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val todayActions: StateFlow<List<ActionLog>> = interventionRepository.observeTodayActions()
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
            if (hasPermission) {
                usageStatsRepository.syncToday()
                interventionRepository.evaluateToday()
                runCatching { attentionAnomalyDetector.detectToday() }
                runCatching { triggerTagger.refreshToday() }
            }
            _usageUiState.value = _usageUiState.value.copy(
                hasPermission = hasPermission,
                topApps = if (hasPermission) _usageUiState.value.topApps else emptyList(),
            )
        }
    }

    fun generateDailyReview() {
        viewModelScope.launch {
            _dailyReviewUiState.value = DailyReviewUiState(isGenerating = true)
            val result = dailyReviewGenerator.generateToday()
            _dailyReviewUiState.value = result.fold(
                onSuccess = { DailyReviewUiState() },
                onFailure = { DailyReviewUiState(error = it.message ?: "生成失败") },
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

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

data class UsageUiState(
    val hasPermission: Boolean = false,
    val topApps: List<AppUsageSummary> = emptyList(),
)

data class DailyReviewUiState(
    val isGenerating: Boolean = false,
    val error: String? = null,
)
