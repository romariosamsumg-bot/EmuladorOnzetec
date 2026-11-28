package com.retroplay.fakes

import com.retroplay.di.UpdateModule
import com.retroplay.feature.update.UpdateConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Testes não procuram atualização na internet. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [UpdateModule::class])
object FakeUpdateModule {
    @Provides
    fun updateConfig(): UpdateConfig = UpdateConfig(null)
}
