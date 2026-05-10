package com.kian.khup.core.anomaly

interface RegressionPatternGenerator {
    /**
     * 分析一条 USER_REPORT 类型的 Event，识别（或更新）一个回归值模式。
     *
     * @param eventId Event 的主键。
     * @return AttentionAnomaly.id；如果未能识别出有意义的 pattern（LLM 失败 / JSON 不可解析），返回 null。
     */
    suspend fun analyzeUserReport(eventId: String): Long?
}

/** 行为线允许的 patternType 白名单。LLM 输出不在此列表则归为 [USER_REPORTED_DEFAULT_PATH]。 */
internal object UserReportPatternType {
    const val USER_REPORTED_DEFAULT_PATH = "USER_REPORTED_DEFAULT_PATH"
    const val USER_REPORTED_ALGORITHM_LOOP = "USER_REPORTED_ALGORITHM_LOOP"
    const val USER_REPORTED_PROCRASTINATION = "USER_REPORTED_PROCRASTINATION"
    const val USER_REPORTED_EMOTIONAL_AVOIDANCE = "USER_REPORTED_EMOTIONAL_AVOIDANCE"
    const val USER_REPORTED_OVERLOAD = "USER_REPORTED_OVERLOAD"
    const val USER_REPORTED_SOCIAL_DRIFT = "USER_REPORTED_SOCIAL_DRIFT"
    const val USER_REPORTED_OTHER = "USER_REPORTED_OTHER"

    const val PREFIX = "USER_REPORTED_"

    val ALL = setOf(
        USER_REPORTED_DEFAULT_PATH,
        USER_REPORTED_ALGORITHM_LOOP,
        USER_REPORTED_PROCRASTINATION,
        USER_REPORTED_EMOTIONAL_AVOIDANCE,
        USER_REPORTED_OVERLOAD,
        USER_REPORTED_SOCIAL_DRIFT,
        USER_REPORTED_OTHER,
    )
}

/** 行为线生成器写入的 ruleVersion，用于回查与 schema 演进。 */
internal const val BEHAVIOR_RULE_VERSION = "khup-behavior-v1"
