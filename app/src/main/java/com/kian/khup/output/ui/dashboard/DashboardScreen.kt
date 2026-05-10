// DEPRECATED: 已被 TodayScreen 替代，下个迭代删除
package com.kian.khup.output.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.kian.khup.core.data.db.TriggerTagTotal
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.DailyReview
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.HourlySummary
import com.kian.khup.core.trigger.TriggerTagger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val usageUiState by viewModel.usageUiState.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val overdueTasks by viewModel.overdueTasks.collectAsStateWithLifecycle()
    val todayActions by viewModel.todayActions.collectAsStateWithLifecycle()
    val latestHourlySummary by viewModel.latestHourlySummary.collectAsStateWithLifecycle()
    val todayReview by viewModel.todayReview.collectAsStateWithLifecycle()
    val todayAnomalies by viewModel.todayAnomalies.collectAsStateWithLifecycle()
    val todayTriggerTags by viewModel.todayTriggerTags.collectAsStateWithLifecycle()
    val dailyReviewUiState by viewModel.dailyReviewUiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsageStats()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DashboardContent(
        usageUiState = usageUiState,
        todayTasks = todayTasks,
        overdueTasks = overdueTasks,
        todayActions = todayActions,
        latestHourlySummary = latestHourlySummary,
        todayReview = todayReview,
        todayAnomalies = todayAnomalies,
        todayTriggerTags = todayTriggerTags,
        dailyReviewUiState = dailyReviewUiState,
        onAddTask = viewModel::addTask,
        onTaskCheckedChange = viewModel::setTaskDone,
        onDeleteTask = viewModel::deleteTask,
        onGenerateDailyReview = viewModel::generateDailyReview,
    )
}

@Composable
private fun DashboardContent(
    usageUiState: UsageUiState,
    todayTasks: List<AnomalySuggestion>,
    overdueTasks: List<AnomalySuggestion>,
    todayActions: List<ActionLog>,
    latestHourlySummary: HourlySummary?,
    todayReview: DailyReview?,
    todayAnomalies: List<AttentionAnomaly>,
    todayTriggerTags: List<TriggerTagTotal>,
    dailyReviewUiState: DailyReviewUiState,
    onAddTask: (String) -> Unit,
    onTaskCheckedChange: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onGenerateDailyReview: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.Overview) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
        ) {
            DashboardTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        when (selectedTab) {
            DashboardTab.Overview -> DashboardTabContent {
                item { AttentionAnomalyCard(anomalies = todayAnomalies) }
                item { TriggerTagsCard(tags = todayTriggerTags) }
                item { HourlySummaryCard(summary = latestHourlySummary) }
                item { InterventionCard(actions = todayActions) }
            }
            DashboardTab.Tasks -> DashboardTabContent {
                item {
                    AnomalySuggestionsCard(
                        tasks = todayTasks,
                        overdueTasks = overdueTasks,
                        onAddTask = onAddTask,
                        onTaskCheckedChange = onTaskCheckedChange,
                        onDeleteTask = onDeleteTask,
                    )
                }
            }
            DashboardTab.Review -> DashboardTabContent {
                item {
                    DailyReviewCard(
                        review = todayReview,
                        uiState = dailyReviewUiState,
                        onGenerate = onGenerateDailyReview,
                    )
                }
            }
            DashboardTab.Usage -> DashboardTabContent {
                item { UsageSummaryCard(usageUiState) }
            }
        }
    }
}

@Composable
private fun DashboardTabContent(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private enum class DashboardTab(val label: String) {
    Overview("概览"),
    Tasks("主线"),
    Review("复盘"),
    Usage("用机"),
}

@Composable
private fun TriggerTagsCard(tags: List<TriggerTagTotal>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日诱因", style = MaterialTheme.typography.titleMedium)
                tags.firstOrNull()?.let {
                    Text(
                        text = triggerTagLabel(it.tag),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (tags.isEmpty()) {
                Text(
                    "暂无明确诱因标签。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tags.take(4).forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = triggerTagLabel(tag.tag),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${tag.count} 次",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionAnomalyCard(anomalies: List<AttentionAnomaly>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日异常", style = MaterialTheme.typography.titleMedium)
                if (anomalies.isNotEmpty()) {
                    Text(
                        text = "${anomalies.size} 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = anomalyColor(anomalies.maxOf { it.severity }),
                    )
                }
            }

            if (anomalies.isEmpty()) {
                Text(
                    "暂未发现明显异常。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val topAnomaly = anomalies.maxWith(compareBy<AttentionAnomaly> { it.severity }.thenBy { it.createdAt })
                Text(
                    text = "最大风险：${topAnomaly.title}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = topAnomaly.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "建议：${anomalySuggestion(topAnomaly)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = anomalyColor(topAnomaly.severity),
                )

                anomalies.filterNot { it.id == topAnomaly.id }.take(2).forEach { anomaly ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = anomaly.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = anomalySeverityLabel(anomaly.severity),
                                style = MaterialTheme.typography.labelSmall,
                                color = anomalyColor(anomaly.severity),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Text(
                            text = anomaly.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyReviewCard(
    review: DailyReview?,
    uiState: DailyReviewUiState,
    onGenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日复盘", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = onGenerate,
                    enabled = !uiState.isGenerating,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = if (review == null) "生成今日复盘" else "重新生成今日复盘")
                }
            }

            when {
                uiState.isGenerating -> {
                    Text(
                        "正在生成今日复盘...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                review == null -> {
                    Text(
                        "还没有生成今日复盘。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    mentorLine(review)?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = review.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "生成于 ${formatTime(review.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            uiState.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HourlySummaryCard(summary: HourlySummary?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近 1 小时", style = MaterialTheme.typography.titleMedium)
                summary?.let {
                    Text(
                        importanceLabel(it.importance),
                        style = MaterialTheme.typography.labelMedium,
                        color = importanceColor(it.importance),
                    )
                }
            }

            if (summary == null) {
                Text(
                    "还没有生成通知摘要。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = summary.summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${summary.eventCount} 条通知 · ${formatHourMinute(summary.windowStartMs)}-${formatHourMinute(summary.windowEndMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InterventionCard(actions: List<ActionLog>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("算法 App 干预", style = MaterialTheme.typography.titleMedium)
            if (actions.isEmpty()) {
                Text(
                    "超过你在设置里配置的当天累计时长后，会提醒一次并记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                actions.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = actionRuleLabel(action.ruleId),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = action.actionType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = formatTime(action.triggeredAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnomalySuggestionsCard(
    tasks: List<AnomalySuggestion>,
    overdueTasks: List<AnomalySuggestion>,
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
                    AnomalySuggestionRow(
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
                    AnomalySuggestionRow(
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
private fun AnomalySuggestionRow(
    task: AnomalySuggestion,
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

private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
private val dayFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
private val hourMinuteFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(ts: Long): String = timeFmt.format(Date(ts))
private fun formatDay(ts: Long): String = dayFmt.format(Date(ts))
private fun formatHourMinute(ts: Long): String = hourMinuteFmt.format(Date(ts))

private fun importanceLabel(importance: Int): String =
    when (importance) {
        0 -> "普通"
        1 -> "一般"
        2 -> "重要"
        else -> "紧急"
    }

@Composable
private fun importanceColor(importance: Int) =
    when (importance) {
        0 -> MaterialTheme.colorScheme.onSurfaceVariant
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

private fun actionRuleLabel(ruleId: String): String =
    when (ruleId) {
        "algorithm.douyin.daily" -> "抖音超过阈值"
        "algorithm.xiaohongshu.daily" -> "小红书超过阈值"
        "algorithm.douyin.daily_30m" -> "抖音超过 30 分钟"
        "algorithm.xiaohongshu.daily_20m" -> "小红书超过 20 分钟"
        else -> ruleId
    }

private fun anomalySeverityLabel(severity: Int): String =
    when (severity) {
        0, 1 -> "低"
        2 -> "中"
        else -> "高"
    }

private fun anomalySuggestion(anomaly: AttentionAnomaly): String =
    when (anomaly.type) {
        "app_usage_spike" -> "接下来打开这个 App 前先写目的，今天只保留一次必要使用窗口。"
        "late_algorithm_usage" -> "22:30 后把它从后台划掉，改用一个低刺激收尾动作。"
        "notification_burst" -> "今晚先关掉非必要通知 1 小时，避免被同一波消息继续牵走。"
        "notification_source_burst" -> "把这个来源的非必要通知静音 1 小时，只保留必须回复的人。"
        "rapid_app_switching" -> "先停在一个入口，接下来 10 分钟只允许一个明确动作。"
        "repeated_unlocks" -> "把手机放到够不到的位置，等下一次想点亮的冲动过去。"
        "late_repeated_unlocks" -> "今晚别再确认一次了，把屏幕朝下，给入睡留出空白。"
        else -> "先暂停 2 分钟，写下它打断你的具体原因。"
    }

private fun mentorLine(review: DailyReview): String? =
    runCatching {
        JSONObject(review.highlights).optString("无名导师").takeIf { it.isNotBlank() }
    }.getOrNull()

private fun triggerTagLabel(tag: String): String =
    when (tag) {
        TriggerTagger.TAG_ALGORITHMIC -> "算法内容"
        TriggerTagger.TAG_SOCIAL -> "社交打断"
        TriggerTagger.TAG_PROMOTION -> "促销推广"
        TriggerTagger.TAG_TASK -> "必要事务"
        TriggerTagger.TAG_STUDY_WORK -> "学习工作"
        TriggerTagger.TAG_EMOTION_ESCAPE -> "情绪逃避"
        else -> tag
    }

@Composable
private fun anomalyColor(severity: Int) =
    when (severity) {
        0, 1 -> MaterialTheme.colorScheme.onSurfaceVariant
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
}
