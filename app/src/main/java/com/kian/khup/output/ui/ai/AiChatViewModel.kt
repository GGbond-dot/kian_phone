package com.kian.khup.output.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.ai.AiContextBridge
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
import com.kian.khup.core.ai.KhupContextSummarizer
import com.kian.khup.core.ai.KhupPromptPolicy
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmModelState
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.entities.ChatMessage as ChatMessageEntity
import com.kian.khup.core.data.db.entities.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val llmEngine: LlmEngine,
    private val aiSettingsRepository: AiSettingsRepository,
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val localToolRegistry: AiLocalToolRegistry,
    private val aiContextBridge: AiContextBridge,
    private val contextSummarizer: KhupContextSummarizer,
) : ViewModel() {

    private var cachedUserContext: String = ""
    private var cachedContextMode: AiProviderMode? = null

    private val _uiState = MutableStateFlow(
        AiChatUiState(
            modelState = llmEngine.modelState(),
            settings = aiSettingsRepository.currentSettings(),
        )
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refreshUserContext(_uiState.value.settings.providerMode)
        }

        // 入口分流：来自建议卡 / FAB 的 AI 入口时，AiContextBridge 里有预填上下文。
        // 这种情况下不复用最近的会话，而是新建一条 ChatSession，
        // 然后自动把预填消息当作用户输入发出。
        // 详见 worklog/技术方案_行为线MVP/07_补丁_不适合后AI讨论.md §5 §9.3。
        val pending = aiContextBridge.consumePending()
        val sessionToOpen = aiContextBridge.consumeSessionToOpen()

        viewModelScope.launch {
            var initialized = false
            chatSessionDao.observeAll().collect { sessions ->
                if (!initialized) {
                    initialized = true
                    if (pending != null) {
                        startSessionForRejectedSuggestion(pending, sessions)
                        return@collect
                    }
                    if (sessionToOpen != null && sessions.any { it.id == sessionToOpen }) {
                        // HistoryScreen → 查看当时的讨论：直接打开指定的 ChatSession。
                        _uiState.update {
                            it.copy(sessions = sessions, currentSessionId = sessionToOpen)
                        }
                        selectSessionInternal(sessionToOpen)
                        return@collect
                    }
                    val mostRecent = sessions.firstOrNull()
                    if (mostRecent != null) {
                        val recent = chatMessageDao.loadRecentBySession(mostRecent.id, MAX_HISTORY_LOAD)
                            .asReversed()
                            .map { it.toUi() }
                        _uiState.update {
                            it.copy(
                                sessions = sessions,
                                currentSessionId = mostRecent.id,
                                messages = recent,
                            )
                        }
                        return@collect
                    }
                }
                _uiState.update { state ->
                    val current = state.currentSessionId
                    val stillExists = current != null && sessions.any { it.id == current }
                    state.copy(
                        sessions = sessions,
                        currentSessionId = if (stillExists) current else null,
                    )
                }
            }
        }
        viewModelScope.launch {
            aiSettingsRepository.observeSettings()
                .collect { settings ->
                    _uiState.update {
                        it.copy(settings = settings, modelState = llmEngine.modelState())
                    }
                    if (settings.providerMode != cachedContextMode) {
                        refreshUserContext(settings.providerMode)
                    }
                }
        }
    }

    private suspend fun startSessionForRejectedSuggestion(
        pending: AiContextBridge.PendingContext,
        sessions: List<ChatSession>,
    ) {
        val now = System.currentTimeMillis()
        val sessionId = chatSessionDao.insert(
            ChatSession(
                title = if (pending.suggestionId == null) "聊聊今天" else "讨论被拒建议",
                createdAt = now,
                updatedAt = now,
                lastMessagePreview = pending.message.take(PREVIEW_LEN),
                linkedSuggestionId = pending.suggestionId,
            )
        )
        _uiState.update {
            it.copy(
                sessions = sessions,
                currentSessionId = sessionId,
                messages = emptyList(),
                error = null,
            )
        }
        send(pending.message)
    }

    private suspend fun refreshUserContext(mode: AiProviderMode) {
        val budget = if (mode == AiProviderMode.ApiOnly)
            KhupContextSummarizer.TOKEN_BUDGET_API
        else
            KhupContextSummarizer.TOKEN_BUDGET_LOCAL
        cachedUserContext = try { contextSummarizer.buildUserContext(budget) } catch (_: Throwable) { "" }
        cachedContextMode = mode
    }

    fun refreshModelState() {
        _uiState.update { it.copy(modelState = llmEngine.modelState()) }
    }

    fun newSession() {
        if (_uiState.value.isGenerating) return
        _uiState.update {
            it.copy(messages = emptyList(), currentSessionId = null, error = null)
        }
    }

    fun selectSession(sessionId: Long) {
        if (_uiState.value.isGenerating) return
        if (_uiState.value.currentSessionId == sessionId) return
        viewModelScope.launch { selectSessionInternal(sessionId) }
    }

    private suspend fun selectSessionInternal(sessionId: Long) {
        val recent = chatMessageDao.loadRecentBySession(sessionId, MAX_HISTORY_LOAD)
            .asReversed()
            .map { it.toUi() }
        _uiState.update {
            it.copy(
                messages = recent,
                currentSessionId = sessionId,
                error = null,
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            chatSessionDao.deleteById(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                _uiState.update {
                    it.copy(messages = emptyList(), currentSessionId = null, error = null)
                }
            }
        }
    }

    fun clearCurrentSession() {
        if (_uiState.value.isGenerating) return
        val sid = _uiState.value.currentSessionId
        _uiState.update { it.copy(messages = emptyList(), error = null) }
        if (sid != null) {
            viewModelScope.launch {
                chatMessageDao.clearSession(sid)
                chatSessionDao.touch(sid, System.currentTimeMillis(), null)
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(role = ChatRole.User, text = trimmed)
        val mode = _uiState.value.settings.providerMode
        val isFirstMessage = _uiState.value.messages.isEmpty()
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                error = null,
                modelState = llmEngine.modelState(),
            )
        }

        viewModelScope.launch {
            val sessionId = ensureSession(trimmed, isFirstMessage)
            persist(sessionId, userMessage, mode)
            val toolRun = localToolRegistry.runFor(trimmed)
            // 用 DAO 兜底：刚由 startSessionForRejectedSuggestion 创建的 session 可能还
            // 没出现在 state.sessions（observeAll 的下一帧才推上来）。
            val isRejectDiscussion = chatSessionDao.findById(sessionId)?.linkedSuggestionId != null
            val prompt = buildPrompt(_uiState.value.messages, mode, toolRun, isRejectDiscussion)
            llmEngine.generate(prompt)
                .onSuccess { response ->
                    val assistant = ChatMessage(
                        role = ChatRole.Assistant,
                        text = response.ifBlank { "模型返回了空内容。" },
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + assistant,
                            isGenerating = false,
                            modelState = llmEngine.modelState(),
                        )
                    }
                    persist(sessionId, assistant, mode)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message ?: error::class.java.simpleName,
                            modelState = llmEngine.modelState(),
                        )
                    }
                }
        }
    }

    fun runSmokeTest() {
        if (_uiState.value.isGenerating) return

        _uiState.update {
            it.copy(isGenerating = true, error = null, modelState = llmEngine.modelState())
        }
        viewModelScope.launch {
            llmEngine.runSmokeTest()
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(ChatRole.Assistant, response),
                            isGenerating = false,
                            modelState = llmEngine.modelState(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message ?: error::class.java.simpleName,
                            modelState = llmEngine.modelState(),
                        )
                    }
                }
        }
    }

    private suspend fun ensureSession(firstUserText: String, isFirstMessage: Boolean): Long {
        val existing = _uiState.value.currentSessionId
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val title = deriveTitle(firstUserText)
        val id = chatSessionDao.insert(
            ChatSession(
                title = title,
                createdAt = now,
                updatedAt = now,
                lastMessagePreview = firstUserText.take(PREVIEW_LEN),
            )
        )
        _uiState.update { it.copy(currentSessionId = id) }
        // isFirstMessage is informational; title 已基于首条消息生成。
        return id
    }

    private suspend fun persist(sessionId: Long, message: ChatMessage, mode: AiProviderMode) {
        val now = System.currentTimeMillis()
        chatMessageDao.insert(
            ChatMessageEntity(
                sessionId = sessionId,
                role = when (message.role) {
                    ChatRole.User -> "user"
                    ChatRole.Assistant -> "assistant"
                },
                text = message.text,
                providerTier = inferTier(mode),
                timestamp = now,
            )
        )
        chatSessionDao.touch(sessionId, now, message.text.take(PREVIEW_LEN))
    }

    private fun inferTier(mode: AiProviderMode): String = when (mode) {
        AiProviderMode.ApiOnly -> "api"
        AiProviderMode.LocalOnly, AiProviderMode.LocalFirst -> "local"
    }

    private fun deriveTitle(firstUserText: String): String {
        val collapsed = firstUserText.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return "新对话"
        return if (collapsed.length <= TITLE_LEN) collapsed else collapsed.take(TITLE_LEN) + "…"
    }

    private fun ChatMessageEntity.toUi(): ChatMessage = ChatMessage(
        role = if (role == "assistant") ChatRole.Assistant else ChatRole.User,
        text = text,
    )

    private fun buildPrompt(
        messages: List<ChatMessage>,
        mode: AiProviderMode,
        toolRun: AiToolRun?,
        isRejectDiscussion: Boolean,
    ): String {
        val limit = when (mode) {
            AiProviderMode.ApiOnly -> MAX_CONTEXT_MESSAGES_API
            AiProviderMode.LocalOnly, AiProviderMode.LocalFirst -> MAX_CONTEXT_MESSAGES
        }
        val recent = messages.takeLast(limit)
        return buildString {
            appendLine(KhupPromptPolicy.AI_CHAT_SYSTEM_PROMPT.trim())
            if (cachedUserContext.isNotBlank()) {
                appendLine()
                appendLine(cachedUserContext)
                appendLine("以上是系统从用户数据中提取的模式摘要，仅供你理解用户背景。对话中不要直接朗读这些数据，而是自然地把它们融入你对用户的理解中。")
            }
            if (isRejectDiscussion) {
                appendLine(KhupPromptPolicy.REJECT_DISCUSSION_RULES.trim())
            }
            appendLine("要求：用中文回答，直接、有用、不要编造你不知道的手机数据。")
            if (toolRun != null) {
                appendLine("这次问题命中了本地工具调用。回答时优先使用工具结果；如果工具结果没有对应数据，要直接说明本地暂无记录。")
                appendLine(toolRun.toPromptContext())
            }
            appendLine("下面是当前对话：")
            recent.forEach { message ->
                val name = when (message.role) {
                    ChatRole.User -> "用户"
                    ChatRole.Assistant -> "助手"
                }
                appendLine("$name：${message.text}")
            }
            append("助手：")
        }
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 8
        const val MAX_CONTEXT_MESSAGES_API = 20
        const val MAX_HISTORY_LOAD = 100
        const val TITLE_LEN = 18
        const val PREVIEW_LEN = 60
    }
}

data class AiChatUiState(
    val modelState: LlmModelState,
    val settings: AiSettings,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
) {
    val currentTitle: String
        get() = sessions.firstOrNull { it.id == currentSessionId }?.title ?: "新对话"
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    User,
    Assistant,
}
