package com.kian.khup.output.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.repository.AnomalySuggestionRepository
import com.kian.khup.core.data.repository.BehaviorReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val behaviorReportRepository: BehaviorReportRepository,
    private val suggestionRepository: AnomalySuggestionRepository,
    private val eventDao: EventDao,
    private val anomalyDao: AttentionAnomalyDao,
    private val appSessionDao: AppSessionDao,
) : ViewModel() {

    data class MiniObservation(
        val screenTimeMs: Long = 0L,
        val anomalyCount: Int = 0,
        val checkInCount: Int = 0,
    )

    data class RejectDialogState(val suggestionId: Long)

    sealed class SuggestionCardState {
        object Loading : SuggestionCardState()
        object Empty : SuggestionCardState()
        object Generating : SuggestionCardState()
        data class Pending(val suggestion: AnomalySuggestion) : SuggestionCardState()
        data class RecentAccepted(val suggestion: AnomalySuggestion) : SuggestionCardState()
    }

    data class UiState(
        val suggestionCardState: SuggestionCardState = SuggestionCardState.Loading,
        val checkInText: String = "",
        val isSubmitting: Boolean = false,
        val miniObservation: MiniObservation = MiniObservation(),
        val rejectDialogState: RejectDialogState? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var isWaitingForGeneration = false
    private var pendingRejectId: Long? = null
    private var generationTimeoutJob: Job? = null
    private var recentAcceptedSuggestion: AnomalySuggestion? = null

    init {
        val todayMs = todayStartLocalMs()
        observeSuggestion()
        observeMiniStats(todayMs)
    }

    private fun observeSuggestion() {
        viewModelScope.launch {
            suggestionRepository.observeTodayPending().collect { suggestion ->
                val accepted = recentAcceptedSuggestion
                val cardState = when {
                    suggestion != null -> {
                        isWaitingForGeneration = false
                        generationTimeoutJob?.cancel()
                        SuggestionCardState.Pending(suggestion)
                    }
                    accepted != null -> SuggestionCardState.RecentAccepted(accepted)
                    isWaitingForGeneration -> SuggestionCardState.Generating
                    else -> SuggestionCardState.Empty
                }
                _uiState.update { it.copy(suggestionCardState = cardState) }
            }
        }
    }

    private fun observeMiniStats(todayMs: Long) {
        viewModelScope.launch {
            combine(
                appSessionDao.observeTotalUsageSince(todayMs),
                anomalyDao.observeForDay(todayMs),
                eventDao.observeByType(EventType.USER_REPORT, todayMs),
            ) { screenMs, anomalies, events ->
                MiniObservation(
                    screenTimeMs = screenMs,
                    anomalyCount = anomalies.count { it.status == "ACTIVE" },
                    checkInCount = events.size,
                )
            }.collect { obs -> _uiState.update { it.copy(miniObservation = obs) } }
        }
    }

    fun onCheckInTextChange(text: String) {
        _uiState.update { it.copy(checkInText = text.take(500)) }
    }

    fun submitCheckIn() {
        val text = _uiState.value.checkInText.trim()
        if (text.isBlank() || _uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                behaviorReportRepository.submit(text)
                isWaitingForGeneration = true
                recentAcceptedSuggestion = null
                _uiState.update {
                    it.copy(
                        checkInText = "",
                        isSubmitting = false,
                        suggestionCardState = SuggestionCardState.Generating,
                    )
                }
                scheduleGenerationTimeout()
            } catch (_: Exception) {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun acceptSuggestion(id: Long) {
        viewModelScope.launch {
            val suggestion = (_uiState.value.suggestionCardState as? SuggestionCardState.Pending)
                ?.suggestion ?: return@launch
            suggestionRepository.accept(id)
            recentAcceptedSuggestion = suggestion
            _uiState.update { it.copy(suggestionCardState = SuggestionCardState.RecentAccepted(suggestion)) }
        }
    }

    fun postponeSuggestion(id: Long) {
        viewModelScope.launch {
            suggestionRepository.postpone(id)
            isWaitingForGeneration = true
            recentAcceptedSuggestion = null
            _uiState.update { it.copy(suggestionCardState = SuggestionCardState.Generating) }
            scheduleGenerationTimeout()
        }
    }

    fun openRejectDialog(id: Long) {
        pendingRejectId = id
        _uiState.update { it.copy(rejectDialogState = RejectDialogState(id)) }
    }

    fun closeRejectDialog() {
        pendingRejectId = null
        _uiState.update { it.copy(rejectDialogState = null) }
    }

    fun confirmReject(reason: String?) {
        val id = pendingRejectId ?: return
        pendingRejectId = null
        viewModelScope.launch {
            suggestionRepository.reject(id, reason?.trim()?.ifBlank { null })
            recentAcceptedSuggestion = null
            _uiState.update { it.copy(rejectDialogState = null) }
        }
    }

    private fun scheduleGenerationTimeout() {
        generationTimeoutJob?.cancel()
        generationTimeoutJob = viewModelScope.launch {
            delay(30_000L)
            if (isWaitingForGeneration) {
                isWaitingForGeneration = false
                _uiState.update { state ->
                    if (state.suggestionCardState is SuggestionCardState.Generating)
                        state.copy(suggestionCardState = SuggestionCardState.Empty)
                    else state
                }
            }
        }
    }
}
