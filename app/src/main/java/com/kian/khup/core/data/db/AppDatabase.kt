package com.kian.khup.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kian.khup.core.data.db.entities.ActionLog
import com.kian.khup.core.data.db.entities.AppSession
import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import com.kian.khup.core.data.db.entities.RuleState

@Database(
    entities = [
        Event::class,
        DerivedResult::class,
        AppSession::class,
        RuleState::class,
        ActionLog::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    // TODO Phase 2+：DerivedResultDao / AppSessionDao / RuleStateDao / ActionLogDao
}
