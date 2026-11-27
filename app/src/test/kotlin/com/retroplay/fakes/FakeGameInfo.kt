package com.retroplay.fakes

import com.retroplay.data.di.InfoModule
import com.retroplay.domain.library.GameInfoSource
import com.retroplay.domain.library.GameSummary
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

/** Resumos sem internet nos testes: só alguns jogos têm. */
object FakeGameInfo : GameInfoSource {
    override suspend fun summary(title: String, systemName: String): GameSummary? = when (title) {
        "Super Mario World" -> GameSummary("Super Mario World é um jogo eletrônico de plataforma lançado em 1990 para o Super Nintendo.", "Wikipédia", null)
        "Sonic The Hedgehog" -> GameSummary("Sonic the Hedgehog é um jogo de plataforma de 1991 para o Mega Drive.", "Wikipédia", null)
        else -> null
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [InfoModule::class])
object FakeGameInfoModule {
    @Provides
    fun gameInfo(): GameInfoSource = FakeGameInfo
}
