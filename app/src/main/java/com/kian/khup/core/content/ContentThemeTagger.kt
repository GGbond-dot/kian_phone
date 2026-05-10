package com.kian.khup.core.content

import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.entities.ContentThemeTag
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.HourlySummary
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContentThemeTagger @Inject constructor(
    private val eventDao: EventDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val appSessionDao: AppSessionDao,
    private val contentThemeTagDao: ContentThemeTagDao,
) {
    suspend fun refreshToday(): List<ContentThemeTag> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayStart = startOfDayMs(now)
        refreshDay(dayStart, now)
    }

    suspend fun refreshDay(dayStart: Long, now: Long = System.currentTimeMillis()): List<ContentThemeTag> =
        withContext(Dispatchers.IO) {
            val tags = buildList {
                addAll(tagNotifications(dayStart, now))
                addAll(tagHourlySummaries(dayStart, now))
                addAll(tagAppUsage(dayStart, now))
            }
            contentThemeTagDao.deleteForDay(dayStart)
            if (tags.isNotEmpty()) contentThemeTagDao.upsertAll(tags)
            tags
        }

    private suspend fun tagNotifications(dayStart: Long, now: Long): List<ContentThemeTag> =
        eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now).flatMap { event ->
            val text = event.searchableText()
            themesFor(text, event.packageName).map { match ->
                ContentThemeTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_NOTIFICATION,
                    sourceId = event.eventId,
                    packageName = event.packageName,
                    theme = match.theme,
                    confidence = match.confidence,
                    evidence = match.evidence,
                    createdAt = event.timestamp,
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private suspend fun tagHourlySummaries(dayStart: Long, now: Long): List<ContentThemeTag> =
        hourlySummaryDao.loadInWindow(dayStart, now).flatMap { summary ->
            themesFor(summary.summary, packageName = null).map { match ->
                ContentThemeTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_HOURLY_SUMMARY,
                    sourceId = summary.windowStartMs.toString(),
                    packageName = null,
                    theme = match.theme,
                    confidence = (match.confidence - 8).coerceAtLeast(55),
                    evidence = match.evidence,
                    createdAt = summary.createdAt,
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private suspend fun tagAppUsage(dayStart: Long, now: Long): List<ContentThemeTag> =
        appSessionDao.loadTopUsageSince(dayStart, APP_USAGE_LIMIT).flatMap { usage ->
            val themes = themesForPackage(usage.packageName)
            themes.map { theme ->
                ContentThemeTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_APP_USAGE,
                    sourceId = usage.packageName,
                    packageName = usage.packageName,
                    theme = theme,
                    confidence = if (usage.foregroundMs >= STRONG_USAGE_MS) 78 else 66,
                    evidence = "今日前台使用 ${formatDuration(usage.foregroundMs)}",
                    createdAt = now,
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private fun themesFor(text: String, packageName: String?): List<ThemeMatch> {
        val normalized = text.lowercase(Locale.ROOT)
        val matches = themeRules.mapNotNull { rule ->
            val hit = rule.keywords.firstOrNull { normalized.contains(it) }
            hit?.let {
                ThemeMatch(
                    theme = rule.theme,
                    confidence = rule.confidence,
                    evidence = "命中关键词：$it",
                )
            }
        }.toMutableList()

        themesForPackage(packageName).forEach { theme ->
            if (matches.none { it.theme == theme }) {
                matches += ThemeMatch(theme, 62, "来源 App 推断")
            }
        }

        return matches
            .distinctBy { it.theme }
            .sortedByDescending { it.confidence }
            .take(MAX_THEMES_PER_SOURCE)
    }

    private fun themesForPackage(packageName: String?): List<String> =
        when (packageName) {
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.kuaishou.nebula",
            "com.sina.weibo",
            "com.zhihu.android" -> listOf(THEME_ALGORITHM_FEED, THEME_ENTERTAINMENT)
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.instagram.android" -> listOf(THEME_SOCIAL_RELATION)
            "com.alibaba.android.rimet",
            "com.zmzx.college.search",
            "com.yiban.app",
            "com.nowcoder.app.florida" -> listOf(THEME_STUDY_COURSE, THEME_WORK_TASK)
            "com.taobao.taobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo" -> listOf(THEME_CONSUMPTION)
            else -> emptyList()
        }

    private fun Event.searchableText(): String =
        listOfNotNull(title, text, subText, bigText, channelId, category)
            .joinToString(" ")

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

    companion object {
        const val THEME_SOCIAL_RELATION = "social_relation"
        const val THEME_STUDY_COURSE = "study_course"
        const val THEME_WORK_TASK = "work_task"
        const val THEME_CONSUMPTION = "consumption"
        const val THEME_ENTERTAINMENT = "entertainment"
        const val THEME_REWARD_LOOP = "reward_loop"
        const val THEME_CONFLICT_NEWS = "conflict_news"
        const val THEME_STATUS_ANXIETY = "status_anxiety"
        const val THEME_SPORTS_GAME = "sports_game"
        const val THEME_ALGORITHM_FEED = "algorithm_feed"

        private const val RULE_VERSION = "content-theme-v1"
        private const val SOURCE_NOTIFICATION = "notification"
        private const val SOURCE_HOURLY_SUMMARY = "hourly_summary"
        private const val SOURCE_APP_USAGE = "app_usage"
        private const val APP_USAGE_LIMIT = 30
        private const val MAX_THEMES_PER_SOURCE = 3
        private val STRONG_USAGE_MS = TimeUnit.MINUTES.toMillis(30)

        private val themeRules = listOf(
            ThemeRule(
                THEME_STUDY_COURSE,
                88,
                setOf("课程", "作业", "考试", "学习", "题", "资料", "课堂", "微机", "算法", "复习", "论文"),
            ),
            ThemeRule(
                THEME_WORK_TASK,
                84,
                setOf("会议", "审批", "任务", "截止", "项目", "工作", "钉钉", "打卡", "提交"),
            ),
            ThemeRule(
                THEME_SOCIAL_RELATION,
                82,
                setOf("微信", "qq", "聊天", "回复", "好友", "群", "同学", "朋友", "家人", "消息"),
            ),
            ThemeRule(
                THEME_CONSUMPTION,
                82,
                setOf("优惠", "订单", "支付", "促销", "购买", "淘宝", "京东", "拼多多", "券", "红包", "种草"),
            ),
            ThemeRule(
                THEME_REWARD_LOOP,
                80,
                setOf("签到", "奖励", "抽奖", "限时", "连续", "领取", "积分", "福利", "红包"),
            ),
            ThemeRule(
                THEME_CONFLICT_NEWS,
                78,
                setOf("争议", "怒", "爆", "冲突", "吵", "骂", "辟谣", "热搜", "新闻", "对立"),
            ),
            ThemeRule(
                THEME_STATUS_ANXIETY,
                78,
                setOf("排名", "成绩", "绩点", "offer", "简历", "面试", "工资", "赚钱", "成长", "焦虑"),
            ),
            ThemeRule(
                THEME_ENTERTAINMENT,
                76,
                setOf("视频", "直播", "游戏", "电影", "音乐", "娱乐", "抖音", "小红书", "微博", "番剧"),
            ),
            ThemeRule(
                THEME_SPORTS_GAME,
                74,
                setOf("比赛", "篮球", "足球", "nba", "赛程", "比分", "观赛", "球员"),
            ),
        )
    }

    private data class ThemeRule(
        val theme: String,
        val confidence: Int,
        val keywords: Set<String>,
    )

    private data class ThemeMatch(
        val theme: String,
        val confidence: Int,
        val evidence: String,
    )
}
