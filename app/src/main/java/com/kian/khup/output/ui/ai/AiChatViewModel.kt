package com.kian.khup.output.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiChatUiState(
            modelState = llmEngine.modelState(),
            settings = aiSettingsRepository.currentSettings(),
        )
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            var initialized = false
            chatSessionDao.observeAll().collect { sessions ->
                if (!initialized) {
                    initialized = true
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
                }
        }
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
            val prompt = buildPrompt(_uiState.value.messages, mode, toolRun)
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
    ): String {
        val limit = when (mode) {
            AiProviderMode.ApiOnly -> MAX_CONTEXT_MESSAGES_API
            AiProviderMode.LocalOnly, AiProviderMode.LocalFirst -> MAX_CONTEXT_MESSAGES
        }
        val recent = messages.takeLast(limit)
        return buildString {
            appendLine("你是 KHUP 里的 kian-ai-chat 助手。")
            appendLine(KhupPromptPolicy.WORLDVIEW.trim())
            appendLine(KhupPromptPolicy.MENTOR_STYLE.trim())
            appendLine(KhupPromptPolicy.LOCAL_TOOL_RULES.trim())
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
