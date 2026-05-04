package com.kian.khup.common.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.ClassificationFeedbackDao
import com.kian.khup.core.data.db.DailyTaskDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "khup.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            // 开发期 schema 还在变，destructive migration 简单粗暴。
            // TODO: 1.0 发布前补正常的 Migration 链。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideAppSessionDao(db: AppDatabase): AppSessionDao = db.appSessionDao()

    @Provides
    fun provideDerivedResultDao(db: AppDatabase): DerivedResultDao = db.derivedResultDao()

    @Provides
    fun provideDailyTaskDao(db: AppDatabase): DailyTaskDao = db.dailyTaskDao()

    @Provides
    fun provideClassificationFeedbackDao(db: AppDatabase): ClassificationFeedbackDao =
        db.classificationFeedbackDao()

    @Provides
    fun provideActionLogDao(db: AppDatabase): ActionLogDao = db.actionLogDao()

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    isDone INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_tasks_dayStartMs ON daily_tasks(dayStartMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_tasks_isDone ON daily_tasks(isDone)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS classification_feedback (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    eventId TEXT NOT NULL,
                    oldClassification TEXT NOT NULL,
                    newClassification TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(eventId) REFERENCES events(eventId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_classification_feedback_eventId ON classification_feedback(eventId)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_classification_feedback_createdAt ON classification_feedback(createdAt)"
            )
        }
    }
}
