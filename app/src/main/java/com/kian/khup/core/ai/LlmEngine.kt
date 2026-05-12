package com.kian.khup.core.ai

data class LlmModelState(
    val primaryPath: String,
    val foundPath: String?,
    val checkedPaths: List<String>,
) {
    val isReady: Boolean = foundPath != null
}

/**
 * 任务档位 — 决定 HybridLlmEngine 把 prompt 路由到哪个通道。
 * - [Light]: 强制本地(摘要、自然语言查询、分类等轻任务)
 * - [Heavy]: 强制 API(复盘、跨日推理、复杂总结)
 * - [Auto]:  按用户在 Settings 里的 ProviderMode 设置走(默认值,聊天界面用)
 */
enum class TaskTier {
    Light,
    Heavy,
    Auto,
}

interface LlmEngine {
    fun modelState(): LlmModelState

    suspend fun runSmokeTest(): Result<String>

    suspend fun generate(prompt: String, tier: TaskTier = TaskTier.Auto): Result<String>

    suspend fun generateStreaming(
        prompt: String,
        tier: TaskTier = TaskTier.Auto,
        onDelta: (String) -> Unit,
    ): Result<String> {
        val result = generate(prompt, tier)
        result.onSuccess { onDelta(it) }
        return result
    }
}
