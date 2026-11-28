package com.retroplay.di

import com.retroplay.BuildConfig
import com.retroplay.feature.update.UpdateConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    @Provides
    fun updateConfig(): UpdateConfig = UpdateConfig(BuildConfig.UPDATE_URL)
}
