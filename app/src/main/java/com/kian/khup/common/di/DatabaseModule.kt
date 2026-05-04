package com.kian.khup.common.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.AppSessionDao
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
            .addMigrations(MIGRATION_2_3)
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
}
