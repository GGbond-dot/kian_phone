package com.kian.khup.output.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kian.khup.collection.usage.AppUsageSummary
import com.kian.khup.core.data.db.entities.DailyTask
import com.kian.khup.core.data.db.entities.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()
    val usageUiState by viewModel.usageUiState.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val overdueTasks by viewModel.overdueTasks.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsageStats()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DashboardContent(
        events = events,
        usageUiState = usageUiState,
        todayTasks = todayTasks,
        overdueTasks = overdueTasks,
        onAddTask = viewModel::addTask,
        onTaskCheckedChange = viewModel::setTaskDone,
        onDeleteTask = viewModel::deleteTask,
    )
}

@Composable
private fun DashboardContent(
    events: List<Event>,
    usageUiState: UsageUiState,
    todayTasks: List<DailyTask>,
    overdueTasks: List<DailyTask>,
    onAddTask: (String) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DailyTasksCard(
                tasks = todayTasks,
                overdueTasks = overdueTasks,
                onAddTask = onAddTask,
                onTaskCheckedChange = onTaskCheckedChange,
                onDeleteTask = onDeleteTask,
            )
        }
        item {
            UsageSummaryCard(usageUiState)
        }
        item {
            Text(
                text = "最近通知",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (events.isEmpty()) {
            item {
                EmptyNotificationsCard()
            }
        } else {
            items(events, key = { it.eventId }) { EventCard(it) }
        }
    }
}

@Composable
private fun DailyTasksCard(
    tasks: List<DailyTask>,
    overdueTasks: List<DailyTask>,
    onAddTask: (String) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
) {
    var newTaskTitle by remember { mutableStateOf("") }
    val doneCount = tasks.count { it.isDone }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日主线", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${doneCount}/${tasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tasks.isEmpty()) {
                Text(
                    "写下今天必须完成的 3 件事。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.forEach { task ->
                    DailyTaskRow(
                        task = task,
                        onCheckedChange = { checked -> onTaskCheckedChange(task.id, checked) },
                        onDelete = { onDeleteTask(task.id) },
                    )
                }
            }

            if (overdueTasks.isNotEmpty()) {
                Text(
                    "过往未完成",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                overdueTasks.forEach { task ->
                    DailyTaskRow(
                        task = task,
                        leadingLabel = formatDay(task.dayStartMs),
                        onCheckedChange = { checked -> onTaskCheckedChange(task.id, checked) },
                        onDelete = { onDeleteTask(task.id) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("新增任务") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onAddTask(newTaskTitle)
                        newTaskTitle = ""
                    },
                    enabled = newTaskTitle.isNotBlank(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加任务")
                }
            }
        }
    }
}

@Composable
private fun DailyTaskRow(
    task: DailyTask,
    leadingLabel: String? = null,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = if (leadingLabel == null) task.title else "$leadingLabel · ${task.title}",
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (task.isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除任务")
        }
    }
}

@Composable
private fun UsageSummaryCard(usageUiState: UsageUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("今日用机 Top 5", style = MaterialTheme.typography.titleMedium)
            when {
                !usageUiState.hasPermission -> {
                    Text(
                        "请先到「设置」开启使用情况访问。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                usageUiState.topApps.isEmpty() -> {
                    Text(
                        "今天还没有可用的前台使用记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val maxMs = usageUiState.topApps.maxOf { it.foregroundMs }
                    usageUiState.topApps.forEach { app ->
                        AppUsageRow(app = app, maxMs = maxMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsageSummary, maxMs: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = app.appLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDuration(app.foregroundMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        LinearProgressIndicator(
            progress = { if (maxMs <= 0) 0f else app.foregroundMs.toFloat() / maxMs.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyNotificationsCard() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有捕获到通知", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "请先到「设置」开启通知使用权",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EventCard(event: Event) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = event.title ?: "(无标题)",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            event.text?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${event.packageName} · ${formatTime(event.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
private val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
private fun formatTime(ts: Long): String = timeFmt.format(Date(ts))
private fun formatDay(ts: Long): String = dayFmt.format(Date(ts))

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
