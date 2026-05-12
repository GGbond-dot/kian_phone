package com.kian.khup.core.data.usecase

import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.CategoryUsageCacheDao
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.DailyPlanDao
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.UserFeedbackDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataClearUseCase @Inject constructor(
    private val eventDao: EventDao,
    private val appSessionDao: AppSessionDao,
    private val derivedResultDao: DerivedResultDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val triggerTagDao: TriggerTagDao,
    private val contentThemeTagDao: ContentThemeTagDao,
    private val categoryUsageCacheDao: CategoryUsageCacheDao,
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val anomalySuggestionDao: AnomalySuggestionDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val userFeedbackDao: UserFeedbackDao,
    private val dailyPlanDao: DailyPlanDao,
    private val dailyReviewDao: DailyReviewDao,
    private val actionLogDao: ActionLogDao,
) {
    suspend fun clearAll() {
        // 先清子表（FK），再清父表
        chatMessageDao.deleteAll()
        chatSessionDao.deleteAll()
        userFeedbackDao.deleteAll()
        anomalySuggestionDao.deleteAll()
        attentionAnomalyDao.deleteAll()
        dailyPlanDao.deleteAll()
        dailyReviewDao.deleteAll()
        hourlySummaryDao.deleteAll()
        triggerTagDao.deleteAll()
        contentThemeTagDao.deleteAll()
        categoryUsageCacheDao.deleteAll()
        derivedResultDao.deleteAll()
        appSessionDao.deleteAll()
        actionLogDao.deleteAll()
        eventDao.deleteAll()
        // user_memory 保留（不清）
    }
}
