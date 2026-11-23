package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId
import java.text.Normalizer

/**
 * Jogos mais populares, por console e no geral, a partir de listas curadas embutidas no app
 * (tools/popular/popular.txt). Não há ranking ao vivo: o app cruza a lista com a biblioteca.
 *
 * O cruzamento usa o título (sem "The", acentos, pontuação e região), o nome completo do DAT
 * e, no Arcade, o nome do romset (arquivo sem a extensão).
 */
object PopularGames {

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^\\]]*]"), " ")
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(' ').filter { it.isNotEmpty() && it != "the" }
            .joinToString(" ")

    /** Por console: nome normalizado (ou "rs:" + romset) → posição no ranking. */
    private val index: Map<String, Map<String, Int>> by lazy {
        POPULAR_LISTS.mapValues { (_, entries) ->
            buildMap {
                entries.forEachIndexed { rank, names ->
                    names.forEach { n ->
                        putIfAbsent(normalize(n), rank)
                        putIfAbsent("rs:" + n.lowercase(), rank)
                    }
                }
            }
        }
    }

    /** Tamanho da lista do console (0 = sem lista). */
    fun listSize(system: SystemId): Int = POPULAR_LISTS[system.value]?.size ?: 0

    /** Posição do jogo no ranking do console dele (0 = mais popular) ou null se não está na lista. */
    fun rank(game: Game): Int? {
        val idx = index[game.systemId?.value ?: return null] ?: return null
        idx[normalize(game.title)]?.let { return it }
        game.datName?.let { idx[normalize(it)] }?.let { return it }
        val stem = game.fileName.substringBeforeLast('.').lowercase()
        idx["rs:$stem"]?.let { return it }
        return idx[normalize(stem)]
    }

    /**
     * Populares de um console, do mais para o menos popular. Se o mesmo jogo existe em várias
     * versões (EUA, Europa, Brasil...), aparece uma só: a preferida pela região.
     */
    fun forSystem(games: List<Game>, system: SystemId): List<Game> =
        games.asSequence()
            .filter { it.systemId == system }
            .mapNotNull { g -> rank(g)?.let { it to g } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .values
            .map { versions -> versions.minWith(compareBy<Game>({ regionPriority(it.region) }, { it.title.length }, { it.id })) }

    /**
     * Populares no geral: intercala os consoles pela posição no ranking (o 1º de cada console,
     * depois o 2º...), na ordem dos consoles dada.
     */
    fun overall(games: List<Game>, systems: List<SystemId>): List<Game> {
        val perSystem = systems.map { forSystem(games, it) }.filter { it.isNotEmpty() }
        val out = mutableListOf<Game>()
        var i = 0
        while (perSystem.any { i < it.size }) {
            perSystem.forEach { list -> list.getOrNull(i)?.let(out::add) }
            i++
        }
        return out
    }

    private fun regionPriority(region: String?): Int {
        val r = region?.lowercase().orEmpty()
        return when {
            "brazil" in r -> 0
            "usa" in r || "world" in r -> 1
            "europe" in r -> 2
            r.isEmpty() -> 3
            "japan" in r -> 5
            else -> 4
        }
    }
}
