package com.kian.khup.core.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridLlmEngine @Inject constructor(
    private val localEngine: LiteRtLmLlmEngine,
    private val apiEngine: ApiLlmEngine,
    private val settingsRepository: AiSettingsRepository,
) : LlmEngine {

    override fun modelState(): LlmModelState = localEngine.modelState()

    override suspend fun runSmokeTest(): Result<String> =
        generate("你是 KHUP 的 AI 自检。请只用一句中文回答：当前 AI 通道已经可以运行。")

    override suspend fun generate(prompt: String): Result<String> {
        val settings = settingsRepository.currentSettings()
        return when (settings.providerMode) {
            AiProviderMode.LocalOnly -> localEngine.generate(prompt)
            AiProviderMode.ApiOnly -> apiEngine.generate(prompt)
            AiProviderMode.LocalFirst -> {
                val localResult = localEngine.generate(prompt)
                if (localResult.isSuccess) {
                    localResult
                } else if (settings.hasApiConfig) {
                    Log.w(TAG, "local generation failed, falling back to API", localResult.exceptionOrNull())
                    apiEngine.generate(prompt)
                } else {
                    localResult
                }
            }
        }
    }

    private companion object {
        const val TAG = "KHUP/AI"
    }
}
