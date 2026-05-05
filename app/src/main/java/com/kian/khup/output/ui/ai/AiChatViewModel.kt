package com.kian.khup.output.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmModelState
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
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true,
                error = null,
                modelState = llmEngine.modelState(),
            )
        }

        viewModelScope.launch {
            val prompt = buildPrompt(_uiState.value.messages)
            llmEngine.generate(prompt)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                role = ChatRole.Assistant,
                                text = response.ifBlank { "模型返回了空内容。" },
                            ),
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
    }

    private fun buildPrompt(messages: List<ChatMessage>): String {
        val recent = messages.takeLast(MAX_CONTEXT_MESSAGES)
        return buildString {
            appendLine("你是 KHUP 里的本地 AI 助手。")
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
