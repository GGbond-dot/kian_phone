package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.intervention.InterventionNotifier
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class InterventionRepository @Inject constructor(
    private val appSessionDao: AppSessionDao,
    private val actionLogDao: ActionLogDao,
    private val notifier: InterventionNotifier,
    private val settingsRepository: InterventionSettingsRepository,
) {
    fun observeRecentActions(limit: Int = 5): Flow<List<ActionLog>> =
        actionLogDao.observeRecent(limit)

    fun observeTodayActions(limit: Int = 5): Flow<List<ActionLog>> =
        actionLogDao.observeSince(startOfTodayMs(), limit)

    fun isMonitoredPackage(packageName: String): Boolean =
        algorithmPackageNames.contains(packageName)

    suspend fun getExceededRuleForPackage(packageName: String): InterventionRuleStatus? =
        withContext(Dispatchers.IO) {
            val todayStart = startOfTodayMs()
            val settings = settingsRepository.currentSettings()
            algorithmRules(settings)
                .firstOrNull { packageName in it.packageNames }
                ?.let { rule ->
                    val usedMs = appSessionDao.getUsageForPackagesSince(rule.packageNames, todayStart)
                    if (usedMs >= rule.thresholdMs) {
                        InterventionRuleStatus(
                            ruleId = rule.ruleId,
                            appLabel = rule.appLabel,
                            packageName = packageName,
                            usedMs = usedMs,
                            thresholdMs = rule.thresholdMs,
                        )
                    } else {
                        null
                    }
                }
        }

    suspend fun recordPurposeGate(rule: InterventionRuleStatus, purpose: String) {
        withContext(Dispatchers.IO) {
            actionLogDao.insert(
                ActionLog(
                    ruleId = rule.ruleId,
                    actionType = ACTION_PURPOSE_GATE,
                    payload = buildJsonObject {
                        put("appLabel", rule.appLabel)
                        put("packageName", rule.packageName)
                        put("usedMs", rule.usedMs)
                        put("thresholdMs", rule.thresholdMs)
                        put("purpose", purpose)
                    }.toString(),
                    triggeredAt = System.currentTimeMillis(),
                    userResponse = "purpose_submitted",
                ),
            )
        }
    }

    suspend fun evaluateToday(): Int = evaluateMutex.withLock {
        withContext(Dispatchers.IO) {
            val todayStart = startOfTodayMs()
            val settings = settingsRepository.currentSettings()
            algorithmRules(settings).count { rule ->
                val usedMs = appSessionDao.getUsageForPackagesSince(rule.packageNames, todayStart)
                val exceeded = usedMs >= rule.thresholdMs
                val alreadyReminded = actionLogDao.hasActionSince(rule.ruleId, ACTION_REMIND, todayStart)
                if (!exceeded || alreadyReminded) {
                    false
                } else {
                    val posted = notifier.notifyThresholdExceeded(
                        ruleId = rule.ruleId,
                        appLabel = rule.appLabel,
                        usedText = formatDuration(usedMs),
                        thresholdText = formatDuration(rule.thresholdMs),
                    )
                    actionLogDao.insert(
                        ActionLog(
                            ruleId = rule.ruleId,
                            actionType = ACTION_REMIND,
                            payload = buildJsonObject {
                                put("appLabel", rule.appLabel)
                                put("packageNames", rule.packageNames.joinToString(","))
                                put("usedMs", usedMs)
                                put("thresholdMs", rule.thresholdMs)
                                put("notificationPosted", posted)
                            }.toString(),
                            triggeredAt = System.currentTimeMillis(),
                        ),
                    )
                    true
                }
            }
        }
    }

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
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

    private data class AlgorithmRule(
        val ruleId: String,
        val appLabel: String,
        val packageNames: List<String>,
        val thresholdMs: Long,
    )

    companion object {
        private const val ACTION_REMIND = "remind"
        private const val ACTION_PURPOSE_GATE = "purpose_gate"
        private val evaluateMutex = Mutex()
        private val algorithmPackageNames = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
        )

        private fun algorithmRules(settings: InterventionSettings) =
            listOf(
                AlgorithmRule(
                    ruleId = "algorithm.douyin.daily",
                    appLabel = "抖音",
                    packageNames = listOf("com.ss.android.ugc.aweme"),
                    thresholdMs = TimeUnit.MINUTES.toMillis(settings.douyinLimitMinutes.toLong()),
                ),
                AlgorithmRule(
                    ruleId = "algorithm.xiaohongshu.daily",
                    appLabel = "小红书",
                    packageNames = listOf("com.xingin.xhs"),
                    thresholdMs = TimeUnit.MINUTES.toMillis(settings.xiaohongshuLimitMinutes.toLong()),
                ),
            )
    }
}

data class InterventionRuleStatus(
    val ruleId: String,
    val appLabel: String,
    val packageName: String,
    val usedMs: Long,
    val thresholdMs: Long,
)
