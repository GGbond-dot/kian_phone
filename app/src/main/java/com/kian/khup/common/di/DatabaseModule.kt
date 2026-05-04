package com.kian.khup.common.di

import android.content.Context
import androidx.room.Room
import com.kian.khup.core.data.db.AppDatabase
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
            // 开发期 schema 还在变，destructive migration 简单粗暴。
            // TODO: 1.0 发布前补正常的 Migration 链。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
}
