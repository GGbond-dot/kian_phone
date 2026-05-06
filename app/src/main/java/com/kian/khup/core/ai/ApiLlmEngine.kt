package com.kian.khup.core.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

@Singleton
class ApiLlmEngine @Inject constructor(
    private val settingsRepository: AiSettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = API_TIMEOUT_MS
            connectTimeoutMillis = API_TIMEOUT_MS
            socketTimeoutMillis = API_TIMEOUT_MS
        }
    }

    suspend fun generate(prompt: String, tier: TaskTier = TaskTier.Auto): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepository.currentSettings()
            require(settings.hasApiConfig) {
                "API 配置不完整：需要 Base URL、API Key、Model。"
            }

            val redacted = PromptRedactor.redact(prompt)
            val url = "${settings.apiBaseUrl.trimEnd('/')}/chat/completions"
            val requestBody = buildJsonObject {
                put("model", settings.apiModel)
                put("messages", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", redacted)
                        }
                    )
                })
                put("temperature", 0.7)
            }.toString()

            val redactionDelta = prompt.length - redacted.length
            Log.i(TAG, "api chat started, url=$url, model=${settings.apiModel}, redaction_delta=$redactionDelta")
            val responseText: String = client.post(url) {
                bearerAuth(settings.apiKey)
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(requestBody)
            }.body()

            val response = json.parseToJsonElement(responseText).jsonObject
            val content = response["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            require(content.isNotBlank()) { "API 返回为空。原始响应：$responseText" }
            Log.i(TAG, "api chat result: $content")
            content
        }.onFailure { error ->
            Log.e(TAG, "api chat failed", error)
        }
    }

    private companion object {
        const val TAG = "KHUP/AI"
        const val API_TIMEOUT_MS = 30_000L
    }
}
