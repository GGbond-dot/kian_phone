package com.kian.khup.output.ui.settings

import androidx.lifecycle.ViewModel
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmModelState
import com.kian.khup.core.data.repository.InterventionSettings
import com.kian.khup.core.data.repository.InterventionSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val interventionSettingsRepository: InterventionSettingsRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val llmEngine: LlmEngine,
) : ViewModel() {

    val interventionSettings: StateFlow<InterventionSettings> =
        interventionSettingsRepository.observeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = interventionSettingsRepository.currentSettings(),
            )

    val aiSettings: StateFlow<AiSettings> =
        aiSettingsRepository.observeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = aiSettingsRepository.currentSettings(),
            )

    private val _aiModelState = MutableStateFlow(llmEngine.modelState())
    val aiModelState: StateFlow<LlmModelState> = _aiModelState.asStateFlow()

    fun setDouyinLimit(minutes: Int) {
        interventionSettingsRepository.setDouyinLimitMinutes(minutes)
    }

    fun setXiaohongshuLimit(minutes: Int) {
        interventionSettingsRepository.setXiaohongshuLimitMinutes(minutes)
    }

    fun setProviderMode(mode: AiProviderMode) {
        aiSettingsRepository.setProviderMode(mode)
    }

    fun setApiBaseUrl(url: String) {
        aiSettingsRepository.setApiBaseUrl(url)
    }

    fun setApiKey(key: String) {
        aiSettingsRepository.setApiKey(key)
    }

    fun setApiModel(model: String) {
        aiSettingsRepository.setApiModel(model)
    }

    fun refreshAiModelState() {
        _aiModelState.value = llmEngine.modelState()
    }
}
