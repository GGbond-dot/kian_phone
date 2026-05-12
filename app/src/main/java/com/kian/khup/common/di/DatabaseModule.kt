package com.kian.khup.common.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.CategoryUsageCacheDao
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.ClassificationFeedbackDao
import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.DailyPlanDao
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.UserMemoryDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.migrations.MIGRATION_10_11
import com.kian.khup.core.data.db.migrations.MIGRATION_11_12
import com.kian.khup.core.data.db.migrations.MIGRATION_12_13
import com.kian.khup.core.data.db.migrations.MIGRATION_13_14
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
            .addMigrations(
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
            )
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideAppSessionDao(db: AppDatabase): AppSessionDao = db.appSessionDao()

    @Provides
    fun provideDerivedResultDao(db: AppDatabase): DerivedResultDao = db.derivedResultDao()

    @Provides
    fun provideAnomalySuggestionDao(db: AppDatabase): AnomalySuggestionDao = db.anomalySuggestionDao()

    @Provides
    fun provideUserFeedbackDao(db: AppDatabase): UserFeedbackDao = db.userFeedbackDao()

    @Provides
    fun provideClassificationFeedbackDao(db: AppDatabase): ClassificationFeedbackDao =
        db.classificationFeedbackDao()

    @Provides
    fun provideActionLogDao(db: AppDatabase): ActionLogDao = db.actionLogDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    fun provideChatSessionDao(db: AppDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideHourlySummaryDao(db: AppDatabase): HourlySummaryDao = db.hourlySummaryDao()

    @Provides
    fun provideDailyReviewDao(db: AppDatabase): DailyReviewDao = db.dailyReviewDao()

    @Provides
    fun provideAttentionAnomalyDao(db: AppDatabase): AttentionAnomalyDao = db.attentionAnomalyDao()

    @Provides
    fun provideTriggerTagDao(db: AppDatabase): TriggerTagDao = db.triggerTagDao()

    @Provides
    fun provideContentThemeTagDao(db: AppDatabase): ContentThemeTagDao = db.contentThemeTagDao()

    @Provides
    fun provideCategoryUsageCacheDao(db: AppDatabase): CategoryUsageCacheDao = db.categoryUsageCacheDao()

    @Provides
    fun provideDailyPlanDao(db: AppDatabase): DailyPlanDao = db.dailyPlanDao()

    @Provides
    fun provideUserMemoryDao(db: AppDatabase): UserMemoryDao = db.userMemoryDao()

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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_message (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    role TEXT NOT NULL,
                    text TEXT NOT NULL,
                    providerTier TEXT,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_message_timestamp ON chat_message(timestamp)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hourly_summary (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    windowStartMs INTEGER NOT NULL,
                    windowEndMs INTEGER NOT NULL,
                    summary TEXT NOT NULL,
                    eventCount INTEGER NOT NULL,
                    topPackages TEXT NOT NULL,
                    importance INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    modelVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_hourly_summary_windowStartMs ON hourly_summary(windowStartMs)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_review (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    summary TEXT NOT NULL,
                    highlights TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    modelVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_daily_review_dayStartMs ON daily_review(dayStartMs)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_session (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    lastMessagePreview TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_session_updatedAt ON chat_session(updatedAt)")

            // 是否有遗留消息，决定是否需要建“旧对话”兜底会话。
            val hadLegacyMessages = db.query("SELECT COUNT(*) FROM chat_message").use { c ->
                c.moveToFirst() && c.getLong(0) > 0
            }

            val defaultSessionId: Long? = if (hadLegacyMessages) {
                db.execSQL(
                    "INSERT INTO chat_session(title, createdAt, updatedAt, lastMessagePreview) " +
                        "VALUES('旧对话', $now, $now, NULL)"
                )
                db.query("SELECT last_insert_rowid()").use { c ->
                    if (c.moveToFirst()) c.getLong(0) else null
                }
            } else null

            // chat_message 加 sessionId + FK + 索引（SQLite 不支持加 FK，需要重建表）。
            db.execSQL(
                """
                CREATE TABLE chat_message_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sessionId INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    text TEXT NOT NULL,
                    providerTier TEXT,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES chat_session(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            if (defaultSessionId != null) {
                db.execSQL(
                    "INSERT INTO chat_message_new(id, sessionId, role, text, providerTier, timestamp) " +
                        "SELECT id, $defaultSessionId, role, text, providerTier, timestamp FROM chat_message"
                )
                // 更新默认会话的预览 / updatedAt 为最后一条消息。
                db.execSQL(
                    """
                    UPDATE chat_session
                    SET updatedAt = COALESCE((SELECT MAX(timestamp) FROM chat_message_new WHERE sessionId = $defaultSessionId), updatedAt),
                        lastMessagePreview = (
                            SELECT substr(text, 1, 60) FROM chat_message_new
                            WHERE sessionId = $defaultSessionId
                            ORDER BY id DESC LIMIT 1
                        )
                    WHERE id = $defaultSessionId
                    """.trimIndent()
                )
            }

            db.execSQL("DROP TABLE chat_message")
            db.execSQL("ALTER TABLE chat_message_new RENAME TO chat_message")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chat_message_sessionId_timestamp ON chat_message(sessionId, timestamp)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chat_message_timestamp ON chat_message(timestamp)"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS attention_anomaly (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    anomalyKey TEXT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    severity INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    packageName TEXT,
                    metricValue INTEGER NOT NULL,
                    baselineValue INTEGER,
                    windowStartMs INTEGER,
                    windowEndMs INTEGER,
                    createdAt INTEGER NOT NULL,
                    ruleVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attention_anomaly_dayStartMs ON attention_anomaly(dayStartMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attention_anomaly_type ON attention_anomaly(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_attention_anomaly_severity ON attention_anomaly(severity)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_attention_anomaly_anomalyKey ON attention_anomaly(anomalyKey)"
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS trigger_tags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    packageName TEXT,
                    tag TEXT NOT NULL,
                    confidence INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    ruleVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_tags_dayStartMs ON trigger_tags(dayStartMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_trigger_tags_tag ON trigger_tags(tag)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trigger_tags_sourceType_sourceId ON trigger_tags(sourceType, sourceId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_trigger_tags_dayStartMs_sourceType_sourceId_tag " +
                    "ON trigger_tags(dayStartMs, sourceType, sourceId, tag)"
            )
            db.execSQL(
                "UPDATE derived_results SET classification = '消费信息' WHERE classification = '金融通知'"
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS content_theme_tags (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    packageName TEXT,
                    theme TEXT NOT NULL,
                    confidence INTEGER NOT NULL,
                    evidence TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    ruleVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_content_theme_tags_dayStartMs ON content_theme_tags(dayStartMs)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_content_theme_tags_theme ON content_theme_tags(theme)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_content_theme_tags_sourceType_sourceId " +
                    "ON content_theme_tags(sourceType, sourceId)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_content_theme_tags_dayStartMs_sourceType_sourceId_theme " +
                    "ON content_theme_tags(dayStartMs, sourceType, sourceId, theme)"
            )
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS category_usage_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    dayStartMs INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    foregroundMs INTEGER NOT NULL,
                    computedAt INTEGER NOT NULL,
                    ruleVersion TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_category_usage_cache_dayStartMs " +
                    "ON category_usage_cache(dayStartMs)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_category_usage_cache_category " +
                    "ON category_usage_cache(category)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_category_usage_cache_dayStartMs_category " +
                    "ON category_usage_cache(dayStartMs, category)"
            )
        }
    }
}
