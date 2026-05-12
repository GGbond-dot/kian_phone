package com.kian.khup.output.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.ai.AiContextBridge
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.DailyUsageTotal
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val anomalyDao: AttentionAnomalyDao,
    private val suggestionDao: AnomalySuggestionDao,
    @Suppress("UnusedPrivateMember") private val feedbackDao: UserFeedbackDao,
    private val eventDao: EventDao,
    private val appSessionDao: AppSessionDao,
    private val chatSessionDao: ChatSessionDao,
    private val aiContextBridge: AiContextBridge,
) : ViewModel() {

    /** "查看当时的讨论"：把 sessionId 推入桥，让 AiChatViewModel 下次 init 时直接打开。 */
    fun requestOpenChatSession(sessionId: Long) {
        aiContextBridge.setSessionToOpen(sessionId)
    }

    data class TrendsData(
        val periodDays: Int = 7,
        val screenTimeByDay: List<DailyUsageTotal> = emptyList(),
        val checkInCount: Int = 0,
        val acceptedCount: Int = 0,
        val totalFeedbackCount: Int = 0,
    )

    private val _patterns = MutableStateFlow<List<AttentionAnomaly>>(emptyList())
    val patternsState: StateFlow<List<AttentionAnomaly>> = _patterns.asStateFlow()

    private val _suggestions = MutableStateFlow<Map<String, List<AnomalySuggestion>>>(emptyMap())
    val suggestionsState: StateFlow<Map<String, List<AnomalySuggestion>>> = _suggestions.asStateFlow()

    private val _periodDays = MutableStateFlow(DEFAULT_PERIOD_DAYS)
    val periodDaysState: StateFlow<Int> = _periodDays.asStateFlow()

    private val _trends = MutableStateFlow(TrendsData())
    val trendsState: StateFlow<TrendsData> = _trends.asStateFlow()

    /** suggestionId → ChatSession.id（被拒后用 [和 AI 聊聊] 产生的会话）。 */
    private val _linkedSessions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val linkedSessionsState: StateFlow<Map<Long, Long>> = _linkedSessions.asStateFlow()

    init {
        observePatterns()
        observeSuggestions()
        observeTrends()
        observeLinkedSessions()
    }

    fun setPeriodDays(days: Int) {
        if (days != 7 && days != 30) return
        if (days == _periodDays.value) return
        _periodDays.value = days
    }

    private fun observePatterns() {
        viewModelScope.launch {
            anomalyDao.observeByStatus("ACTIVE").collect { _patterns.value = it }
        }
    }

    private fun observeLinkedSessions() {
        viewModelScope.launch {
            chatSessionDao.observeLinkedSessions().collect { sessions ->
                _linkedSessions.value = sessions
                    .mapNotNull { s -> s.linkedSuggestionId?.let { it to s.id } }
                    .toMap()
            }
        }
    }

    private fun observeSuggestions() {
        viewModelScope.launch {
            combine(
                suggestionDao.observeByStatus("ACCEPTED"),
                suggestionDao.observeByStatus("PENDING"),
                suggestionDao.observeByStatus("POSTPONED"),
                suggestionDao.observeByStatus("REJECTED"),
            ) { accepted, pending, postponed, rejected ->
                buildMap {
                    if (pending.isNotEmpty()) put("PENDING", pending)
                    if (accepted.isNotEmpty()) put("ACCEPTED", accepted)
                    if (postponed.isNotEmpty()) put("POSTPONED", postponed)
                    if (rejected.isNotEmpty()) put("REJECTED", rejected)
                }
            }.collect { _suggestions.value = it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeTrends() {
        viewModelScope.launch {
            _periodDays
                .flatMapLatest { days ->
                    val sinceMs = todayStartLocalMs() - days * 86_400_000L
                    combine(
                        appSessionDao.observeDailyUsageSince(sinceMs),
                        eventDao.observeByType(EventType.USER_REPORT, sinceMs),
                        suggestionDao.observeByStatus("ACCEPTED"),
                        suggestionDao.observeByStatus("REJECTED"),
                    ) { screenDays, checkIns, accepted, rejected ->
                        val acceptedInPeriod = accepted.count { it.createdAt >= sinceMs }
                        val rejectedInPeriod = rejected.count { it.createdAt >= sinceMs }
                        TrendsData(
                            periodDays = days,
                            screenTimeByDay = screenDays,
                            checkInCount = checkIns.size,
                            acceptedCount = acceptedInPeriod,
                            totalFeedbackCount = acceptedInPeriod + rejectedInPeriod,
                        )
                    }
                }
                .collect { _trends.value = it }
        }
    }

    private companion object {
        const val DEFAULT_PERIOD_DAYS = 7
    }
}
