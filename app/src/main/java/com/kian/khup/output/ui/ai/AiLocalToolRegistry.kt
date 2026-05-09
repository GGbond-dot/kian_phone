package com.kian.khup.output.ui.ai

import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.trigger.TriggerTagger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AiLocalToolRegistry @Inject constructor(
    private val usageStatsCollector: UsageStatsCollector,
    private val appSessionDao: AppSessionDao,
    private val eventDao: EventDao,
    private val derivedResultDao: DerivedResultDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val triggerTagDao: TriggerTagDao,
    private val triggerTagger: TriggerTagger,
    private val dailyReviewDao: DailyReviewDao,
) {
    suspend fun runFor(userText: String): AiToolRun? = withContext(Dispatchers.IO) {
        val calls = AiToolSelector.select(userText)
        if (calls.isEmpty()) return@withContext null

        val now = System.currentTimeMillis()
        val dayStart = startOfDayMs(now)
        runCatching { triggerTagger.refreshToday() }

        AiToolRun(
            generatedAt = now,
            dayStart = dayStart,
            calls = calls,
            results = calls.map { call -> execute(call, dayStart, now, userText) },
        )
    }

    private suspend fun execute(
        call: AiToolCall,
        dayStart: Long,
        now: Long,
        userText: String,
    ): AiToolResult =
        when (call.name) {
            AiLocalToolName.GetTodayUsage -> getTodayUsage(call, dayStart, now, userText)
            AiLocalToolName.GetTodayAnomalies -> getTodayAnomalies(call, dayStart)
            AiLocalToolName.GetTodayTriggers -> getTodayTriggers(call, dayStart)
            AiLocalToolName.GetTodayNotifications -> getTodayNotifications(call, dayStart, now)
            AiLocalToolName.GetTodayReview -> getTodayReview(call, dayStart)
            AiLocalToolName.GetTodayDiagnosis -> getTodayDiagnosis(call, dayStart, now)
            AiLocalToolName.GetTodayContext -> getTodayContext(call, dayStart, now, userText)
        }

    private suspend fun getTodayUsage(
        call: AiToolCall,
        dayStart: Long,
        now: Long,
        userText: String,
    ): AiToolResult {
        val packages = targetPackages(userText)
        val lines = mutableListOf<String>()
        if (packages.isEmpty()) {
            val topApps = appSessionDao.loadTopUsageSince(dayStart, 8)
            if (topApps.isEmpty()) {
                lines += "top_apps=[]"
            } else {
                lines += "top_apps=["
                topApps.forEach {
                    lines += "  {label=\"${usageStatsCollector.resolveAppLabel(it.packageName)}\", package=\"${it.packageName}\", foreground=\"${formatDuration(it.foregroundMs)}\"}"
                }
                lines += "]"
            }
        } else {
            lines += "requested_apps=["
            packages.forEach { packageName ->
                val usedMs = appSessionDao.getUsageForPackagesSince(listOf(packageName), dayStart)
                lines += "  {label=\"${usageStatsCollector.resolveAppLabel(packageName)}\", package=\"$packageName\", foreground=\"${formatDuration(usedMs)}\"}"
            }
            lines += "]"
        }
        val screenMs = usageStatsCollector.getScreenInteractiveMs(dayStart, now).coerceAtMost(now - dayStart)
        lines += "screen_interactive=\"${formatDuration(screenMs)}\""
        return AiToolResult(call, lines.joinToString("\n"))
    }

    private suspend fun getTodayAnomalies(call: AiToolCall, dayStart: Long): AiToolResult {
        val anomalies = attentionAnomalyDao.loadForDay(dayStart)
        val output = if (anomalies.isEmpty()) {
            "anomalies=[]"
        } else {
            buildString {
                appendLine("anomalies=[")
                anomalies.take(8).forEach {
                    appendLine("  {severity=\"${severityLabel(it.severity)}\", title=\"${it.title}\", detail=\"${it.detail}\"}")
                }
                append("]")
            }
        }
        return AiToolResult(call, output)
    }

    private suspend fun getTodayTriggers(call: AiToolCall, dayStart: Long): AiToolResult {
        val tags = triggerTagDao.loadTagTotalsForDay(dayStart)
        val output = if (tags.isEmpty()) {
            "triggers=[]"
        } else {
            buildString {
                appendLine("triggers=[")
                tags.take(8).forEach {
                    appendLine("  {tag=\"${triggerTagLabel(it.tag)}\", rawTag=\"${it.tag}\", count=${it.count}, avgConfidence=${it.averageConfidence.toInt()}}")
                }
                append("]")
            }
        }
        return AiToolResult(call, output)
    }

    private suspend fun getTodayNotifications(call: AiToolCall, dayStart: Long, now: Long): AiToolResult {
        val notifications = eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now)
        val packages = eventDao.loadTopPackagesInWindow(EventType.NOTIFICATION_POSTED, dayStart, now, 6)
        val classifications = derivedResultDao.loadClassificationTotalsInWindow(dayStart, now, 8)
        val output = buildString {
            appendLine("notification_count=${notifications.size}")
            appendLine("top_sources=[")
            packages.forEach {
                appendLine("  {label=\"${usageStatsCollector.resolveAppLabel(it.packageName)}\", package=\"${it.packageName}\", count=${it.count}}")
            }
            appendLine("]")
            appendLine("classifications=[")
            classifications.forEach { appendLine("  {classification=\"${it.classification}\", count=${it.count}}") }
            append("]")
        }
        return AiToolResult(call, output)
    }

    private suspend fun getTodayReview(call: AiToolCall, dayStart: Long): AiToolResult {
        val review = dailyReviewDao.findForDay(dayStart)
        val output = review?.let { "review=\"${it.summary}\"" } ?: "review=null"
        return AiToolResult(call, output)
    }

    private suspend fun getTodayDiagnosis(call: AiToolCall, dayStart: Long, now: Long): AiToolResult {
        val totalMs = usageStatsCollector.getScreenInteractiveMs(dayStart, now).coerceAtMost(now - dayStart)
        val todayCategoryMs = usageStatsCollector.getTopApps(dayStart, now, Int.MAX_VALUE)
            .groupBy { categoryForPackage(it.packageName) }
            .mapValues { entry -> entry.value.sumOf { it.foregroundMs } }
        val baselineByCategory = loadSevenDayCategoryMedians(dayStart)
        val categorySpikes = todayCategoryMs.mapNotNull { (category, todayMs) ->
            val medianMs = baselineByCategory[category] ?: return@mapNotNull null
            if (medianMs < MIN_BASELINE_MS || todayMs < MIN_SPIKE_MS) return@mapNotNull null
            val ratio = todayMs.toDouble() / medianMs.toDouble()
            if (ratio >= CATEGORY_SPIKE_RATIO) {
                JSONObject()
                    .put("类目", category)
                    .put("今日", formatDuration(todayMs))
                    .put("中位数", formatDuration(medianMs))
                    .put("倍数", String.format(Locale.ROOT, "%.1f", ratio).toDouble())
            } else {
                null
            }
        }.sortedByDescending { it.optDouble("倍数") }

        val triggers = triggerTagDao.loadTagTotalsForDay(dayStart)
        val totalTriggerCount = triggers.sumOf { it.count }.coerceAtLeast(0)
        val dominant = triggers.maxByOrNull { it.count }
        val cocoonRatio = if (dominant != null && totalTriggerCount > 0) {
            dominant.count.toDouble() / totalTriggerCount.toDouble()
        } else {
            0.0
        }
        val cocoon = JSONObject()
            .put("是否", totalTriggerCount >= MIN_TRIGGER_COUNT && cocoonRatio >= COCOON_RATIO)
            .put("主导标签", dominant?.let { triggerTagLabel(it.tag) }.orEmpty())
            .put("占比", if (totalTriggerCount > 0) "${(cocoonRatio * 100).toInt()}%" else "")

        val anomalies = attentionAnomalyDao.loadForDay(dayStart)
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

        val nature = anomalyNature(
            categorySpikes = categorySpikes,
            triggerCounts = triggers.associate { it.tag to it.count },
            anomalies = anomalies.map { it.type },
        )

        val output = JSONObject()
            .put("总时长", formatDuration(totalMs))
            .put("类目超基线", JSONArray(categorySpikes))
            .put("茧房收敛", cocoon)
            .put("异常模式", patterns)
            .put("异常性质", nature)
            .toString()
        return AiToolResult(call, output)
    }

    private suspend fun getTodayContext(
        call: AiToolCall,
        dayStart: Long,
        now: Long,
        userText: String,
    ): AiToolResult {
        val subResults = listOf(
            getTodayDiagnosis(AiToolCall(AiLocalToolName.GetTodayDiagnosis), dayStart, now),
            getTodayUsage(AiToolCall(AiLocalToolName.GetTodayUsage), dayStart, now, userText),
            getTodayAnomalies(AiToolCall(AiLocalToolName.GetTodayAnomalies), dayStart),
            getTodayTriggers(AiToolCall(AiLocalToolName.GetTodayTriggers), dayStart),
            getTodayNotifications(AiToolCall(AiLocalToolName.GetTodayNotifications), dayStart, now),
            getTodayReview(AiToolCall(AiLocalToolName.GetTodayReview), dayStart),
        )
        return AiToolResult(
            call = call,
            content = subResults.joinToString("\n\n") { result ->
                "[${result.call.name.wireName}]\n${result.content}"
            },
        )
    }

    private fun categoryForPackage(packageName: String): String =
        when (packageName) {
            in algorithmPackages -> "算法内容"
            in socialPackages -> "社交"
            in studyWorkPackages -> "学习工作"
            in shoppingPackages -> "消费"
            in financePackages -> "必要事务"
            else -> "其他"
        }

    private fun loadSevenDayCategoryMedians(dayStart: Long): Map<String, Long> {
        val values = mutableMapOf<String, MutableList<Long>>()
        repeat(7) { index ->
            val start = dayStart - DAY_MS * (index + 1)
            val end = start + DAY_MS
            usageStatsCollector.getTopApps(start, end, Int.MAX_VALUE)
                .groupBy { categoryForPackage(it.packageName) }
                .mapValues { entry -> entry.value.sumOf { it.foregroundMs } }
                .forEach { (category, foregroundMs) ->
                    if (foregroundMs > 0) values.getOrPut(category) { mutableListOf() } += foregroundMs
                }
        }
        return values.mapValues { (_, list) -> median(list) }
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun anomalyNature(
        categorySpikes: List<JSONObject>,
        triggerCounts: Map<String, Int>,
        anomalies: List<String>,
    ): String {
        val creativeScore =
            (triggerCounts[TriggerTagger.TAG_STUDY_WORK] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_TASK] ?: 0)
        val escapeScore =
            (triggerCounts[TriggerTagger.TAG_ALGORITHMIC] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_SOCIAL] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_PROMOTION] ?: 0) +
                (triggerCounts[TriggerTagger.TAG_EMOTION_ESCAPE] ?: 0) +
                anomalies.count { it in escapeAnomalyTypes } * 3 +
                categorySpikes.count { it.optString("类目") in escapeCategories } * 2
        return when {
            creativeScore <= 0 && escapeScore <= 0 -> "无明显异常"
            creativeScore > 0 && escapeScore > 0 -> "混合"
            escapeScore > 0 -> "逃逸性"
            else -> "创造性"
        }
    }

    private fun targetPackages(text: String): List<String> = buildList {
        if (text.contains("抖音") || text.contains("douyin", ignoreCase = true)) add("com.ss.android.ugc.aweme")
        if (text.contains("小红书") || text.contains("xhs", ignoreCase = true)) add("com.xingin.xhs")
        if (text.contains("微信")) add("com.tencent.mm")
        if (text.contains("qq", ignoreCase = true) || text.contains("QQ")) add("com.tencent.mobileqq")
        if (text.contains("微博")) add("com.sina.weibo")
        if (text.contains("知乎")) add("com.zhihu.android")
    }.distinct()

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

    companion object {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_TRIGGER_COUNT = 5
        private const val COCOON_RATIO = 0.6
        private const val CATEGORY_SPIKE_RATIO = 1.8
        private const val MIN_BASELINE_MS = 10L * 60L * 1000L
        private const val MIN_SPIKE_MS = 20L * 60L * 1000L
        private val algorithmPackages = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.sina.weibo",
            "com.kuaishou.nebula",
        )
        private val socialPackages = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.instagram.android",
        )
        private val studyWorkPackages = setOf(
            "com.alibaba.android.rimet",
            "com.zmzx.college.search",
            "com.yiban.app",
            "com.nowcoder.app.florida",
        )
        private val shoppingPackages = setOf(
            "com.taobao.taobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
        )
        private val financePackages = setOf(
            "com.eg.android.AlipayGphone",
            "com.unionpay",
            "com.icbc",
            "com.icbc.im",
            "cmb.pb",
            "com.cmbchina.ccd.pluto.cmbActivity",
            "com.chinamworld.main",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "com.bankcomm.Bankcomm",
            "cn.com.spdb.mobilebank.per",
            "com.cmbc.cc.mbank",
        )
        private val escapeAnomalyTypes = setOf(
            "late_algorithm_usage",
            "notification_burst",
            "notification_source_burst",
            "app_usage_spike",
            "rapid_app_switching",
            "repeated_unlocks",
            "late_repeated_unlocks",
        )
        private val escapeCategories = setOf("算法内容", "社交", "消费")
    }
}

data class AiToolRun(
    val generatedAt: Long,
    val dayStart: Long,
    val calls: List<AiToolCall>,
    val results: List<AiToolResult>,
) {
    fun toPromptContext(): String = buildString {
        appendLine("本地工具调用结果：")
        appendLine("统计日期：${AiLocalToolRegistry.dayFmt.format(Date(dayStart))}，截止：${AiLocalToolRegistry.timeFmt.format(Date(generatedAt))}")
        appendLine("使用规则：只能依据工具结果回答；工具结果为空或 null 时明确说本地暂无记录，不要猜。")
        results.forEach { result ->
            appendLine()
            appendLine("tool=${result.call.name.wireName}")
            if (result.call.arguments.isNotEmpty()) appendLine("arguments=${result.call.arguments}")
            appendLine(result.content)
        }
    }.trim()
}

data class AiToolCall(
    val name: AiLocalToolName,
    val arguments: Map<String, String> = emptyMap(),
)

data class AiToolResult(
    val call: AiToolCall,
    val content: String,
)

enum class AiLocalToolName(val wireName: String) {
    GetTodayUsage("get_today_usage"),
    GetTodayAnomalies("get_today_anomalies"),
    GetTodayTriggers("get_today_triggers"),
    GetTodayNotifications("get_today_notifications"),
    GetTodayReview("get_today_review"),
    GetTodayDiagnosis("get_today_diagnosis"),
    GetTodayContext("get_today_context"),
}

private object AiToolSelector {
    fun select(text: String): List<AiToolCall> {
        val lower = text.lowercase(Locale.ROOT)
        val tool = when {
            hasAny(lower, contextKeywords) -> AiLocalToolName.GetTodayContext
            hasAny(lower, diagnosisKeywords) -> AiLocalToolName.GetTodayDiagnosis
            hasAny(lower, anomalyKeywords) -> AiLocalToolName.GetTodayAnomalies
            hasAny(lower, triggerKeywords) -> AiLocalToolName.GetTodayTriggers
            hasAny(lower, notificationKeywords) -> AiLocalToolName.GetTodayNotifications
            hasAny(lower, reviewKeywords) -> AiLocalToolName.GetTodayReview
            hasAny(lower, usageKeywords) || targetAppKeywords.any { lower.contains(it) } -> AiLocalToolName.GetTodayUsage
            else -> null
        } ?: return emptyList()
        return listOf(AiToolCall(tool))
    }

    private fun hasAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }

    private val contextKeywords = setOf("为什么失控", "今天怎么样", "今天状态", "守哪条防线", "明天计划")
    private val diagnosisKeywords = setOf("诊断", "异常性质", "创造性异常", "逃逸性异常", "茧房", "基线", "超基线")
    private val anomalyKeywords = setOf("异常", "风险", "失控")
    private val triggerKeywords = setOf("诱因", "牵着走", "为什么", "原因", "打断")
    private val notificationKeywords = setOf("通知", "消息最多", "谁发", "来源")
    private val reviewKeywords = setOf("复盘", "总结")
    private val usageKeywords = setOf("用机", "使用", "用了多久", "刷了多久", "多久", "时长", "top app")
    private val targetAppKeywords = setOf("抖音", "小红书", "微信", "qq", "微博", "知乎", "douyin", "xhs")
}
