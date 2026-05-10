package com.kian.khup.common.di

import com.kian.khup.core.ai.HybridLlmEngine
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.anomaly.AnomalySuggestionGenerator
import com.kian.khup.core.anomaly.AnomalySuggestionGeneratorImpl
import com.kian.khup.core.anomaly.RegressionPatternGenerator
import com.kian.khup.core.anomaly.RegressionPatternGeneratorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindLlmEngine(engine: HybridLlmEngine): LlmEngine

    @Binds
    @Singleton
    abstract fun bindRegressionPatternGenerator(
        impl: RegressionPatternGeneratorImpl,
    ): RegressionPatternGenerator

    @Binds
    @Singleton
    abstract fun bindAnomalySuggestionGenerator(
        impl: AnomalySuggestionGeneratorImpl,
    ): AnomalySuggestionGenerator
}
