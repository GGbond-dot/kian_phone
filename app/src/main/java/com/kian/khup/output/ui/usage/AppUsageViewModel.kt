package com.kian.khup.output.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AppUsageTotal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppUsageViewModel @Inject constructor(
    private val appSessionDao: AppSessionDao,
) : ViewModel() {

    enum class Period { TODAY, WEEK, MONTH }

    private val _period = MutableStateFlow(Period.TODAY)
    val period: StateFlow<Period> = _period.asStateFlow()

    val usageData: StateFlow<AppUsageUiState> = _period.flatMapLatest { p ->
        val (startMs, endMs) = p.toTimeRange()
        appSessionDao.observeUsageSummary(startMs, endMs)
            .map { sessions -> buildUiState(sessions) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUsageUiState.Loading)

    fun selectPeriod(period: Period) { _period.value = period }

    private fun buildUiState(sessions: List<AppUsageTotal>): AppUsageUiState {
        val totalMs = sessions.sumOf { it.foregroundMs }
        val entries = sessions.map { AppEntry(packageName = it.packageName, totalMs = it.foregroundMs) }
        return AppUsageUiState.Ready(totalMs = totalMs, apps = entries)
    }

    private fun Period.toTimeRange(): Pair<Long, Long> {
        val todayStart = todayStartLocalMs()
        val tomorrowStart = todayStart + TimeUnit.DAYS.toMillis(1)
        return when (this) {
            Period.TODAY -> todayStart to tomorrowStart
            Period.WEEK -> (todayStart - TimeUnit.DAYS.toMillis(6)) to tomorrowStart
            Period.MONTH -> (todayStart - TimeUnit.DAYS.toMillis(29)) to tomorrowStart
        }
    }

    data class AppEntry(
        val packageName: String,
        val totalMs: Long,
    )

    sealed class AppUsageUiState {
        object Loading : AppUsageUiState()
        data class Ready(
            val totalMs: Long,
            val apps: List<AppEntry>,
        ) : AppUsageUiState()
    }
}
