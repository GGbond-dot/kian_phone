package com.kian.khup.core.trigger

import com.kian.khup.core.classification.RuleNotificationClassifier
import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.TriggerTag
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class TriggerTagger @Inject constructor(
    private val eventDao: EventDao,
    private val derivedResultDao: DerivedResultDao,
    private val appSessionDao: AppSessionDao,
    private val actionLogDao: ActionLogDao,
    private val triggerTagDao: TriggerTagDao,
    private val notificationClassifier: RuleNotificationClassifier,
) {
    suspend fun refreshToday(): List<TriggerTag> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayStart = startOfDayMs(now)
        refreshDay(dayStart, now)
    }

    suspend fun refreshDay(dayStart: Long, now: Long = System.currentTimeMillis()): List<TriggerTag> =
        withContext(Dispatchers.IO) {
            val tags = buildList {
                addAll(tagNotifications(dayStart, now))
                addAll(tagAppUsage(dayStart))
                addAll(tagActions(dayStart))
            }
            triggerTagDao.deleteForDay(dayStart)
            triggerTagDao.upsertAll(tags)
            tags
        }

    private suspend fun tagNotifications(dayStart: Long, now: Long): List<TriggerTag> =
        eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now).flatMap { event ->
            val classification = classificationFor(event)
            tagsForNotificationClassification(classification).map { tag ->
                TriggerTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_NOTIFICATION,
                    sourceId = event.eventId,
                    packageName = event.packageName,
                    tag = tag,
                    confidence = confidenceForNotification(classification, tag),
                    reason = "通知分类：$classification",
                    createdAt = now,
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private suspend fun classificationFor(event: Event): String {
        val stored = derivedResultDao.getClassification(event.eventId)
        if (!stored.isNullOrBlank()) {
            return if (stored == "金融通知") "消费信息" else stored
        }
        val classified = notificationClassifier.classify(event)
        derivedResultDao.upsert(classified)
        return classified.classification
    }

    private suspend fun tagAppUsage(dayStart: Long): List<TriggerTag> =
        appSessionDao.loadTopUsageSince(dayStart, APP_USAGE_LIMIT).flatMap { usage ->
            val baseTags = tagsForPackage(usage.packageName).toMutableList()
            if (usage.foregroundMs >= EMOTION_ESCAPE_USAGE_MS && usage.packageName in highStimulationPackages) {
                baseTags += TAG_EMOTION_ESCAPE
            }
            baseTags.distinct().map { tag ->
                TriggerTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_APP_USAGE,
                    sourceId = usage.packageName,
                    packageName = usage.packageName,
                    tag = tag,
                    confidence = confidenceForAppUsage(usage.packageName, tag, usage.foregroundMs),
                    reason = "今日前台使用 ${formatDuration(usage.foregroundMs)}",
                    createdAt = System.currentTimeMillis(),
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private suspend fun tagActions(dayStart: Long): List<TriggerTag> =
        actionLogDao.loadSince(dayStart, ACTION_LIMIT).flatMap { action ->
            tagsForAction(action).map { tag ->
                TriggerTag(
                    dayStartMs = dayStart,
                    sourceType = SOURCE_ACTION,
                    sourceId = action.id.toString(),
                    packageName = extractPackageName(action),
                    tag = tag,
                    confidence = if (tag == TAG_ALGORITHMIC) 95 else 72,
                    reason = "干预记录：${action.actionType}",
                    createdAt = action.triggeredAt,
                    ruleVersion = RULE_VERSION,
                )
            }
        }

    private fun tagsForNotificationClassification(classification: String): List<String> =
        when (classification) {
            "算法推送" -> listOf(TAG_ALGORITHMIC)
            "社交" -> listOf(TAG_SOCIAL)
            "推广" -> listOf(TAG_PROMOTION)
            "工作" -> listOf(TAG_STUDY_WORK)
            "验证码", "消费信息" -> listOf(TAG_TASK)
            else -> emptyList()
        }

    private fun confidenceForNotification(classification: String, tag: String): Int =
        when {
            classification == "算法推送" && tag == TAG_ALGORITHMIC -> 90
            classification == "推广" && tag == TAG_PROMOTION -> 88
            classification == "社交" && tag == TAG_SOCIAL -> 84
            classification == "工作" && tag == TAG_STUDY_WORK -> 82
            classification == "消费信息" && tag == TAG_TASK -> 78
            else -> 70
        }

    private fun tagsForPackage(packageName: String): List<String> =
        when (packageName) {
            in algorithmPackages -> listOf(TAG_ALGORITHMIC)
            in socialPackages -> listOf(TAG_SOCIAL)
            in studyWorkPackages -> listOf(TAG_STUDY_WORK)
            in shoppingPackages -> listOf(TAG_PROMOTION)
            else -> emptyList()
        }

    private fun confidenceForAppUsage(packageName: String, tag: String, foregroundMs: Long): Int =
        when {
            packageName in algorithmPackages && tag == TAG_ALGORITHMIC -> 95
            packageName in socialPackages && tag == TAG_SOCIAL -> 88
            packageName in studyWorkPackages && tag == TAG_STUDY_WORK -> 82
            packageName in shoppingPackages && tag == TAG_PROMOTION -> 82
            tag == TAG_EMOTION_ESCAPE && foregroundMs >= EMOTION_ESCAPE_USAGE_MS -> 76
            else -> 65
        }

    private fun tagsForAction(action: ActionLog): List<String> {
        val tags = mutableListOf<String>()
        if (action.ruleId.startsWith("algorithm.")) tags += TAG_ALGORITHMIC
        val purpose = extractPurpose(action).lowercase(Locale.ROOT)
        when {
            hasAny(purpose, studyWorkKeywords) -> tags += TAG_STUDY_WORK
            hasAny(purpose, socialKeywords) -> tags += TAG_SOCIAL
            hasAny(purpose, emotionEscapeKeywords) -> tags += TAG_EMOTION_ESCAPE
            hasAny(purpose, taskKeywords) -> tags += TAG_TASK
        }
        return tags.distinct()
    }

    private fun extractPurpose(action: ActionLog): String =
        runCatching { JSONObject(action.payload).optString("purpose") }.getOrDefault("")

    private fun extractPackageName(action: ActionLog): String? =
        runCatching { JSONObject(action.payload).optString("packageName").takeIf { it.isNotBlank() } }
            .getOrNull()

    private fun hasAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }

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
        const val TAG_ALGORITHMIC = "algorithmic"
        const val TAG_SOCIAL = "social"
        const val TAG_PROMOTION = "promotion"
        const val TAG_TASK = "task"
        const val TAG_STUDY_WORK = "study_work"
        const val TAG_EMOTION_ESCAPE = "emotion_escape"

        private const val RULE_VERSION = "trigger-tags-v1"
        private const val SOURCE_NOTIFICATION = "notification"
        private const val SOURCE_APP_USAGE = "app_usage"
        private const val SOURCE_ACTION = "action"
        private const val APP_USAGE_LIMIT = 30
        private const val ACTION_LIMIT = 50
        private val EMOTION_ESCAPE_USAGE_MS = TimeUnit.MINUTES.toMillis(30)

        private val algorithmPackages = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.sina.weibo",
            "com.kuaishou.nebula",
        )
        private val highStimulationPackages = algorithmPackages + setOf(
            "com.tencent.mobileqq",
            "com.instagram.android",
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
        private val studyWorkKeywords = setOf("学习", "教程", "课程", "作业", "工作", "会议", "资料", "查")
        private val socialKeywords = setOf("朋友", "同学", "家人", "回复", "聊天", "消息")
        private val emotionEscapeKeywords = setOf("放松", "无聊", "缓解", "逃避", "刷", "看看", "消遣")
        private val taskKeywords = setOf("支付", "买", "订单", "验证码", "登录", "办事", "确认")
    }
}
