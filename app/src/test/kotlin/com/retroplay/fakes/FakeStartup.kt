package com.retroplay.fakes

import com.retroplay.di.StartupModule
import com.retroplay.feature.profiles.StartupOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/**
 * Nos testes de interface o app abre direto na Início (sem abertura nem boas-vindas; a escolha
 * de perfil só aparece com mais de um perfil). StartupFlowTest liga tudo explicitamente.
 */
object TestStartup {
    @Volatile var options = StartupOptions(intro = false, alwaysAsk = false, welcome = false)
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StartupModule::class])
object FakeStartupModule {
    @Provides
    fun startupOptions(): StartupOptions = TestStartup.options
}
