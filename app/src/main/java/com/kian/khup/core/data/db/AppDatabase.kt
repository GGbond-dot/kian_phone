package com.kian.khup.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.AppSession
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.CategoryUsageCache
import com.kian.khup.core.data.db.entities.ChatMessage
import com.kian.khup.core.data.db.entities.ChatSession
import com.kian.khup.core.data.db.entities.ClassificationFeedback
import com.kian.khup.core.data.db.entities.ContentThemeTag
import com.kian.khup.core.data.db.entities.DailyPlan
import com.kian.khup.core.data.db.entities.DailyReview
import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.HourlySummary
import com.kian.khup.core.data.db.entities.RuleState
import com.kian.khup.core.data.db.entities.TodayNarration
import com.kian.khup.core.data.db.entities.TriggerTag
import com.kian.khup.core.data.db.entities.UserFeedback
import com.kian.khup.core.data.db.entities.UserMemory

@Database(
    entities = [
        Event::class,
        DerivedResult::class,
        AppSession::class,
        AnomalySuggestion::class,
        RuleState::class,
        ActionLog::class,
        ClassificationFeedback::class,
        ChatMessage::class,
        ChatSession::class,
        HourlySummary::class,
        DailyReview::class,
        AttentionAnomaly::class,
        TriggerTag::class,
        ContentThemeTag::class,
        CategoryUsageCache::class,
        UserFeedback::class,
        UserMemory::class,
        DailyPlan::class,
        TodayNarration::class,
    ],
    version = 17,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun derivedResultDao(): DerivedResultDao
    abstract fun anomalySuggestionDao(): AnomalySuggestionDao
    abstract fun userFeedbackDao(): UserFeedbackDao
    abstract fun classificationFeedbackDao(): ClassificationFeedbackDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun attentionAnomalyDao(): AttentionAnomalyDao
    abstract fun triggerTagDao(): TriggerTagDao
    abstract fun contentThemeTagDao(): ContentThemeTagDao
    abstract fun categoryUsageCacheDao(): CategoryUsageCacheDao
    abstract fun dailyPlanDao(): DailyPlanDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun todayNarrationDao(): TodayNarrationDao
    // TODO Phase 2+：RuleStateDao
}
