package com.kian.khup.output.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.ai.AiContextBridge
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.DailyUsageTotal
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.TodayNarrationDao
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.TodayNarration
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
    private val todayNarrationDao: TodayNarrationDao,
    private val llm: LlmEngine,
    private val aiContextBridge: AiContextBridge,
) : ViewModel() {

    /** "查看当时的讨论"：把 sessionId 推入桥，让 AiChatViewModel 下次 init 时直接打开。 */
    fun requestOpenChatSession(sessionId: Long) {
        aiContextBridge.setSessionToOpen(sessionId)
    }

    fun discussFromReview(anomaly: AttentionAnomaly) {
        aiContextBridge.setPending(
            """
                我注意到 KHUP 提到这个模式：${anomaly.title}。
                这意味着什么？我应该怎么看？
            """.trimIndent()
        )
    }

    fun discussFromReview(suggestion: AnomalySuggestion) {
        aiContextBridge.setPending(
            """
                能再讲讲这条建议吗？

                建议：${suggestion.actionText}

                你说是因为：${suggestion.whyText}

                我当时${suggestion.status.toStatusZh()}了。
            """.trimIndent()
        )
    }

    fun discussTrendFromReview(narrationDiff: String) {
        aiContextBridge.setPending("这周相比上周，$narrationDiff。能解读一下吗？")
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

    private val _storyNarration = MutableStateFlow<String?>(null)
    val storyNarrationState: StateFlow<String?> = _storyNarration.asStateFlow()

    /** suggestionId → ChatSession.id（被拒后用 [和 AI 聊聊] 产生的会话）。 */
    private val _linkedSessions = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val linkedSessionsState: StateFlow<Map<Long, Long>> = _linkedSessions.asStateFlow()

    init {
        observePatterns()
        observeSuggestions()
        observeTrends()
        observeStoryNarration()
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
                .collect {
                    _trends.value = it
                    ensureStoryNarration(it)
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeStoryNarration() {
        viewModelScope.launch {
            _periodDays
                .flatMapLatest { days ->
                    todayNarrationDao.observeForPeriod(todayStartLocalMs(), days)
                }
                .collect { row -> _storyNarration.value = row?.narrationText }
        }
    }

    private fun ensureStoryNarration(trends: TrendsData) {
        viewModelScope.launch {
            val todayMs = todayStartLocalMs()
            if (todayNarrationDao.getForPeriod(todayMs, trends.periodDays) != null) return@launch
            val prompt = buildStoryPrompt(trends)
            val narration = llm.generate(prompt).getOrNull()?.trim()?.take(160).orEmpty()
            if (narration.isBlank()) return@launch
            todayNarrationDao.upsert(
                TodayNarration(
                    dayStartMs = todayMs,
                    periodDays = trends.periodDays,
                    narrationText = narration,
                    generatedAt = System.currentTimeMillis(),
                    modelVersion = REVIEW_MODEL_VERSION,
                )
            )
        }
    }

    private fun buildStoryPrompt(trends: TrendsData): String = """
        你是 KHUP，正在为用户写"这 ${trends.periodDays} 天的故事"。
        数据：
          - 用户写了 ${trends.checkInCount} 次检入
          - 接受了 ${trends.acceptedCount} 条建议
          - 反馈总数 ${trends.totalFeedbackCount} 条
          - 有 ${trends.screenTimeByDay.size} 天的屏幕时间记录
        要求：
          - 1-2 句中文，不超过 80 个汉字
          - 不评价，不训诫，只把趋势说成人话
          - 第二人称"你"
          - 不出现"异常值"、"回归值"、"接受率"这类术语
        直接输出那段话，不要任何前缀或解释。
    """.trimIndent()

    private companion object {
        const val DEFAULT_PERIOD_DAYS = 7
        const val REVIEW_MODEL_VERSION = "review_narration.v1"
    }
}

private fun String.toStatusZh(): String = when (this) {
    "ACCEPTED" -> "接受"
    "REJECTED" -> "拒绝"
    "POSTPONED" -> "换一条"
    "PENDING" -> "还没决定"
    else -> "看过"
}
