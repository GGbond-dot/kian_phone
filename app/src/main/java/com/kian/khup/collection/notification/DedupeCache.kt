package com.kian.khup.collection.notification

import android.app.PendingIntent
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存级实时去重（架构文档 §5.1 第 1 层）。
 *
 * 同一条逻辑通知的 [NotificationSnapshot.key] 相同。如果 contentHash 与上次相同，
 * 说明只是系统刷新通知（进度条、媒体控件之类），直接丢弃。
 *
 * 第 2 层 DB 去重靠 Event 的 PK（基于内容 hash）。
 */
class DedupeCache {
    private val cache = ConcurrentHashMap<String, Int>()

    /** 返回 true 表示是新内容（应当继续处理），false 表示重复。 */
    fun acceptIfNew(key: String, contentHash: Int): Boolean {
        val prev = cache.put(key, contentHash)
        return prev != contentHash
    }

    fun remove(key: String) { cache.remove(key) }

    fun clear() { cache.clear() }

    fun size(): Int = cache.size
}

/** [MessageListener] 解析出的通知快照。 */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    val channelId: String?,
    val category: String?,
    val postTime: Long,
    val rawJson: String?,
    val contentIntent: PendingIntent?,
) {
    val contentHash: Int = listOf(title, text, subText, bigText).hashCode()
}
