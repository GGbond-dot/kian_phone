package com.kian.khup.core.ai

data class LlmModelState(
    val primaryPath: String,
    val foundPath: String?,
    val checkedPaths: List<String>,
) {
    val isReady: Boolean = foundPath != null
}

interface LlmEngine {
    fun modelState(): LlmModelState

    suspend fun runSmokeTest(): Result<String>

    suspend fun generate(prompt: String): Result<String>
}
