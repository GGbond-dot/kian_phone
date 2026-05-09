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

@Singleton
class AiLocalContextBuilder @Inject constructor(
    private val usageStatsCollector: UsageStatsCollector,
    private val appSessionDao: AppSessionDao,
    private val eventDao: EventDao,
    private val derivedResultDao: DerivedResultDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val triggerTagDao: TriggerTagDao,
    private val triggerTagger: TriggerTagger,
    private val dailyReviewDao: DailyReviewDao,
) {
    suspend fun buildFor(userText: String): String? = withContext(Dispatchers.IO) {
        val intent = QueryIntent.from(userText)
        if (intent == QueryIntent.None) return@withContext null

        val now = System.currentTimeMillis()
        val dayStart = startOfDayMs(now)
        runCatching { triggerTagger.refreshToday() }

        buildString {
            appendLine("本地数据上下文：")
            appendLine("统计日期：${dayFmt.format(Date(dayStart))}，截止：${timeFmt.format(Date(now))}")
            appendLine("使用规则：只能依据下面的数据回答；没有数据时明确说本地暂无记录，不要猜。")

            if (intent.includes(QueryIntent.Usage)) appendUsage(dayStart, now, userText)
            if (intent.includes(QueryIntent.Anomaly)) appendAnomalies(dayStart)
            if (intent.includes(QueryIntent.Trigger)) appendTriggerTags(dayStart)
            if (intent.includes(QueryIntent.Notification)) appendNotifications(dayStart, now)
            if (intent.includes(QueryIntent.Review)) appendReview(dayStart)
        }.trim()
    }

    private suspend fun StringBuilder.appendUsage(dayStart: Long, now: Long, userText: String) {
        appendLine("今日用机：")
        val targetPackages = targetPackages(userText)
        if (targetPackages.isNotEmpty()) {
            targetPackages.forEach { packageName ->
                val usedMs = appSessionDao.getUsageForPackagesSince(listOf(packageName), dayStart)
                appendLine("- ${usageStatsCollector.resolveAppLabel(packageName)}($packageName): ${formatDuration(usedMs)}")
            }
        } else {
            val topApps = appSessionDao.loadTopUsageSince(dayStart, 8)
            if (topApps.isEmpty()) {
                appendLine("- 本地暂无今日 App 前台使用记录。")
            } else {
                topApps.forEach {
                    appendLine("- ${usageStatsCollector.resolveAppLabel(it.packageName)}(${it.packageName}): ${formatDuration(it.foregroundMs)}")
                }
            }
        }
        val screenMs = usageStatsCollector.getScreenInteractiveMs(dayStart, now).coerceAtMost(now - dayStart)
        appendLine("- 今日屏幕交互总时长: ${formatDuration(screenMs)}")
    }

    private suspend fun StringBuilder.appendAnomalies(dayStart: Long) {
        appendLine("今日异常：")
        val anomalies = attentionAnomalyDao.loadForDay(dayStart)
        if (anomalies.isEmpty()) {
            appendLine("- 本地暂无今日异常记录。")
        } else {
            anomalies.take(8).forEach {
                appendLine("- [${severityLabel(it.severity)}] ${it.title}: ${it.detail}")
            }
        }
    }

    private suspend fun StringBuilder.appendTriggerTags(dayStart: Long) {
        appendLine("今日诱因标签：")
        val tags = triggerTagDao.loadTagTotalsForDay(dayStart)
        if (tags.isEmpty()) {
            appendLine("- 本地暂无明确诱因标签。")
        } else {
            tags.take(8).forEach {
                appendLine("- ${triggerTagLabel(it.tag)}: ${it.count} 次，平均置信度 ${it.averageConfidence.toInt()}")
            }
        }
    }

    private suspend fun StringBuilder.appendNotifications(dayStart: Long, now: Long) {
        appendLine("今日通知：")
        val notifications = eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now)
        appendLine("- 总数: ${notifications.size} 条")
        val packages = eventDao.loadTopPackagesInWindow(EventType.NOTIFICATION_POSTED, dayStart, now, 6)
        if (packages.isNotEmpty()) {
            appendLine("- 高频来源:")
            packages.forEach {
                appendLine("  - ${usageStatsCollector.resolveAppLabel(it.packageName)}(${it.packageName}): ${it.count} 条")
            }
        }
        val classifications = derivedResultDao.loadClassificationTotalsInWindow(dayStart, now, 8)
        if (classifications.isNotEmpty()) {
            appendLine("- 分类分布:")
            classifications.forEach { appendLine("  - ${it.classification}: ${it.count} 条") }
        }
    }

    private suspend fun StringBuilder.appendReview(dayStart: Long) {
        appendLine("今日复盘：")
        val review = dailyReviewDao.findForDay(dayStart)
        if (review == null) {
            appendLine("- 本地还没有生成今日复盘。")
        } else {
            appendLine("- ${review.summary}")
        }
    }

    private fun QueryIntent.includes(other: QueryIntent): Boolean =
        this == QueryIntent.All || this == other

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

    private enum class QueryIntent {
        None,
        Usage,
        Anomaly,
        Trigger,
        Notification,
        Review,
        All;

        companion object {
            fun from(text: String): QueryIntent {
                val lower = text.lowercase(Locale.ROOT)
                return when {
                    hasAny(lower, allKeywords) -> All
                    hasAny(lower, anomalyKeywords) -> Anomaly
                    hasAny(lower, triggerKeywords) -> Trigger
                    hasAny(lower, notificationKeywords) -> Notification
                    hasAny(lower, reviewKeywords) -> Review
                    hasAny(lower, usageKeywords) || targetAppKeywords.any { lower.contains(it) } -> Usage
                    else -> None
                }
            }

            private fun hasAny(text: String, keywords: Set<String>): Boolean =
                keywords.any { text.contains(it) }

            private val allKeywords = setOf("为什么失控", "今天怎么样", "今天状态", "守哪条防线", "明天计划")
            private val anomalyKeywords = setOf("异常", "风险", "失控")
            private val triggerKeywords = setOf("诱因", "牵着走", "为什么", "原因", "打断")
            private val notificationKeywords = setOf("通知", "消息最多", "谁发", "来源")
            private val reviewKeywords = setOf("复盘", "总结")
            private val usageKeywords = setOf("用机", "使用", "用了多久", "刷了多久", "多久", "时长", "top app")
            private val targetAppKeywords = setOf("抖音", "小红书", "微信", "qq", "微博", "知乎", "douyin", "xhs")
        }
    }

    private companion object {
        private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
