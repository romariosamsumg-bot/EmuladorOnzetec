package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {

    private fun g(
        id: Long, title: String, system: SystemId? = SystemRegistry.Ids.SNES, genre: String? = null,
        fav: Boolean = false, played: Long? = null, count: Int = 0, added: Long = id, thumbs: Int = 1,
    ) = Game(
        id, "/x/$id", GameSource.FILE, 1, "$title.sfc", 1, system, title, null, null, null, null, genre, null, thumbs, null, null,
        IdentStatus.IDENTIFIED, RomsetStatus.NOT_APPLICABLE, 0, fav, count, played, added, 0,
    )

    private val games = listOf(
        g(1, "Zelda", genre = "Action", played = 500, count = 3),
        g(2, "Mario", genre = "Platform", fav = true, played = 900, count = 10),
        g(3, "Sonic", system = SystemRegistry.Ids.MEGA_DRIVE, genre = "Platform", added = 99),
        g(4, "Alex Kidd", system = SystemRegistry.Ids.MASTER_SYSTEM, genre = "Platform"),
        g(5, "Donkey Kong", genre = "Platform"),
        g(6, "Quebrado", system = null),
    )

    private fun rows() = HomeRowsBuilder.build(games, SystemRegistry)

    @Test
    fun `ordem e conteudo das fileiras`() {
        val keys = rows().map { it.type.key }
        assertEquals(
            listOf("continue", "favorites", "recent", "system_snes", "system_megadrive", "system_mastersystem", "most_played", "genre_Platform"),
            keys,
        )
        val continuePlaying = rows().first { it.type == RowType.ContinuePlaying }.games.map { it.title }
        assertEquals(listOf("Mario", "Zelda"), continuePlaying)
        assertEquals("Sonic", rows().first { it.type == RowType.RecentlyAdded }.games.first().title)
        assertEquals(listOf("Donkey Kong", "Mario", "Zelda"), rows().first { it.type == RowType.BySystem(SystemRegistry.Ids.SNES) }.games.map { it.title })
        assertEquals("Mario", rows().first { it.type == RowType.MostPlayed }.games.first().title)
    }

    @Test
    fun `sem console e fileiras vazias ficam de fora`() {
        assertTrue(rows().none { r -> r.games.any { it.systemId == null } })
        assertTrue(rows().none { it.type == RowType.BySystem(SystemRegistry.Ids.NEO_GEO) })
        assertTrue(rows().none { it.type == RowType.ByGenre("Action") }) // menos de 4 jogos
    }

    @Test
    fun `destaque e o ultimo jogado ou aleatorio estavel`() {
        assertEquals("Mario", HomeRowsBuilder.hero(games, seed = 7)!!.title)
        val neverPlayed = games.map { it.copy(lastPlayedAt = null) }
        assertEquals(HomeRowsBuilder.hero(neverPlayed, 42), HomeRowsBuilder.hero(neverPlayed, 42))
        assertNull(HomeRowsBuilder.hero(emptyList(), 1))
    }

    @Test
    fun `busca ignora acentos e maiusculas e prioriza prefixo`() {
        val list = listOf(g(1, "Pokémon"), g(2, "Super Mario World"), g(3, "Mario Paint"), g(4, "Dr. Mario"))
        assertEquals(listOf("Pokémon"), SearchMatcher.search(list, "POKEMON").map { it.title })
        assertEquals(listOf("Mario Paint", "Dr. Mario", "Super Mario World"), SearchMatcher.search(list, "mario").map { it.title })
        assertEquals(listOf("Super Mario World"), SearchMatcher.search(list, "mario wor").map { it.title })
        assertTrue(SearchMatcher.search(list, "  ").isEmpty())
    }

    @Test
    fun `filtros da pagina do console`() {
        assertEquals("Alex Kidd", games.applyFilter(GameFilter.AZ).first().title)
        assertEquals("Mario", games.applyFilter(GameFilter.RECENT).first().title)
        assertEquals("Mario", games.applyFilter(GameFilter.MOST_PLAYED).first().title)
        assertEquals(listOf("Mario"), games.applyFilter(GameFilter.FAVORITES).map { it.title })
    }
}
