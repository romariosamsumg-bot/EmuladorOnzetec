package com.retroplay.di

import com.retroplay.feature.profiles.StartupOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Trilha de inicialização (abertura, escolha de perfil e boas-vindas). Os testes a desligam. */
@Module
@InstallIn(SingletonComponent::class)
object StartupModule {
    @Provides
    fun startupOptions(): StartupOptions = StartupOptions()
}
