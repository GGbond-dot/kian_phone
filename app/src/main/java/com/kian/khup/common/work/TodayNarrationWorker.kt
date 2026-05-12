package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.TodayNarrationDao
import com.kian.khup.core.data.db.entities.TodayNarration
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 今日观察 narration Worker：每 2 小时刷一次（由 WorkScheduler 注册）。
 * 拉今日 KV → 拼 prompt → 调 LlmEngine → upsert today_narration。
 * 失败不影响 UI：UI 端的 ViewModel 找不到行时回退到原 KV 渲染。
 */
@HiltWorker
class TodayNarrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llm: LlmEngine,
    private val narrationDao: TodayNarrationDao,
    private val appSessionDao: AppSessionDao,
    private val anomalyDao: AttentionAnomalyDao,
    private val eventDao: EventDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val todayMs = todayStartLocalMs()

            val totalMs = appSessionDao.observeTotalUsageSince(todayMs).first()
            val totalMinutes = (totalMs / 60_000L).toInt()
            val topApps = appSessionDao.loadTopUsageSince(todayMs, limit = 3)
            val anomalies = anomalyDao.observeForDay(todayMs).first()
            val anomalyCount = anomalies.count { it.status == "ACTIVE" }
            val checkInEvents = eventDao.observeByType(EventType.USER_REPORT, todayMs).first()
            val checkInCount = checkInEvents.size

            val topAppsLine = topApps.joinToString("、") { item ->
                val minutes = (item.foregroundMs / 60_000L).toInt()
                "${item.packageName} $minutes 分钟"
            }.ifBlank { "暂无应用使用" }

            val prompt = buildPrompt(
                totalMinutes = totalMinutes,
                topAppsLine = topAppsLine,
                anomalyCount = anomalyCount,
                checkInCount = checkInCount,
            )
            val narration = llm.generate(prompt).getOrThrow().trim().take(120)
            if (narration.isBlank()) return Result.retry()

            narrationDao.upsert(
                TodayNarration(
                    dayStartMs = todayMs,
                    narrationText = narration,
                    generatedAt = now,
                    modelVersion = MODEL_VERSION,
                )
            )
            // 旧日数据清理：保留 8 天
            narrationDao.deleteBefore(todayMs - 8L * 24 * 3600 * 1000)
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "failed: ${t.javaClass.simpleName}: ${t.message}")
            Result.retry()
        }
    }

    private fun buildPrompt(
        totalMinutes: Int,
        topAppsLine: String,
        anomalyCount: Int,
        checkInCount: Int,
    ): String = """
        你是 KHUP，正在为用户写"今天观察到的一段话"。
        数据：
          - 今天屏幕时间 $totalMinutes 分钟
          - 主要应用：$topAppsLine
          - 触发了 $anomalyCount 次注意力异常
          - 用户写了 $checkInCount 次检入
        要求：
          - 1-2 句中文，不超过 60 个汉字
          - 不评价（不说"太多了"、"不健康"），只观察
          - 不夸张数字（"差不多 2 小时"优于"2 小时 14 分 37 秒"）
          - 第二人称"你"
          - 不出现"异常值"、"回归值"、"注意力异常"这类术语
        直接输出那段话，不要任何前缀或解释。
    """.trimIndent()

    companion object {
        private const val TAG = "KHUP/TodayNarration"
        const val UNIQUE_NAME = "khup.today_narration"
        private const val MODEL_VERSION = "narration.v1"
    }
}
