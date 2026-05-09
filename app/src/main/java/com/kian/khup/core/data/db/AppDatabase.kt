package com.kian.khup.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AppSession
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.ChatMessage
import com.kian.khup.core.data.db.entities.ChatSession
import com.kian.khup.core.data.db.entities.ClassificationFeedback
import com.kian.khup.core.data.db.entities.DailyReview
import com.kian.khup.core.data.db.entities.DailyTask
import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.HourlySummary
import com.kian.khup.core.data.db.entities.RuleState
import com.kian.khup.core.data.db.entities.TriggerTag

@Database(
    entities = [
        Event::class,
        DerivedResult::class,
        AppSession::class,
        DailyTask::class,
        RuleState::class,
        ActionLog::class,
        ClassificationFeedback::class,
        ChatMessage::class,
        ChatSession::class,
        HourlySummary::class,
        DailyReview::class,
        AttentionAnomaly::class,
        TriggerTag::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun appSessionDao(): AppSessionDao
    abstract fun derivedResultDao(): DerivedResultDao
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun classificationFeedbackDao(): ClassificationFeedbackDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun hourlySummaryDao(): HourlySummaryDao
    abstract fun dailyReviewDao(): DailyReviewDao
    abstract fun attentionAnomalyDao(): AttentionAnomalyDao
    abstract fun triggerTagDao(): TriggerTagDao
    // TODO Phase 2+：RuleStateDao
}
