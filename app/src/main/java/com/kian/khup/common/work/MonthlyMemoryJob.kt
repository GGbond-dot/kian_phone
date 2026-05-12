package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.data.db.UserMemoryDao
import com.kian.khup.core.data.db.entities.UserMemory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val LONG_TERM_COMPRESSION_PROMPT = """
你是一个数据提炼助手。
将以下用户的周摘要提炼为一段不超过 50 字的稳定规律描述。

要求：
- 只保留跨周都出现的规律，忽略偶发模式
- 聚焦"在什么情境下容易回归"和"什么类型的建议有用/没用"
- 不加标题，不加列表，只输出一段话
- 绝对不超过 50 字

周摘要：
{summaries}
"""

@HiltWorker
class MonthlyMemoryJob @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llm: LlmEngine,
    private val userMemoryDao: UserMemoryDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val medium = userMemoryDao.getByType("MEDIUM_TERM") ?: return Result.success()

            val prompt = LONG_TERM_COMPRESSION_PROMPT.replace("{summaries}", medium.content)
            val compressed = llm.generate(prompt).getOrThrow().trim().take(100)

            userMemoryDao.upsert(
                UserMemory(
                    type = "LONG_TERM",
                    content = compressed,
                    tokenEstimate = (compressed.length * 1.5).toInt().coerceAtMost(80),
                    generatedAt = System.currentTimeMillis(),
                    expiresAt = Long.MAX_VALUE,
                )
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "failed: ${t.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KHUP/MonthlyMemoryJob"
        const val UNIQUE_NAME = "khup.monthly_memory"
    }
}
