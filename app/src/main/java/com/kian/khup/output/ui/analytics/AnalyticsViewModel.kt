package com.kian.khup.output.ui.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.collection.usage.AppUsageSummary
import com.kian.khup.core.data.repository.DailyUsageSummary
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsRepository: UsageStatsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            usageStatsRepository.observeTodayTotal()
                .collect { todayTotalMs ->
                    _uiState.value = _uiState.value.copy(todayTotalMs = todayTotalMs)
                }
        }
        viewModelScope.launch {
            usageStatsRepository.observeTodayTopApps(limit = 8)
                .collect { topApps ->
                    _uiState.value = _uiState.value.copy(topApps = topApps)
                }
        }
        viewModelScope.launch {
            usageStatsRepository.observeDailyTotals(days = 7)
                .collect { dailyTotals ->
                    _uiState.value = _uiState.value.copy(dailyTotals = dailyTotals)
                }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val hasPermission = NotificationPermissions.hasUsageAccess(context)
            if (hasPermission) usageStatsRepository.syncToday()
            _uiState.value = _uiState.value.copy(
                hasPermission = hasPermission,
                topApps = if (hasPermission) _uiState.value.topApps else emptyList(),
                todayTotalMs = if (hasPermission) _uiState.value.todayTotalMs else 0L,
                dailyTotals = if (hasPermission) _uiState.value.dailyTotals else emptyList(),
            )
        }
    }
}

data class AnalyticsUiState(
    val hasPermission: Boolean = false,
    val todayTotalMs: Long = 0L,
    val topApps: List<AppUsageSummary> = emptyList(),
    val dailyTotals: List<DailyUsageSummary> = emptyList(),
)
