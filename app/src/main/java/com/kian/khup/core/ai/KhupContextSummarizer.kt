package com.kian.khup.core.ai

import com.kian.khup.core.ai.memory.ShortTermMemoryBuilder
import com.kian.khup.core.data.db.UserMemoryDao
import com.kian.khup.core.data.db.entities.UserMemory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从预压缩的 user_memory 表读取三层摘要，拼装 ≤350 token 的用户上下文注入 AI 对话。
 * SHORT_TERM 在读取时若过期（>1h）则实时重算并写表；MEDIUM/LONG 由 WorkManager 定期刷新。
 */
@Singleton
class KhupContextSummarizer @Inject constructor(
    private val userMemoryDao: UserMemoryDao,
    private val shortTermBuilder: ShortTermMemoryBuilder,
) {
    companion object {
        const val TOKEN_BUDGET_LOCAL = 350
        const val TOKEN_BUDGET_API = 800
    }

    suspend fun buildUserContext(tokenBudget: Int = TOKEN_BUDGET_LOCAL): String {
        val now = System.currentTimeMillis()

        val short = getOrRefreshShortTerm(now)
        val medium = userMemoryDao.getByType("MEDIUM_TERM")
        val long = userMemoryDao.getByType("LONG_TERM")

        val result = StringBuilder()
        var usedTokens = 0

        short?.let {
            result.appendLine(it.content)
            usedTokens += it.tokenEstimate
        }

        medium?.let {
            if (usedTokens + it.tokenEstimate <= tokenBudget) {
                result.appendLine(it.content)
                usedTokens += it.tokenEstimate
            }
        }

        long?.let {
            if (usedTokens + it.tokenEstimate <= tokenBudget) {
                result.appendLine(it.content)
            }
        }

        return result.toString().trim()
    }

    private suspend fun getOrRefreshShortTerm(now: Long): UserMemory? {
        val cached = userMemoryDao.getByType("SHORT_TERM")
        return if (cached == null || cached.expiresAt < now) {
            val fresh = try { shortTermBuilder.build() } catch (_: Throwable) { return cached }
            userMemoryDao.upsert(fresh)
            fresh
        } else {
            cached
        }
    }
}
