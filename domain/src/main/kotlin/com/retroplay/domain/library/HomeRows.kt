package com.retroplay.domain.library

import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import java.text.Normalizer

/** Tipos de fileira da tela inicial (textos ficam na UI). */
sealed interface RowType {
    val key: String

    data object ContinuePlaying : RowType { override val key = "continue" }
    data object Favorites : RowType { override val key = "favorites" }
    data object RecentlyAdded : RowType { override val key = "recent" }
    data class BySystem(val system: SystemId) : RowType { override val key = "system_${system.value}" }
    data object MostPlayed : RowType { override val key = "most_played" }
    data class ByGenre(val genre: String) : RowType { override val key = "genre_$genre" }
}

data class HomeRow(val type: RowType, val games: List<Game>)

/** Monta as fileiras e o destaque da Home a partir da biblioteca. Puro e testável. */
object HomeRowsBuilder {
    const val ROW_LIMIT = 30
    const val MIN_GENRE_GAMES = 4
    const val MAX_GENRE_ROWS = 4

    fun build(games: List<Game>, catalog: SystemCatalog, rowLimit: Int = ROW_LIMIT): List<HomeRow> {
        val playable = games.filter { it.systemId != null }
        val rows = mutableListOf<HomeRow>()
        fun add(type: RowType, list: List<Game>) {
            if (list.isNotEmpty()) rows += HomeRow(type, list.take(rowLimit))
        }
        add(RowType.ContinuePlaying, playable.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt })
        add(RowType.Favorites, playable.filter { it.isFavorite }.sortedBy { it.title.lowercase() })
        add(RowType.RecentlyAdded, playable.sortedByDescending { it.addedAt })
        for (system in catalog.all) {
            add(RowType.BySystem(system.id), playable.filter { it.systemId == system.id }.sortedBy { it.title.lowercase() })
        }
        add(RowType.MostPlayed, playable.filter { it.playCount > 0 }.sortedWith(compareByDescending<Game> { it.playCount }.thenByDescending { it.playTimeMs }))
        playable.filter { !it.genre.isNullOrBlank() }
            .groupBy { it.genre!! }
            .filterValues { it.size >= MIN_GENRE_GAMES }
            .entries.sortedByDescending { it.value.size }
            .take(MAX_GENRE_ROWS)
            .forEach { (genre, list) -> add(RowType.ByGenre(genre), list.sortedBy { it.title.lowercase() }) }
        return rows
    }

    /**
     * Jogo em destaque: o último jogado; senão um aleatório (de preferência com arte),
     * estável durante a sessão graças à [seed].
     */
    fun hero(games: List<Game>, seed: Long): Game? {
        val playable = games.filter { it.systemId != null }
        playable.filter { it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt!! }?.let { return it }
        val pool = playable.filter { it.thumbs != 0 }.ifEmpty { playable }
        if (pool.isEmpty()) return null
        val sorted = pool.sortedBy { it.id }
        return sorted[((seed % sorted.size) + sorted.size).toInt() % sorted.size]
    }
}

/** Busca por título, sem diferenciar acentos/maiúsculas; todas as palavras precisam aparecer. */
object SearchMatcher {
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun search(games: List<Game>, query: String, limit: Int = 60): List<Game> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return games.asSequence()
            .filter { it.systemId != null }
            .map { it to normalize(it.title + " " + (it.datName ?: "")) }
            .filter { (_, text) -> tokens.all { it in text } }
            // Títulos que começam com a busca vêm primeiro.
            .sortedWith(compareBy<Pair<Game, String>> { (g, _) -> if (normalize(g.title).startsWith(tokens.first())) 0 else 1 }.thenBy { it.first.title.lowercase() })
            .map { it.first }
            .take(limit)
            .toList()
    }
}

/** Ordenações/filtros da página do console. */
enum class GameFilter { AZ, RECENT, MOST_PLAYED, FAVORITES }

fun List<Game>.applyFilter(filter: GameFilter): List<Game> = when (filter) {
    GameFilter.AZ -> sortedBy { it.title.lowercase() }
    GameFilter.RECENT -> sortedWith(compareByDescending<Game> { it.lastPlayedAt ?: 0L }.thenByDescending { it.addedAt })
    GameFilter.MOST_PLAYED -> sortedWith(compareByDescending<Game> { it.playCount }.thenBy { it.title.lowercase() })
    GameFilter.FAVORITES -> filter { it.isFavorite }.sortedBy { it.title.lowercase() }
}
