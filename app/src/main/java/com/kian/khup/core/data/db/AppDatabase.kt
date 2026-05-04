package com.kian.khup.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AppSession
import com.kian.khup.core.data.db.entities.ClassificationFeedback
import com.kian.khup.core.data.db.entities.DailyTask
import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.RuleState

@Database(
    entities = [
        Event::class,
        DerivedResult::class,
        AppSession::class,
        DailyTask::class,
        RuleState::class,
        ActionLog::class,
        ClassificationFeedback::class,
    ],
    version = 4,
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
    // TODO Phase 2+：RuleStateDao
}
