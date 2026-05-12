package com.kian.khup.core.anomaly

import android.util.Log
import com.kian.khup.core.ai.KhupPromptPolicy
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmJsonExtractor
import com.kian.khup.core.ai.PromptRedactor
import com.kian.khup.core.ai.TaskTier
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 把一条 USER_REPORT Event 转换成 AttentionAnomaly（回归模式）。
 *
 * 命中已有 anomalyKey 时累加 frequency / 更新 lastSeenAt；未命中则 INSERT 一条新记录。
 * 任何 LLM 失败 / JSON 不可解析都返回 null（不落库）。
 */
@Singleton
class RegressionPatternGeneratorImpl @Inject constructor(
    private val llm: LlmEngine,
    private val eventDao: EventDao,
    private val anomalyDao: AttentionAnomalyDao,
) : RegressionPatternGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeUserReport(eventId: String): Long? = withContext(Dispatchers.IO) {
        val event = findUserReportEvent(eventId) ?: return@withContext null
        val rawText = event.text?.takeIf { it.isNotBlank() } ?: return@withContext null
        val redacted = PromptRedactor.redact(rawText)

        val now = System.currentTimeMillis()
        val recentPatterns = anomalyDao.loadRecentByTypePrefix(
            sinceMs = now - TimeUnit.DAYS.toMillis(7),
            typePrefix = "${UserReportPatternType.PREFIX}%",
            limit = 10,
        )

        val prompt = buildPatternPrompt(
            redactedReport = redacted,
            now = now,
            recent = recentPatterns,
        )

        val rawOutput = llm.generate(prompt, TaskTier.Heavy).getOrElse { error ->
            Log.w(TAG, "pattern LLM failed", error)
            return@withContext null
        }
        val parsed = parsePatternJson(rawOutput) ?: run {
            Log.w(TAG, "pattern JSON parse failed; output discarded")
            return@withContext null
        }

        upsertAnomaly(parsed, event.timestamp, now)
    }

    private suspend fun findUserReportEvent(eventId: String): com.kian.khup.core.data.db.entities.Event? {
        val event = eventDao.getById(eventId) ?: return null
        require(event.type == EventType.USER_REPORT) {
            "event $eventId is not USER_REPORT (got ${event.type})"
        }
        return event
    }

    private fun buildPatternPrompt(
        redactedReport: String,
        now: Long,
        recent: List<AttentionAnomaly>,
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val weekdayFmt = SimpleDateFormat("EEEE", Locale.ENGLISH)

        val recentJson = buildJsonArray {
            recent.forEach { p ->
                add(
                    buildJsonObject {
                        put("type", p.type)
                        put("title", p.title)
                        put("frequency", p.frequency)
                        val lastSeen = p.lastSeenAt ?: p.createdAt
                        val daysAgo = ((now - lastSeen) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0L)
                        put("lastSeenDaysAgo", daysAgo)
                    }
                )
            }
        }

        val inputJson = buildJsonObject {
            put("user_report", redactedReport)
            put("current_time_local", timeFmt.format(cal.time))
            put("weekday", weekdayFmt.format(cal.time))
            put("recent_patterns_7d", recentJson)
        }

        return buildString {
            append(KhupPromptPolicy.BEHAVIOR_LINE_SYSTEM_PROMPT.trim())
            append("\n\n")
            append("下面是用户提交的处境。识别这是一条什么类型的回归值模式（regression pattern）。\n")
            append(
                buildJsonObject {
                    put("task", "identify_regression_pattern")
                    put("input", inputJson)
                }.toString()
            )
            append("\n\n")
            append("严格输出 JSON：\n")
            append("{\n")
            append("  \"patternType\": \"USER_REPORTED_DEFAULT_PATH\",\n")
            append("  \"patternTitle\": \"...\",\n")
            append("  \"patternDescription\": \"...\",\n")
            append("  \"severity\": 1-5,\n")
            append("  \"confidence\": 0.0-1.0\n")
            append("}\n")
            append("可选 patternType：${UserReportPatternType.ALL.joinToString(", ")}。")
        }
    }

    private fun parsePatternJson(raw: String): ParsedPattern? {
        val jsonText = LlmJsonExtractor.extract(raw) ?: return null
        return runCatching {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            val rawType = obj["patternType"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val type = if (rawType in UserReportPatternType.ALL) {
                rawType
            } else {
                UserReportPatternType.USER_REPORTED_DEFAULT_PATH
            }
            val title = obj["patternTitle"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val description = obj["patternDescription"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() || description.isBlank()) return@runCatching null
            val severity = (obj["severity"]?.jsonPrimitive?.intOrNull ?: 2).coerceIn(1, 5)
            val confidence = (obj["confidence"]?.jsonPrimitive?.floatOrNull ?: 0.5f).coerceIn(0.0f, 1.0f)
            ParsedPattern(type, title, description, severity, confidence)
        }.getOrNull()
    }

    private suspend fun upsertAnomaly(
        parsed: ParsedPattern,
        eventTimestamp: Long,
        now: Long,
    ): Long {
        val anomalyKey = "${parsed.type}:${normalizeForKey(parsed.title)}"
        val existing = anomalyDao.findByKey(anomalyKey)
        if (existing != null) {
            val updated = existing.copy(
                lastSeenAt = now,
                frequency = existing.frequency + 1,
                confidence = maxOf(existing.confidence, parsed.confidence),
            )
            anomalyDao.update(updated)
            return existing.id
        }
        val anomaly = AttentionAnomaly(
            anomalyKey = anomalyKey,
            dayStartMs = startOfDayMs(eventTimestamp),
            type = parsed.type,
            severity = parsed.severity,
            title = parsed.title,
            detail = parsed.description,
            packageName = null,
            metricValue = 1L,
            baselineValue = null,
            windowStartMs = null,
            windowEndMs = null,
            createdAt = now,
            ruleVersion = BEHAVIOR_RULE_VERSION,
            status = "ACTIVE",
            firstSeenAt = now,
            lastSeenAt = now,
            frequency = 1,
            confidence = parsed.confidence,
        )
        return anomalyDao.insert(anomaly)
    }

    private fun normalizeForKey(input: String): String {
        val lowered = input.lowercase(Locale.getDefault())
        // 去掉 ASCII 标点 + CJK 标点 + 数字，让相近表述命中同 key
        val stripped = lowered.replace(Regex("[\\p{Punct}\\d\\s\\u3000-\\u303F\\uFF00-\\uFFEF]"), "")
        return stripped.take(50)
    }

    private fun startOfDayMs(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private data class ParsedPattern(
        val type: String,
        val title: String,
        val description: String,
        val severity: Int,
        val confidence: Float,
    )

    private companion object {
        const val TAG = "KHUP/PatternGen"
    }
}
