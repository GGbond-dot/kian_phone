package com.kian.khup.core.summary

import android.util.Log
import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.ai.KhupPromptPolicy
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.TaskTier
import com.kian.khup.core.anomaly.AttentionAnomalyDetector
import com.kian.khup.core.content.ContentThemeTagger
import com.kian.khup.core.content.InformationCocoonAnalyzer
import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.ContentThemeTotal
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.TriggerTagTotal
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.DailyReview
import com.kian.khup.core.data.repository.UsageStatsRepository
import com.kian.khup.core.trigger.TriggerTagger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Suppress("DEPRECATION")
@Singleton
class DailyReviewGenerator @Inject constructor(
    private val usageStatsRepository: UsageStatsRepository,
    private val usageStatsCollector: UsageStatsCollector,
    private val appSessionDao: AppSessionDao,
    private val anomalySuggestionDao: AnomalySuggestionDao,
    private val actionLogDao: ActionLogDao,
    private val eventDao: EventDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val attentionAnomalyDetector: AttentionAnomalyDetector,
    private val triggerTagDao: TriggerTagDao,
    private val triggerTagger: TriggerTagger,
    private val contentThemeTagDao: ContentThemeTagDao,
    private val contentThemeTagger: ContentThemeTagger,
    private val informationCocoonAnalyzer: InformationCocoonAnalyzer,
    private val dailyReviewDao: DailyReviewDao,
    private val llmEngine: LlmEngine,
) {
    suspend fun generateToday(): Result<DailyReview> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val dayStart = startOfDayMs(now)

            runCatching { usageStatsRepository.syncToday() }
                .onFailure { Log.w(TAG, "usage sync before daily review failed", it) }

            val topApps = appSessionDao.loadTopUsageSince(dayStart, TOP_APP_LIMIT)
            val totalUsageMs = usageStatsCollector.getScreenInteractiveMs(dayStart, now)
                .coerceAtMost(now - dayStart)
            // 行为线 MVP：daily_tasks 已迁移到 anomaly_suggestion；下个迭代复盘会改成读 suggestion 反馈。
            val tasks = anomalySuggestionDao.loadForDayLegacy(dayStart)
            val actions = actionLogDao.loadSince(dayStart, ACTION_LIMIT)
            val notifications = eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now)
            val hourlySummaries = hourlySummaryDao.loadInWindow(dayStart, now)
            val anomalies = runCatching { attentionAnomalyDetector.detectToday() }
                .onFailure { Log.w(TAG, "attention anomaly refresh before daily review failed", it) }
                .getOrElse { attentionAnomalyDao.loadForDay(dayStart) }
            val triggerTags = runCatching {
                triggerTagger.refreshToday()
                triggerTagDao.loadTagTotalsForDay(dayStart)
            }.onFailure {
                Log.w(TAG, "trigger tag refresh before daily review failed", it)
            }.getOrElse { emptyList() }
            val contentThemes = runCatching {
                contentThemeTagger.refreshToday()
                contentThemeTagDao.loadThemeTotalsForDay(dayStart)
            }.onFailure {
                Log.w(TAG, "content theme refresh before daily review failed", it)
            }.getOrElse { emptyList() }
            val cocoon = runCatching { informationCocoonAnalyzer.analyzeToday(dayStart) }
                .onFailure { Log.w(TAG, "information cocoon analysis failed", it) }
                .getOrElse { JSONObject() }

            val prompt = buildPrompt(
                dayStart = dayStart,
                now = now,
                totalUsageMs = totalUsageMs,
                topApps = topApps.map {
                    AppUsageLine(
                        label = usageStatsCollector.resolveAppLabel(it.packageName),
                        packageName = it.packageName,
                        foregroundMs = it.foregroundMs,
                    )
                },
                tasks = tasks.map { TaskLine(it.title, it.isDone) },
                actions = actions,
                notificationCount = notifications.size,
                topNotificationPackages = notifications.groupingBy { it.packageName }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { "${usageStatsCollector.resolveAppLabel(it.key)}(${it.value})" },
                anomalies = anomalies,
                triggerTags = triggerTags,
                contentThemes = contentThemes,
                cocoon = cocoon,
                hourlySummaries = hourlySummaries.map { it.summary },
            )

            val parsed = llmEngine.generate(prompt, TaskTier.Heavy)
                .mapCatching(::parseLlmOutput)
                .getOrElse {
                    Log.w(TAG, "daily review llm failed, using rule fallback", it)
                    fallbackReview(totalUsageMs, topApps.map {
                        AppUsageLine(
                            label = usageStatsCollector.resolveAppLabel(it.packageName),
                            packageName = it.packageName,
                            foregroundMs = it.foregroundMs,
                        )
                    }, tasks.map { TaskLine(it.title, it.isDone) }, notifications.size, anomalies, triggerTags)
                }
            val diagnosisJson = buildDiagnosisJson(
                totalUsageMs = totalUsageMs,
                anomalies = anomalies,
                triggerTags = triggerTags,
                contentThemes = contentThemes,
                cocoon = cocoon,
            )
            val mentorLine = llmEngine.generate(buildMentorPrompt(diagnosisJson), TaskTier.Heavy)
                .mapCatching(::parseMentorLine)
                .getOrElse {
                    Log.w(TAG, "daily mentor llm failed, using rule fallback", it)
                    fallbackMentorLine(diagnosisJson, anomalies, triggerTags)
                }
            if (mentorLine.isNotBlank()) {
                parsed.highlights.put(KEY_MENTOR_LINE, mentorLine)
            }
            parsed.highlights.put("诊断", diagnosisJson)

            val review = DailyReview(
                dayStartMs = dayStart,
                summary = parsed.summary,
                highlights = parsed.highlights.toString(),
                createdAt = now,
                modelVersion = MODEL_VERSION,
            )
            dailyReviewDao.upsert(review)
            dailyReviewDao.findForDay(dayStart) ?: review
        }
    }

    private fun buildPrompt(
        dayStart: Long,
        now: Long,
        totalUsageMs: Long,
        topApps: List<AppUsageLine>,
        tasks: List<TaskLine>,
        actions: List<ActionLog>,
        notificationCount: Int,
        topNotificationPackages: List<String>,
        anomalies: List<AttentionAnomaly>,
        triggerTags: List<TriggerTagTotal>,
        contentThemes: List<ContentThemeTotal>,
        cocoon: JSONObject,
        hourlySummaries: List<String>,
    ): String {
        val date = dateFmt.format(Date(dayStart))
        return buildString {
            appendLine("你是 KHUP 每日注意力复盘器。请基于统计数据写今天的复盘。")
            appendLine(KhupPromptPolicy.WORLDVIEW.trim())
            appendLine(KhupPromptPolicy.MENTOR_STYLE.trim())
            appendLine("日期: $date, 统计截止: ${timeFmt.format(Date(now))}")
            appendLine("总用机时长: ${formatDuration(totalUsageMs)}")
            appendLine("Top App:")
            if (topApps.isEmpty()) {
                appendLine("- 无")
            } else {
                topApps.forEach { appendLine("- ${it.label}(${it.packageName}): ${formatDuration(it.foregroundMs)}") }
            }
            appendLine("今日主线任务:")
            if (tasks.isEmpty()) {
                appendLine("- 未记录")
            } else {
                tasks.forEach { appendLine("- [${if (it.isDone) "完成" else "未完成"}] ${it.title.take(50)}") }
            }
            appendLine("干预记录:")
            if (actions.isEmpty()) {
                appendLine("- 无")
            } else {
                actions.take(8).forEach { action ->
                    appendLine("- ${action.actionType}: ${actionRuleLabel(action.ruleId)} ${extractPurpose(action).take(40)}")
                }
            }
            appendLine("通知: 共 $notificationCount 条; 高频来源: ${topNotificationPackages.ifEmpty { listOf("无") }.joinToString("、")}")
            appendLine("今日异常:")
            if (anomalies.isEmpty()) {
                appendLine("- 暂无明显异常")
            } else {
                anomalies.take(6).forEach { anomaly ->
                    appendLine("- [${severityLabel(anomaly.severity)}] ${anomaly.title}: ${anomaly.detail.take(100)}")
                }
            }
            appendLine("诱因标签:")
            if (triggerTags.isEmpty()) {
                appendLine("- 暂无")
            } else {
                triggerTags.take(6).forEach { tag ->
                    appendLine("- ${triggerTagLabel(tag.tag)}: ${tag.count} 次")
                }
            }
            appendLine("内容主题:")
            if (contentThemes.isEmpty()) {
                appendLine("- 暂无")
            } else {
                contentThemes.take(6).forEach { theme ->
                    appendLine("- ${contentThemeLabel(theme.theme)}: ${theme.count} 次")
                }
            }
            appendLine("信息茧房风险: ${if (cocoon.optBoolean("是否")) "是" else "否"}; 主导主题: ${cocoon.optString("主导主题")}; 建议: ${cocoon.optString("建议")}")
            if (hourlySummaries.isNotEmpty()) {
                appendLine("小时摘要:")
                hourlySummaries.takeLast(6).forEach { appendLine("- ${it.take(120)}") }
            }
            appendLine()
            appendLine("输出要求:")
            appendLine("1) 用中文,150-250 字,不要说教。")
            appendLine("2) 分成三段: 亮点、警示、明日建议。明日建议只给 1-2 条具体动作。")
            appendLine("3) 仅输出 JSON,不要 markdown 代码块。格式: {\"summary\":\"...\",\"highlights\":{\"亮点\":\"...\",\"警示\":\"...\",\"明日建议\":\"...\"}}")
        }
    }

    private fun buildMentorPrompt(diagnosisJson: JSONObject): String =
        buildString {
            appendLine("你是 KHUP 的无名导师。把本地诊断 JSON 变成一句对用户有用的话。")
            appendLine(KhupPromptPolicy.WORLDVIEW.trim())
            appendLine(KhupPromptPolicy.MENTOR_STYLE.trim())
            appendLine("本地诊断 JSON:")
            appendLine(diagnosisJson.toString())
            appendLine()
            appendLine("输出要求:")
            appendLine("1) 只输出一句中文，不要 JSON，不要标题，不要换行。")
            appendLine("2) 不超过 200 字。")
            appendLine("3) 不复述数据，不寒暄，不道德审判。")
            appendLine("4) 一次只抓一个点，并给出今晚或明天能执行的创造性异常行动。")
            appendLine("5) 如果异常性质是逃逸性，少建议，多靠近，允许用一个问题收尾。")
        }

    private fun parseMentorLine(raw: String): String =
        raw.trim()
            .removePrefix("```")
            .removeSuffix("```")
            .lineSequence()
            .map { it.trim().trim('"') }
            .firstOrNull { it.isNotBlank() }
            ?.take(MAX_MENTOR_CHARS)
            .orEmpty()

    private fun buildDiagnosisJson(
        totalUsageMs: Long,
        anomalies: List<AttentionAnomaly>,
        triggerTags: List<TriggerTagTotal>,
        contentThemes: List<ContentThemeTotal>,
        cocoon: JSONObject,
    ): JSONObject {
        val patterns = JSONArray()
        anomalies.forEach { anomaly ->
            when (anomaly.type) {
                "late_algorithm_usage" -> patterns.put("深夜长时使用：${anomaly.title}")
                "notification_burst" -> patterns.put("通知暴增：${anomaly.title}")
                "notification_source_burst" -> patterns.put("单一来源高频打断：${anomaly.title}")
                "app_usage_spike" -> patterns.put("App 用时显著偏离：${anomaly.title}")
                "rapid_app_switching" -> patterns.put("快速切屏：${anomaly.detail}")
                "repeated_unlocks" -> patterns.put("重复亮屏：${anomaly.detail}")
                "late_repeated_unlocks" -> patterns.put("睡前重复亮屏：${anomaly.detail}")
                else -> patterns.put("${anomaly.title}: ${anomaly.detail}")
            }
        }

        return JSONObject()
            .put("总时长", formatDuration(totalUsageMs))
            .put("类目超基线", JSONArray())
            .put("内容主题", JSONArray(contentThemes.take(8).map { theme ->
                JSONObject()
                    .put("主题", contentThemeLabel(theme.theme))
                    .put("原始值", theme.theme)
                    .put("次数", theme.count)
                    .put("平均置信度", theme.averageConfidence.toInt())
            }))
            .put("茧房收敛", cocoon)
            .put("异常模式", patterns)
            .put(
                "异常性质",
                anomalyNature(
                    triggerCounts = triggerTags.associate { it.tag to it.count },
                    anomalyTypes = anomalies.map { it.type },
                ),
            )
    }

    private fun anomalyNature(
        triggerCounts: Map<String, Int>,
        anomalyTypes: List<String>,
    ): String {
        val creativeScore =
            (triggerCounts[TriggerTagger.TAG_STUDY_WORK] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_TASK] ?: 0)
        val escapeScore =
            (triggerCounts[TriggerTagger.TAG_ALGORITHMIC] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_SOCIAL] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_PROMOTION] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_EMOTION_ESCAPE] ?: 0) +
                anomalyTypes.count { it in escapeAnomalyTypes } * 3
        return when {
            creativeScore <= 0 && escapeScore <= 0 -> "无明显异常"
            creativeScore > 0 && escapeScore > 0 -> "混合"
            escapeScore > 0 -> "逃逸性"
            else -> "创造性"
        }
    }

    private fun fallbackMentorLine(
        diagnosisJson: JSONObject,
        anomalies: List<AttentionAnomaly>,
        triggerTags: List<TriggerTagTotal>,
    ): String {
        val nature = diagnosisJson.optString("异常性质")
        val mainAnomaly = anomalies.firstOrNull()
        val mainTrigger = triggerTags.firstOrNull()?.let { triggerTagLabel(it.tag) }
        return when {
            nature == "逃逸性" && mainAnomaly?.type == "rapid_app_switching" ->
                "这不是出走，是在入口之间来回换位置；接下来十分钟只留一个动作，做完就把手机放远。"
            nature == "逃逸性" && mainAnomaly?.type == "repeated_unlocks" ->
                "你今天更像是在反复回到手机，而不是完成一次选择；下一次想点亮屏幕前，先坐十秒，看看真正想躲开的是什么。"
            nature == "逃逸性" ->
                "今天最值得守的不是少用几分钟，而是别再用一个刺激替换另一个刺激；今晚把手机放远，留十分钟空白。"
            nature == "创造性" ->
                "今天有一点注意力被放回了创造性异常里；明天保留同一个入口，继续给它一段不被通知切碎的时间。"
            mainTrigger != null ->
                "今天牵引你最多的是$mainTrigger；明天先守住它出现后的第一分钟，别急着顺手进入下一个入口。"
            else ->
                "今天的数据还不足以判断一个清晰模式；明天只记录一件事：你第一次偏离主线时，是被什么带走的。"
        }
    }

    private fun parseLlmOutput(raw: String): ParsedReview {
        val jsonText = extractJsonObject(raw)
        if (jsonText != null) {
            val obj = JSONObject(jsonText)
            val summary = obj.optString("summary").ifBlank { raw.trim() }
            val highlights = obj.optJSONObject("highlights") ?: JSONObject()
            return ParsedReview(summary, highlights)
        }
        return ParsedReview(raw.trim(), JSONObject())
    }

    private fun fallbackReview(
        totalUsageMs: Long,
        topApps: List<AppUsageLine>,
        tasks: List<TaskLine>,
        notificationCount: Int,
        anomalies: List<AttentionAnomaly>,
        triggerTags: List<TriggerTagTotal>,
    ): ParsedReview {
        val done = tasks.count { it.isDone }
        val taskText = if (tasks.isEmpty()) "今天还没有记录主线任务" else "今日主线完成 $done/${tasks.size}"
        val topText = topApps.firstOrNull()?.let { "用时最多的是 ${it.label}，${formatDuration(it.foregroundMs)}" }
            ?: "今天还没有可用的 App 使用记录"
        val anomalyText = anomalies.firstOrNull()?.let { "主要异常是 ${it.title}，${it.detail}" }
            ?: "暂未发现明显异常"
        val triggerText = triggerTags.firstOrNull()?.let { "主要诱因是 ${triggerTagLabel(it.tag)}" }
            ?: "暂未形成明确诱因标签"
        val summary = "亮点：$taskText，可以先把这一项当作今天的基本锚点。警示：总用机时长 ${formatDuration(totalUsageMs)}，$topText，通知共 $notificationCount 条；$anomalyText；$triggerText。明日建议：先写下 1 件必须完成的主线任务；晚上 22:00 后如果继续打开高刺激 App，先写清楚这次打开的目的。"
        return ParsedReview(
            summary = summary,
            highlights = JSONObject()
                .put("亮点", taskText)
                .put("警示", "总用机 ${formatDuration(totalUsageMs)}；$topText；通知 $notificationCount 条；$anomalyText；$triggerText")
                .put("明日建议", "先写 1 件主线任务；22:00 后打开高刺激 App 先写目的"),
        )
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    private fun extractPurpose(action: ActionLog): String {
        if (action.payload.isBlank()) return ""
        return runCatching {
            JSONObject(action.payload).optString("purpose").takeIf { it.isNotBlank() }?.let { "目的: $it" }.orEmpty()
        }.getOrDefault("")
    }

    private fun actionRuleLabel(ruleId: String): String =
        when (ruleId) {
            "algorithm.douyin.daily" -> "抖音超过阈值"
            "algorithm.xiaohongshu.daily" -> "小红书超过阈值"
            else -> ruleId
        }

    private fun severityLabel(severity: Int): String =
        when (severity) {
            0, 1 -> "低"
            2 -> "中"
            else -> "高"
        }

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

    private fun contentThemeLabel(theme: String): String =
        when (theme) {
            ContentThemeTagger.THEME_SOCIAL_RELATION -> "社交关系"
            ContentThemeTagger.THEME_STUDY_COURSE -> "学习课程"
            ContentThemeTagger.THEME_WORK_TASK -> "工作任务"
            ContentThemeTagger.THEME_CONSUMPTION -> "消费种草"
            ContentThemeTagger.THEME_ENTERTAINMENT -> "娱乐内容"
            ContentThemeTagger.THEME_REWARD_LOOP -> "奖励循环"
            ContentThemeTagger.THEME_CONFLICT_NEWS -> "争议新闻"
            ContentThemeTagger.THEME_STATUS_ANXIETY -> "状态焦虑"
            ContentThemeTagger.THEME_SPORTS_GAME -> "比赛观赛"
            ContentThemeTagger.THEME_ALGORITHM_FEED -> "算法内容"
            else -> theme
        }

    private fun startOfDayMs(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
    }

    private data class AppUsageLine(
        val label: String,
        val packageName: String,
        val foregroundMs: Long,
    )

    private data class TaskLine(
        val title: String,
        val isDone: Boolean,
    )

    private data class ParsedReview(
        val summary: String,
        val highlights: JSONObject,
    )

    private companion object {
        private const val TAG = "KHUP/DailyReview"
        private const val TOP_APP_LIMIT = 5
        private const val ACTION_LIMIT = 20
        private const val MODEL_VERSION = "daily-review-v3"
        private const val KEY_MENTOR_LINE = "无名导师"
        private const val MAX_MENTOR_CHARS = 200
        private const val MIN_TRIGGER_COUNT = 5
        private const val COCOON_RATIO = 0.6
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val escapeAnomalyTypes = setOf(
            "late_algorithm_usage",
            "notification_burst",
            "notification_source_burst",
            "app_usage_spike",
            "rapid_app_switching",
            "repeated_unlocks",
            "late_repeated_unlocks",
        )
    }
}
