package com.retroplay.domain.library

import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopularGamesTest {

    private fun game(id: Long, system: com.retroplay.domain.system.SystemId, title: String, datName: String? = null, fileName: String = "$title.bin", region: String? = "USA") = Game(
        id = id, uri = "/x/$fileName", source = GameSource.FILE, folderId = 1, fileName = fileName,
        sizeBytes = 1, systemId = system, title = title, datName = datName,
        region = region, year = null, developer = null, genre = null, players = null, thumbs = 0, thumbName = null,
        coverOverride = null, status = IdentStatus.IDENTIFIED, romsetStatus = RomsetStatus.NOT_APPLICABLE, missingFiles = 0,
        playCount = 0, lastPlayedAt = null, addedAt = id, playTimeMs = 0, isFavorite = false,
    )

    private val snes = SystemRegistry.Ids.SNES
    private val md = SystemRegistry.Ids.MEGA_DRIVE
    private val arcade = SystemRegistry.Ids.ARCADE

    @Test
    fun normaliza_artigo_pontuacao_acentos_e_regiao() {
        assertEquals("legend of zelda a link to past", PopularGames.normalize("Legend of Zelda, The - A Link to the Past (Europe)"))
        assertEquals(PopularGames.normalize("Street Fighter II: The World Warrior"), PopularGames.normalize("Street Fighter II - The World Warrior"))
        assertEquals("monica no castelo do dragao", PopularGames.normalize("Mônica no Castelo do Dragão (Brazil)"))
    }

    @Test
    fun ranking_por_console_com_uma_versao_por_jogo() {
        val games = listOf(
            game(1, snes, "Zombies Ate My Neighbors"),
            game(2, snes, "Super Mario World", region = "Europe"),
            game(3, snes, "Super Mario World", region = "USA"),
            game(4, snes, "The Legend of Zelda - A Link to the Past"),
            game(5, snes, "Jogo Obscuro Qualquer"),
            game(6, md, "Sonic The Hedgehog"),
        )
        val popular = PopularGames.forSystem(games, snes)
        assertEquals(listOf(3L, 4L, 1L), popular.map { it.id }) // EUA preferido; obscuro fica de fora
        assertNull(PopularGames.rank(games[4]))
    }

    @Test
    fun arcade_casa_pelo_romset_e_titulo_com_dois_pontos() {
        val sf2 = game(1, arcade, "Street Fighter II: The World Warrior", fileName = "sf2.zip")
        val kof = game(2, arcade, "kof97", fileName = "kof97.zip") // não identificado: só o arquivo
        val pac = game(3, arcade, "PuckMan", fileName = "pacman.zip")
        assertTrue(PopularGames.rank(sf2) != null)
        assertTrue(PopularGames.rank(kof) != null)
        assertTrue(PopularGames.rank(pac) != null)
    }

    @Test
    fun geral_intercala_os_consoles_pelo_ranking() {
        val games = listOf(
            game(1, snes, "Super Mario World"),
            game(2, snes, "Donkey Kong Country"),
            game(3, md, "Sonic The Hedgehog"),
            game(4, md, "Sonic The Hedgehog 2"),
        )
        val overall = PopularGames.overall(games, listOf(snes, md))
        assertEquals(listOf(1L, 3L, 2L, 4L), overall.map { it.id })
    }

    @Test
    fun filtro_populares_da_pagina_do_console() {
        val games = listOf(game(1, md, "Ristar"), game(2, md, "Sonic The Hedgehog"), game(3, md, "Sem Lista"))
        assertEquals(listOf(2L, 1L), games.applyFilter(GameFilter.POPULAR).map { it.id })
        assertTrue(emptyList<Game>().applyFilter(GameFilter.POPULAR).isEmpty())
    }
}

class PopularRowTest {
    @Test
    fun fileira_mais_populares_vem_depois_dos_favoritos() {
        val g = Game(
            id = 1, uri = "/x", source = GameSource.FILE, folderId = 1, fileName = "smw.sfc", sizeBytes = 1,
            systemId = SystemRegistry.Ids.SNES, title = "Super Mario World", datName = null, region = "USA", year = null,
            developer = null, genre = null, players = null, thumbs = 0, thumbName = null, coverOverride = null,
            status = IdentStatus.IDENTIFIED, romsetStatus = RomsetStatus.NOT_APPLICABLE, missingFiles = 0,
            playCount = 0, lastPlayedAt = null, addedAt = 1, playTimeMs = 0, isFavorite = true,
        )
        val keys = HomeRowsBuilder.build(listOf(g), SystemRegistry).map { it.type.key }
        assertEquals(listOf("favorites", "popular", "recent", "system_snes"), keys)
    }
}
