package com.kian.khup.core.usage

import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.data.db.CategoryUsageCacheDao
import com.kian.khup.core.data.db.entities.CategoryUsageCache
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CategoryUsageCacheRefresher @Inject constructor(
    private val usageStatsCollector: UsageStatsCollector,
    private val categoryUsageCacheDao: CategoryUsageCacheDao,
) {
    suspend fun refreshRecentDays(todayStartMs: Long, days: Int = 8) = withContext(Dispatchers.IO) {
        repeat(days.coerceAtLeast(1)) { index ->
            val dayStart = todayStartMs - DAY_MS * index
            refreshDay(dayStart)
        }
    }

    suspend fun refreshDay(dayStartMs: Long) = withContext(Dispatchers.IO) {
        val dayEnd = dayStartMs + DAY_MS
        val now = System.currentTimeMillis()
        val rows = usageStatsCollector.getTopApps(dayStartMs, dayEnd.coerceAtMost(now), Int.MAX_VALUE)
            .groupBy { categoryForPackage(it.packageName) }
            .map { (category, apps) ->
                CategoryUsageCache(
                    dayStartMs = dayStartMs,
                    category = category,
                    foregroundMs = apps.sumOf { it.foregroundMs },
                    computedAt = now,
                    ruleVersion = RULE_VERSION,
                )
            }
            .filter { it.foregroundMs > 0 }

        categoryUsageCacheDao.deleteForDay(dayStartMs)
        if (rows.isNotEmpty()) categoryUsageCacheDao.upsertAll(rows)
    }

    fun categoryForPackage(packageName: String): String =
        when (packageName) {
            in algorithmPackages -> "算法内容"
            in socialPackages -> "社交"
            in studyWorkPackages -> "学习工作"
            in shoppingPackages -> "消费"
            in financePackages -> "必要事务"
            else -> "其他"
        }

    fun startOfDayMs(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val RULE_VERSION = "category-usage-cache-v1"

        private val algorithmPackages = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.sina.weibo",
            "com.kuaishou.nebula",
        )
        private val socialPackages = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.instagram.android",
        )
        private val studyWorkPackages = setOf(
            "com.alibaba.android.rimet",
            "com.zmzx.college.search",
            "com.yiban.app",
            "com.nowcoder.app.florida",
        )
        private val shoppingPackages = setOf(
            "com.taobao.taobao",
            "com.tmall.wireless",
            "com.jingdong.app.mall",
            "com.xunmeng.pinduoduo",
        )
        private val financePackages = setOf(
            "com.eg.android.AlipayGphone",
            "com.unionpay",
            "com.icbc",
            "com.icbc.im",
            "cmb.pb",
            "com.cmbchina.ccd.pluto.cmbActivity",
            "com.chinamworld.main",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "com.bankcomm.Bankcomm",
            "cn.com.spdb.mobilebank.per",
            "com.cmbc.cc.mbank",
        )
    }
}
