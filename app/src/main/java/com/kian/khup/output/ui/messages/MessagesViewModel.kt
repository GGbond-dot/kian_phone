package com.kian.khup.output.ui.messages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kian.khup.collection.notification.LaunchCapability
import com.kian.khup.collection.notification.NotificationLaunchRegistry
import com.kian.khup.core.data.db.ClassifiedEvent
import com.kian.khup.core.data.repository.EventRepository
import com.kian.khup.core.data.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessagesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(MessageCategory.Social.label)
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val classifiedMessages: StateFlow<List<ClassifiedEvent>> = _selectedCategory
        .flatMapLatest { category -> messageRepository.observeMessages(category) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val messages: StateFlow<List<MessageUiItem>> = combine(
        classifiedMessages,
        NotificationLaunchRegistry.directLaunchEventIds,
    ) { messages, directLaunchIds ->
        messages.map { message ->
            val capability = when {
                directLaunchIds.contains(message.event.eventId) -> LaunchCapability.DirectNotification
                NotificationLaunchRegistry.canOpenApp(context, message.event.packageName) -> LaunchCapability.App
                else -> LaunchCapability.None
            }
            MessageUiItem(message, capability)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        refreshClassifications()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun refreshClassifications() {
        viewModelScope.launch {
            eventRepository.classifyUnprocessed()
        }
    }

    fun openMessage(item: MessageUiItem) {
        NotificationLaunchRegistry.open(context, item.message.event)
    }

    fun updateClassification(item: MessageUiItem, category: String) {
        if (category == MessageCategory.All.label || category == item.message.classification) return
        viewModelScope.launch {
            messageRepository.updateClassification(item.message.event.eventId, category)
        }
    }
}

data class MessageUiItem(
    val message: ClassifiedEvent,
    val launchCapability: LaunchCapability,
)

enum class MessageCategory(val label: String) {
    Social("社交"),
    All("全部"),
    Verification("验证码"),
    Finance("消费信息"),
    Work("工作"),
    Promotion("推广"),
    Algorithm("算法推送"),
    Other("其他"),
}
