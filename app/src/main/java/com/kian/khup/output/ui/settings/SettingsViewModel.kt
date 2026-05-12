package com.kian.khup.output.ui.settings

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.AiSettingsRepository
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmModelState
import com.kian.khup.core.data.repository.InterventionSettings
import com.kian.khup.core.data.repository.InterventionSettingsRepository
import com.kian.khup.core.data.usecase.DataClearUseCase
import com.kian.khup.core.data.usecase.DataExportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val interventionSettingsRepository: InterventionSettingsRepository,
    private val aiSettingsRepository: AiSettingsRepository,
    private val llmEngine: LlmEngine,
    private val dataClearUseCase: DataClearUseCase,
    private val dataExportUseCase: DataExportUseCase,
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

    private val _clearState = MutableStateFlow<ClearState>(ClearState.Idle)
    val clearState: StateFlow<ClearState> = _clearState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

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

    fun clearAllData() {
        viewModelScope.launch {
            _clearState.value = ClearState.InProgress
            runCatching { dataClearUseCase.clearAll() }
                .onSuccess { _clearState.value = ClearState.Done }
                .onFailure { _clearState.value = ClearState.Error(it.message ?: "未知错误") }
        }
    }

    fun resetClearState() { _clearState.value = ClearState.Idle }

    fun exportData() {
        viewModelScope.launch {
            _exportState.value = ExportState.InProgress
            runCatching {
                val json = dataExportUseCase.buildExportJson()
                val fileName = "khup_export_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.writeText(json)
                file.absolutePath
            }
                .onSuccess { path -> _exportState.value = ExportState.Done(path) }
                .onFailure { _exportState.value = ExportState.Error(it.message ?: "导出失败") }
        }
    }

    fun resetExportState() { _exportState.value = ExportState.Idle }

    sealed interface ClearState {
        object Idle : ClearState
        object InProgress : ClearState
        object Done : ClearState
        data class Error(val message: String) : ClearState
    }

    sealed interface ExportState {
        object Idle : ExportState
        object InProgress : ExportState
        data class Done(val filePath: String) : ExportState
        data class Error(val message: String) : ExportState
    }
}
