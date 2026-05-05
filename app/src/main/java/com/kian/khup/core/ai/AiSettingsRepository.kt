package com.kian.khup.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AiProviderMode {
    LocalFirst,
    LocalOnly,
    ApiOnly,
}

data class AiSettings(
    val providerMode: AiProviderMode = AiProviderMode.LocalFirst,
    val apiBaseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val apiModel: String = "",
) {
    val hasApiConfig: Boolean =
        apiBaseUrl.isNotBlank() && apiKey.isNotBlank() && apiModel.isNotBlank()
}

@Singleton
class AiSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observeSettings(): Flow<AiSettings> = callbackFlow {
        fun emitCurrent() {
            trySend(currentSettings())
        }

        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            emitCurrent()
        }
        emitCurrent()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun currentSettings(): AiSettings =
        AiSettings(
            providerMode = runCatching {
                AiProviderMode.valueOf(prefs.getString(KEY_PROVIDER_MODE, null) ?: AiProviderMode.LocalFirst.name)
            }.getOrDefault(AiProviderMode.LocalFirst),
            apiBaseUrl = prefs.getString(KEY_API_BASE_URL, null) ?: "https://api.openai.com/v1",
            apiKey = prefs.getString(KEY_API_KEY, null).orEmpty(),
            apiModel = prefs.getString(KEY_API_MODEL, null).orEmpty(),
        )

    fun setProviderMode(mode: AiProviderMode) {
        prefs.edit().putString(KEY_PROVIDER_MODE, mode.name).apply()
    }

    fun setApiBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_API_BASE_URL, baseUrl.trim().trimEnd('/')).apply()
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun setApiModel(model: String) {
        prefs.edit().putString(KEY_API_MODEL, model.trim()).apply()
    }

    private companion object {
        const val PREFS_NAME = "khup.ai_settings"
        const val KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_MODEL = "api_model"
    }
}
