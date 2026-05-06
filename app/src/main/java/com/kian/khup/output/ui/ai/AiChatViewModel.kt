package com.kian.khup.output.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmModelState
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.entities.ChatMessage as ChatMessageEntity
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
            val recent = chatMessageDao.loadRecent(MAX_HISTORY_LOAD)
                .asReversed()
                .map { it.toUi() }
            if (recent.isNotEmpty()) {
                _uiState.update { it.copy(messages = recent) }
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

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(role = ChatRole.User, text = trimmed)
        val mode = _uiState.value.settings.providerMode
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                error = null,
                modelState = llmEngine.modelState(),
            )
        }

        viewModelScope.launch {
            persist(userMessage, mode)
            val prompt = buildPrompt(_uiState.value.messages, mode)
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
                    persist(assistant, mode)
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

    fun clear() {
        _uiState.update {
            it.copy(messages = emptyList(), error = null, modelState = llmEngine.modelState())
        }
        viewModelScope.launch { chatMessageDao.clear() }
    }

    private suspend fun persist(message: ChatMessage, mode: AiProviderMode) {
        chatMessageDao.insert(
            ChatMessageEntity(
                role = when (message.role) {
                    ChatRole.User -> "user"
                    ChatRole.Assistant -> "assistant"
                },
                text = message.text,
                providerTier = inferTier(mode),
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    private fun inferTier(mode: AiProviderMode): String = when (mode) {
        AiProviderMode.ApiOnly -> "api"
        AiProviderMode.LocalOnly, AiProviderMode.LocalFirst -> "local"
    }

    private fun ChatMessageEntity.toUi(): ChatMessage = ChatMessage(
        role = if (role == "assistant") ChatRole.Assistant else ChatRole.User,
        text = text,
    )

    private fun buildPrompt(messages: List<ChatMessage>, mode: AiProviderMode): String {
        val limit = when (mode) {
            AiProviderMode.ApiOnly -> MAX_CONTEXT_MESSAGES_API
            AiProviderMode.LocalOnly, AiProviderMode.LocalFirst -> MAX_CONTEXT_MESSAGES
        }
        val recent = messages.takeLast(limit)
        return buildString {
            appendLine("你是 KHUP 里的 kian-ai-chat 助手。")
            appendLine("要求：用中文回答，直接、有用、不要编造你不知道的手机数据。")
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
    }
}

data class AiChatUiState(
    val modelState: LlmModelState,
    val settings: AiSettings,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
)

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    User,
    Assistant,
}
