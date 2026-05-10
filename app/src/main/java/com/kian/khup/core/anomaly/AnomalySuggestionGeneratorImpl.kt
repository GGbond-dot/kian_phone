package com.kian.khup.core.anomaly

import android.util.Log
import com.kian.khup.core.ai.KhupPromptPolicy
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.LlmJsonExtractor
import com.kian.khup.core.ai.TaskTier
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.entities.AnomalySuggestion
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class AnomalySuggestionGeneratorImpl @Inject constructor(
    private val llm: LlmEngine,
    private val anomalyDao: AttentionAnomalyDao,
    private val suggestionDao: AnomalySuggestionDao,
    private val feedbackDao: UserFeedbackDao,
) : AnomalySuggestionGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateForPattern(
        patternId: Long,
        regenerationCount: Int,
        parentSuggestionId: Long?,
    ): Long? = withContext(Dispatchers.IO) {
        val pattern = anomalyDao.getById(patternId) ?: return@withContext null

        // 24h 冷却：仅对首次生成生效；POSTPONE 重生成（regenerationCount>0 或 parent 非空）跳过。
        if (regenerationCount == 0 && parentSuggestionId == null) {
            val sinceMs = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            val recent = suggestionDao.findByPatternKeySince(pattern.anomalyKey, sinceMs)
            if (recent.any { it.status in COOLDOWN_STATUSES }) {
                Log.i(TAG, "cooldown hit for patternKey=${pattern.anomalyKey}; skip generation")
                return@withContext null
            }
        }

        val parent = parentSuggestionId?.let { suggestionDao.getById(it) }
        val feedbackContext = loadFeedbackContext()

        val prompt = buildSuggestionPrompt(
            pattern = pattern,
            feedbackContext = feedbackContext,
            parent = parent,
            regenerationCount = regenerationCount,
        )

        val parsed = generateWithRetry(prompt)
        val now = System.currentTimeMillis()
        val finalSuggestion = parsed?.takeIf { it.passesThreePillars() }
            ?: ParsedSuggestion.fallback()

        val row = AnomalySuggestion(
            title = pattern.title,
            dayStartMs = startOfDayMs(now),
            createdAt = now,
            patternId = pattern.id,
            patternKey = pattern.anomalyKey,
            suggestionDomain = SUGGESTION_DOMAIN_BEHAVIOR,
            actionText = finalSuggestion.actionText,
            whyText = finalSuggestion.whyText,
            costLevel = "LOW",
            expectedUpside = finalSuggestion.expectedUpside,
            status = "PENDING",
            modelVersion = BEHAVIOR_SUGGESTION_MODEL_VERSION,
            regenerationCount = regenerationCount,
            parentSuggestionId = parentSuggestionId,
            updatedAt = now,
        )
        suggestionDao.insert(row)
    }

    private suspend fun generateWithRetry(prompt: String): ParsedSuggestion? {
        // 第一次失败若是 costLevel 非 LOW，按 spec §4.2 step 7 再试一次；其他失败直接交给 fallback。
        repeat(2) { attempt ->
            val raw = llm.generate(prompt, TaskTier.Light).getOrElse { error ->
                Log.w(TAG, "suggestion LLM failed (attempt=$attempt)", error)
                return@repeat
            }
            val parsed = parseSuggestionJson(raw) ?: run {
                Log.w(TAG, "suggestion JSON parse failed (attempt=$attempt)")
                return@repeat
            }
            if (parsed.costLevel == "LOW") return parsed
            Log.w(TAG, "suggestion costLevel=${parsed.costLevel} != LOW; retry")
        }
        return null
    }

    private suspend fun loadFeedbackContext(): List<FeedbackEntry> {
        val sinceMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val feedbacks = feedbackDao.recentByTargetType(
            targetType = TARGET_TYPE_SUGGESTION,
            sinceMs = sinceMs,
            limit = 20,
        )
        return feedbacks.mapNotNull { fb ->
            val target = suggestionDao.getById(fb.targetId) ?: return@mapNotNull null
            FeedbackEntry(
                feedbackType = fb.feedbackType,
                reason = fb.reason,
                actionText = target.actionText,
            )
        }
    }

    private fun buildSuggestionPrompt(
        pattern: AttentionAnomaly,
        feedbackContext: List<FeedbackEntry>,
        parent: AnomalySuggestion?,
        regenerationCount: Int,
    ): String {
        val now = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        val isRegen = regenerationCount > 0 || parent != null

        val inputJson = buildJsonObject {
            put("task", "generate_anomaly_suggestion")
            put(
                "pattern",
                buildJsonObject {
                    put("type", pattern.type)
                    put("title", pattern.title)
                    put("description", pattern.detail)
                    put("frequency", pattern.frequency)
                }
            )
            put(
                "recent_feedbacks_7d",
                buildJsonArray {
                    feedbackContext.forEach { fb ->
                        add(
                            buildJsonObject {
                                put("suggestion_action", fb.actionText.take(80))
                                put("feedback", mapFeedbackTypeForPrompt(fb.feedbackType))
                                fb.reason?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
                            }
                        )
                    }
                }
            )
            put(
                "regeneration",
                buildJsonObject {
                    put("is_regeneration", isRegen)
                    put("previous_action", parent?.actionText)
                    put("regeneration_count", regenerationCount)
                }
            )
            put("current_time_local", timeFmt.format(now))
            put("available_minutes_estimate", DEFAULT_AVAILABLE_MINUTES)
        }

        return buildString {
            append(KhupPromptPolicy.BEHAVIOR_LINE_SYSTEM_PROMPT.trim())
            append("\n\n")
            append("基于下面的 pattern 与最近反馈，生成一条满足三件套的异常值建议。\n")
            append(inputJson.toString())
            if (isRegen) {
                append("\n\n")
                append("用户对前一条建议点了\"换一个\"。前一条是：\"${parent?.actionText ?: "(空)"}\"。")
                append("请给出完全不同角度的低成本异常值，避免与前一条重复或近义。")
            }
            append("\n\n")
            append("严格输出 JSON：\n")
            append("{\n")
            append("  \"actionText\": \"...\",\n")
            append("  \"whyText\": \"...\",\n")
            append("  \"costLevel\": \"LOW\",\n")
            append("  \"expectedUpside\": \"...\",\n")
            append("  \"tone\": \"sharp_but_not_shaming\"\n")
            append("}")
        }
    }

    private fun parseSuggestionJson(raw: String): ParsedSuggestion? {
        val jsonText = LlmJsonExtractor.extract(raw) ?: return null
        return runCatching {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            val action = obj["actionText"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val why = obj["whyText"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val cost = obj["costLevel"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase().orEmpty()
            val upside = obj["expectedUpside"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            ParsedSuggestion(action, why, cost, upside)
        }.getOrNull()
    }

    private fun mapFeedbackTypeForPrompt(type: String): String = when (type.uppercase()) {
        "ACCEPT" -> "ACCEPTED"
        "REJECT" -> "REJECTED"
        "POSTPONE" -> "POSTPONED"
        else -> type.uppercase()
    }

    private fun startOfDayMs(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private data class ParsedSuggestion(
        val actionText: String,
        val whyText: String,
        val costLevel: String,
        val expectedUpside: String,
    ) {
        fun passesThreePillars(): Boolean {
            if (costLevel != "LOW") return false
            if (expectedUpside.isBlank() || expectedUpside.length <= 5) return false
            if (actionText.isBlank() || actionText.length > 200) return false
            if (whyText.isBlank() || whyText.length > 200) return false
            return true
        }

        companion object {
            fun fallback(): ParsedSuggestion = ParsedSuggestion(
                actionText = KhupPromptPolicy.FALLBACK_SUGGESTION_ACTION,
                whyText = KhupPromptPolicy.FALLBACK_SUGGESTION_WHY,
                costLevel = "LOW",
                expectedUpside = KhupPromptPolicy.FALLBACK_SUGGESTION_UPSIDE,
            )
        }
    }

    private data class FeedbackEntry(
        val feedbackType: String,
        val reason: String?,
        val actionText: String,
    )

    private companion object {
        const val TAG = "KHUP/SuggestionGen"
        const val SUGGESTION_DOMAIN_BEHAVIOR = "BEHAVIOR"
        const val TARGET_TYPE_SUGGESTION = "SUGGESTION"
        const val DEFAULT_AVAILABLE_MINUTES = 30
        val COOLDOWN_STATUSES = setOf("PENDING", "ACCEPTED", "REJECTED")
    }
}
