package com.kian.khup.output.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.collection.notification.LaunchCapability
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessagesScreen(viewModel: MessagesViewModel = hiltViewModel()) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshClassifications()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MessagesContent(
        selectedCategory = selectedCategory,
        messages = messages,
        onCategorySelected = viewModel::selectCategory,
        onMessageClick = viewModel::openMessage,
        onCategoryChange = viewModel::updateClassification,
    )
}

@Composable
private fun MessagesContent(
    selectedCategory: String,
    messages: List<MessageUiItem>,
    onCategorySelected: (String) -> Unit,
    onMessageClick: (MessageUiItem) -> Unit,
    onCategoryChange: (MessageUiItem, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChips(
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                )
            }
            if (messages.isEmpty()) {
                item {
                    EmptyMessages()
                }
            } else {
                items(messages, key = { it.message.event.eventId }) { message ->
                    MessageCard(
                        item = message,
                        onClick = { onMessageClick(message) },
                        onCategoryChange = { category -> onCategoryChange(message, category) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CategoryChips(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MessageCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category.label,
                onClick = { onCategorySelected(category.label) },
                label = { Text(category.label) },
            )
        }
    }
}

@Composable
private fun MessageCard(
    item: MessageUiItem,
    onClick: () -> Unit,
    onCategoryChange: (String) -> Unit,
) {
    val message = item.message
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.event.title ?: "(无标题)",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = message.classification,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            message.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${priorityText(message.priority)} · ${launchText(item.launchCapability)} · ${message.event.packageName} · ${formatTime(message.event.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CategoryMenu(
                    currentCategory = message.classification,
                    onCategoryChange = onCategoryChange,
                )
            }
        }
    }
}

@Composable
private fun CategoryMenu(
    currentCategory: String,
    onCategoryChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("改分类")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MessageCategory.entries
                .filter { it != MessageCategory.All }
                .forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        enabled = category.label != currentCategory,
                        onClick = {
                            expanded = false
                            onCategoryChange(category.label)
                        },
                    )
                }
        }
    }
}

@Composable
private fun EmptyMessages() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "还没有这个分类的消息",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private fun formatTime(timestamp: Long): String = timeFmt.format(Date(timestamp))

private fun priorityText(priority: Int): String =
    when (priority) {
        3 -> "高优先级"
        2 -> "中优先级"
        1 -> "低优先级"
        else -> "普通"
    }

private fun launchText(capability: LaunchCapability): String =
    when (capability) {
        LaunchCapability.DirectNotification -> "可直达通知"
        LaunchCapability.App -> "打开 App"
        LaunchCapability.None -> "无法打开"
    }
