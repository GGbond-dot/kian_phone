package com.kian.khup.core.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LiteRtLmLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmEngine {

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    override fun modelState(): LlmModelState {
        val paths = candidateModelPaths()
        return LlmModelState(
            primaryPath = paths.first(),
            foundPath = paths.firstOrNull { File(it).isFile },
            checkedPaths = paths,
        )
    }

    override suspend fun runSmokeTest(): Result<String> = generateInternal(
        """
        你是 KHUP 的端侧 AI 自检。请只用一句中文回答：本地模型已经可以运行。
        """.trimIndent(),
        logLabel = "smoke test",
    )

    override suspend fun generate(prompt: String): Result<String> =
        generateInternal(prompt, logLabel = "chat")

    private suspend fun generateInternal(prompt: String, logLabel: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val state = modelState()
                val modelPath = state.foundPath
                    ?: error("没有找到模型文件。先把 .litertlm 模型放到 ${state.primaryPath}")

                val eng = mutex.withLock { getOrCreateEngine(modelPath) }

                Log.i(TAG, "$logLabel started, model=$modelPath")
                val response = eng.createConversation().use { conv ->
                    val reply = conv.sendMessage(prompt)
                    reply.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { it.text }
                        .trim()
                }
                Log.i(TAG, "$logLabel result: $response")
                response
            }.onFailure { error ->
                Log.e(TAG, "$logLabel failed", error)
            }
        }

    private fun getOrCreateEngine(modelPath: String): Engine {
        engine?.takeIf { loadedPath == modelPath }?.let { return it }

        engine?.close()
        engine = null
        loadedPath = null

        Log.i(TAG, "loading LiteRT-LM engine from $modelPath (this can take 10–30s)")
        runCatching { Engine.Companion.setNativeMinLogSeverity(LogSeverity.VERBOSE) }
            .onFailure { Log.w(TAG, "could not bump native log severity", it) }
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            audioBackend = Backend.CPU(),
            maxNumTokens = 2048,
            cacheDir = cacheDir.absolutePath,
        )
        val created = Engine(config).also { it.initialize() }

        engine = created
        loadedPath = modelPath
        return created
    }

    private fun candidateModelPaths(): List<String> {
        val appPrivateModel = File(File(context.filesDir, "models"), MODEL_FILE_NAME).absolutePath
        val externalModel = context.getExternalFilesDir("models")
            ?.let { File(it, MODEL_FILE_NAME).absolutePath }
        val adbDevModel = "/data/local/tmp/llm/$MODEL_FILE_NAME"

        return listOfNotNull(appPrivateModel, externalModel, adbDevModel)
    }

    private companion object {
        const val TAG = "KHUP/AI"
        // Gemma 4 E2B (LiteRT-LM 格式,~2.58GB)
        // 来源:huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
        // 选 E2B 而非 E4B:E4B 在小米 14 上 PSS 触发 MIUI 6GB 单 app 上限被 OOM kill。
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
