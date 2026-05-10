package com.kian.khup.core.content

import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.ContentThemeTotal
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class InformationCocoonAnalyzer @Inject constructor(
    private val contentThemeTagDao: ContentThemeTagDao,
) {
    suspend fun analyzeToday(dayStartMs: Long): JSONObject = withContext(Dispatchers.IO) {
        val todayThemes = contentThemeTagDao.loadThemeTotalsForDay(dayStartMs)
        val weeklyThemes = contentThemeTagDao.loadDailyThemeTotalsSince(dayStartMs - DAY_MS * 6)
        val total = todayThemes.sumOf { it.count }
        val dominant = todayThemes.firstOrNull()
        val ratio = if (dominant != null && total > 0) dominant.count.toDouble() / total.toDouble() else 0.0
        val consecutiveDays = dominant?.let { theme ->
            countRecentDominantDays(
                dayStartMs = dayStartMs,
                theme = theme.theme,
                dailyThemes = weeklyThemes,
            )
        } ?: 0
        val isCocoon = total >= MIN_THEME_COUNT &&
            ratio >= DOMINANT_RATIO &&
            (consecutiveDays >= MIN_CONSECUTIVE_DAYS || dominantThemeIsHighPull(dominant?.theme))

        JSONObject()
            .put("是否", isCocoon)
            .put("主导主题", dominant?.let { themeLabel(it.theme) }.orEmpty())
            .put("主导主题原始值", dominant?.theme.orEmpty())
            .put("占比", if (total > 0) "${(ratio * 100).toInt()}%" else "")
            .put("连续主导天数", consecutiveDays)
            .put("主题分布", JSONArray(todayThemes.take(6).map(::themeTotalJson)))
            .put("建议", if (isCocoon) interventionFor(dominant?.theme) else "")
    }

    private fun countRecentDominantDays(
        dayStartMs: Long,
        theme: String,
        dailyThemes: List<com.kian.khup.core.data.db.DailyContentThemeTotal>,
    ): Int {
        val topThemeByDay = dailyThemes
            .groupBy { it.dayStartMs }
            .mapValues { (_, themes) -> themes.maxByOrNull { it.count }?.theme }
        var count = 0
        var cursor = dayStartMs
        while (topThemeByDay[cursor] == theme) {
            count += 1
            cursor -= DAY_MS
        }
        return count
    }

    private fun dominantThemeIsHighPull(theme: String?): Boolean =
        theme in setOf(
            ContentThemeTagger.THEME_ALGORITHM_FEED,
            ContentThemeTagger.THEME_ENTERTAINMENT,
            ContentThemeTagger.THEME_REWARD_LOOP,
            ContentThemeTagger.THEME_CONFLICT_NEWS,
            ContentThemeTagger.THEME_STATUS_ANXIETY,
            ContentThemeTagger.THEME_CONSUMPTION,
        )

    private fun interventionFor(theme: String?): String =
        when (theme) {
            ContentThemeTagger.THEME_CONFLICT_NEWS ->
                "先别继续追同类标题，写下这件事你还没确认的 3 个事实。"
            ContentThemeTagger.THEME_REWARD_LOOP ->
                "把奖励入口冷却 2 小时，下一次打开前先写目的。"
            ContentThemeTagger.THEME_CONSUMPTION ->
                "今天同类消费线索偏集中，先延迟购买 24 小时。"
            ContentThemeTagger.THEME_ENTERTAINMENT,
            ContentThemeTagger.THEME_ALGORITHM_FEED ->
                "把同类内容冷却 1 小时，换成一个更慢的输入：深读、写作或散步。"
            ContentThemeTagger.THEME_STATUS_ANXIETY ->
                "先停下比较，把焦虑拆成一个明天能推进的具体动作。"
            else ->
                "先暂停同类入口 1 小时，问一句：我是在解决问题，还是在继续找刺激？"
        }

    private fun themeTotalJson(total: ContentThemeTotal): JSONObject =
        JSONObject()
            .put("主题", themeLabel(total.theme))
            .put("原始值", total.theme)
            .put("次数", total.count)
            .put("平均置信度", total.averageConfidence.toInt())

    private fun themeLabel(theme: String): String =
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

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MIN_THEME_COUNT = 8
        private const val DOMINANT_RATIO = 0.55
        private const val MIN_CONSECUTIVE_DAYS = 2
    }
}
