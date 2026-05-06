package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.TaskTier
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.HourlySummary
import com.kian.khup.core.summary.HourlySummaryNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

@HiltWorker
class HourlySummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val llmEngine: LlmEngine,
    private val notifier: HourlySummaryNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val now = System.currentTimeMillis()
            val hourMs = TimeUnit.HOURS.toMillis(1)
            val windowEnd = (now / hourMs) * hourMs
            val windowStart = windowEnd - hourMs

            if (hourlySummaryDao.findByWindow(windowStart) != null) {
                Log.i(TAG, "skip: window $windowStart already summarized")
                return@runCatching Result.success()
            }

            val events = eventDao.getInWindow(EventType.NOTIFICATION_POSTED, windowStart, windowEnd)
            if (events.isEmpty()) {
                Log.i(TAG, "skip: no events in window $windowStart")
                return@runCatching Result.success()
            }

            val truncated = events.takeLast(MAX_EVENTS_IN_PROMPT)
            val omitted = events.size - truncated.size
            val prompt = buildPrompt(truncated, omitted)

            val raw = llmEngine.generate(prompt, TaskTier.Light).getOrElse {
                Log.w(TAG, "llm generate failed", it)
                return@runCatching Result.retry()
            }
            val parsed = parseLlmOutput(raw)

            val topPackages = events.groupingBy { it.packageName }.eachCount()
                .entries.sortedByDescending { it.value }
                .take(3)
                .map { it.key }

            val record = HourlySummary(
                windowStartMs = windowStart,
                windowEndMs = windowEnd,
                summary = parsed.summary,
                eventCount = events.size,
                topPackages = JSONArray(topPackages).toString(),
                importance = parsed.importance,
                createdAt = System.currentTimeMillis(),
                modelVersion = MODEL_VERSION,
            )
            hourlySummaryDao.upsert(record)
            Log.i(TAG, "summary stored: window=$windowStart importance=${parsed.importance} events=${events.size}")

            if (parsed.importance >= IMPORTANCE_NOTIFY_THRESHOLD) {
                val matched = parsed.triggerPackages.filter { it in HourlySummaryNotifier.TRIGGER_WHITELIST }
                if (matched.isNotEmpty()) {
                    notifier.notifyImportant(windowStart, parsed.summary, matched)
                }
            }

            Result.success()
        }.getOrElse {
            Log.w(TAG, "hourly summary failed", it)
            Result.retry()
        }
    }

    private fun buildPrompt(events: List<Event>, omittedCount: Int): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return buildString {
            appendLine("你是 KHUP 通知摘要器。过去 1 小时收到这些通知:")
            events.forEach { e ->
                val time = fmt.format(Date(e.timestamp))
                val title = e.title.orEmpty().take(40)
                val text = e.text.orEmpty().take(80)
                appendLine("[$time ${e.packageName}] $title: $text")
            }
            if (omittedCount > 0) appendLine("还有 $omittedCount 条未列出。")
            appendLine()
            appendLine("要求:")
            appendLine("1) 用 1-2 句中文概括这小时谁找你、有什么重要事项、是不是被打扰多。不要列举每条,不要\"好的\"开头。")
            appendLine("2) 评估重要程度 importance: 0=无关紧要, 1=普通, 2=值得注意, 3=必须立即处理。")
            appendLine("3) 列出含重要事项的 packageName 数组(没有则空数组)。")
            appendLine("仅输出 JSON,不要 markdown 代码块,格式: {\"summary\":\"...\",\"importance\":0,\"trigger_packages\":[]}")
        }
    }

    private data class ParsedSummary(
        val summary: String,
        val importance: Int,
        val triggerPackages: List<String>,
    )

    private fun parseLlmOutput(raw: String): ParsedSummary {
        val jsonText = extractJsonObject(raw)
        if (jsonText != null) {
            runCatching {
                val obj = JSONObject(jsonText)
                val summary = obj.optString("summary").ifBlank { raw.trim() }
                val importance = obj.optInt("importance", 0).coerceIn(0, 3)
                val triggers = obj.optJSONArray("trigger_packages")?.let { arr ->
                    List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotBlank() }
                } ?: emptyList()
                return ParsedSummary(summary, importance, triggers)
            }.onFailure { Log.w(TAG, "json parse failed, fallback to plain text", it) }
        }
        return ParsedSummary(summary = raw.trim(), importance = 0, triggerPackages = emptyList())
    }

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    companion object {
        private const val TAG = "KHUP/HourlySummary"
        const val UNIQUE_NAME = "khup.hourly_summary"
        private const val MODEL_VERSION = "gemma-4-e2b-summary-v1"
        private const val MAX_EVENTS_IN_PROMPT = 50
        private const val IMPORTANCE_NOTIFY_THRESHOLD = 2
    }
}
