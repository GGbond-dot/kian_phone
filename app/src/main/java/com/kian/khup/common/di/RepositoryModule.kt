package com.kian.khup.common.di

import com.kian.khup.core.data.repository.AnomalySuggestionRepository
import com.kian.khup.core.data.repository.AnomalySuggestionRepositoryImpl
import com.kian.khup.core.data.repository.BehaviorReportRepository
import com.kian.khup.core.data.repository.BehaviorReportRepositoryImpl
import com.kian.khup.core.data.repository.DailyPlanRepository
import com.kian.khup.core.data.repository.DailyPlanRepositoryImpl
import com.kian.khup.core.data.repository.UserFeedbackRepository
import com.kian.khup.core.data.repository.UserFeedbackRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBehaviorReportRepository(
        impl: BehaviorReportRepositoryImpl,
    ): BehaviorReportRepository

    @Binds
    @Singleton
    abstract fun bindAnomalySuggestionRepository(
        impl: AnomalySuggestionRepositoryImpl,
    ): AnomalySuggestionRepository

    @Binds
    @Singleton
    abstract fun bindUserFeedbackRepository(
        impl: UserFeedbackRepositoryImpl,
    ): UserFeedbackRepository

    @Binds
    @Singleton
    abstract fun bindDailyPlanRepository(impl: DailyPlanRepositoryImpl): DailyPlanRepository
}
