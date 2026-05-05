package com.kian.khup.common.di

import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.HybridLlmEngine
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
}
