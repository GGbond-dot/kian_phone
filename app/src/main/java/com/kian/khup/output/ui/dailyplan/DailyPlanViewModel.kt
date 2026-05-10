package com.kian.khup.output.ui.dailyplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.core.data.db.entities.DailyPlan
import com.kian.khup.core.data.repository.DailyPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DailyPlanViewModel @Inject constructor(
    private val repository: DailyPlanRepository,
) : ViewModel() {

    val plans: StateFlow<List<DailyPlan>> =
        repository.observeToday().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<Pair<Int, Int>> =
        repository.observeTodayProgress().stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)

    fun add(title: String) = viewModelScope.launch { repository.add(title) }
    fun toggleDone(id: Long) = viewModelScope.launch { repository.toggleDone(id) }
    fun updateContent(id: Long, title: String, note: String?) =
        viewModelScope.launch { repository.updateContent(id, title, note) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
    fun reorder(ids: List<Long>) = viewModelScope.launch { repository.reorder(ids) }
}
