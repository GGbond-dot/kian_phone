package com.kian.khup.core.anomaly

interface AnomalySuggestionGenerator {
    /**
     * 为指定 pattern 生成异常值建议，落库。
     *
     * @param patternId AttentionAnomaly.id
     * @param regenerationCount 第 N 次重试，0 = 首次。
     * @param parentSuggestionId 上一条 suggestion 的 id（POSTPONE 触发的重新生成时传入）。
     * @return 新生成的 AnomalySuggestion.id；24h 冷却中、pattern 不存在或 LLM 全失败时返回 null。
     *         注意：LLM 失败但走 fallback 文案落库时仍返回 id（不向用户暴露失败）。
     */
    suspend fun generateForPattern(
        patternId: Long,
        regenerationCount: Int = 0,
        parentSuggestionId: Long? = null,
    ): Long?
}

/** 行为线 suggestion 写入的 modelVersion。 */
internal const val BEHAVIOR_SUGGESTION_MODEL_VERSION = "khup-behavior-v1"
