package com.kian.khup.core.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * 内存中的单向桥：Today → AI tab 的上下文传递。
 * 使用单例内存状态，避免导航参数的 URL 编码问题。
 *
 * 见 worklog/技术方案_行为线MVP/07_补丁_不适合后AI讨论.md §2 §9.2。
 */
@Singleton
class AiContextBridge @Inject constructor() {

    data class PendingContext(val message: String, val suggestionId: Long?)

    private val _pending = MutableStateFlow<PendingContext?>(null)
    private val _sessionToOpen = MutableStateFlow<Long?>(null)

    /**
     * TodayViewModel 写入：用户点"和 AI 聊聊"时调用。
     * 建议卡入口携带 suggestionId；FAB 入口没有具体建议，传 null。
     * 下次 AiChatViewModel 初始化时会消费这条消息。
     */
    fun setPending(message: String, suggestionId: Long? = null) {
        _pending.value = PendingContext(message.trim().take(MAX_MESSAGE_LEN), suggestionId)
    }

    /**
     * AiChatViewModel 消费：进入 AI tab 时调用一次，随后清空。
     * 返回 null 表示没有待处理的上下文（正常打开 AI tab 时的情形）。
     */
    fun consumePending(): PendingContext? = _pending.getAndUpdate { null }

    /**
     * HistoryScreen 写入：用户点"查看当时的讨论 →"时调用。
     * 让 AiChatViewModel 在下次 init 时直接打开这条 ChatSession，而不是默认行为。
     */
    fun setSessionToOpen(sessionId: Long) {
        _sessionToOpen.value = sessionId
    }

    fun consumeSessionToOpen(): Long? = _sessionToOpen.getAndUpdate { null }

    private companion object {
        const val MAX_MESSAGE_LEN = 1000
    }
}
