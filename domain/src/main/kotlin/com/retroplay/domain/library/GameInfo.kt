package com.retroplay.domain.library

/** Resumo de um jogo (ex.: o primeiro parágrafo da Wikipédia) e de onde veio. */
data class GameSummary(val text: String, val source: String, val url: String?)

/** Busca o resumo de um jogo. Devolve null quando não há (ou sem internet). */
interface GameInfoSource {
    suspend fun summary(title: String, systemName: String): GameSummary?
}
