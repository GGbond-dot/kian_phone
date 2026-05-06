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

    override suspend fun generate(prompt: String, tier: TaskTier): Result<String> {
        val settings = settingsRepository.currentSettings()

        // 显式档位绕过 ProviderMode,业务调用方明确知道自己要哪个通道。
        when (tier) {
            TaskTier.Light -> {
                Log.i(TAG, "tier=Light → local")
                return localEngine.generate(prompt, tier)
            }
            TaskTier.Heavy -> {
                if (!settings.hasApiConfig) {
                    return Result.failure(IllegalStateException("Heavy 任务需要 API 通道,但 API 未配置。去 Settings 配置或降级到 Light。"))
                }
                Log.i(TAG, "tier=Heavy → api")
                return apiEngine.generate(prompt, tier)
            }
            TaskTier.Auto -> Unit // 落到下面 ProviderMode 分支
        }

        return when (settings.providerMode) {
            AiProviderMode.LocalOnly -> localEngine.generate(prompt, tier)
            AiProviderMode.ApiOnly -> apiEngine.generate(prompt, tier)
            AiProviderMode.LocalFirst -> {
                val localResult = localEngine.generate(prompt, tier)
                if (localResult.isSuccess) {
                    localResult
                } else if (settings.hasApiConfig) {
                    Log.w(TAG, "local generation failed, falling back to API", localResult.exceptionOrNull())
                    apiEngine.generate(prompt, tier)
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
