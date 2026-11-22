package com.retroplay.di

import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemDetector
import com.retroplay.domain.system.SystemRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun systemCatalog(): SystemCatalog = SystemRegistry

    @Provides
    @Singleton
    fun systemDetector(catalog: SystemCatalog): SystemDetector = SystemDetector(catalog)
}
