package com.kian.khup.output.ui.settings

import androidx.lifecycle.ViewModel
import com.kian.khup.core.data.repository.InterventionSettings
import com.kian.khup.core.data.repository.InterventionSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val interventionSettingsRepository: InterventionSettingsRepository,
) : ViewModel() {

    val interventionSettings: StateFlow<InterventionSettings> =
        interventionSettingsRepository.observeSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = interventionSettingsRepository.currentSettings(),
            )

    fun setDouyinLimit(minutes: Int) {
        interventionSettingsRepository.setDouyinLimitMinutes(minutes)
    }

    fun setXiaohongshuLimit(minutes: Int) {
        interventionSettingsRepository.setXiaohongshuLimitMinutes(minutes)
    }
}
