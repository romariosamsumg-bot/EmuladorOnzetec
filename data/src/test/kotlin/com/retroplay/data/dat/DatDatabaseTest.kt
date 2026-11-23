package com.retroplay.data.dat

import androidx.test.core.app.ApplicationProvider
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Consultas ao banco REAL gerado por tools/dat/build_database.py. */
@RunWith(RobolectricTestRunner::class)
class DatDatabaseTest {

    private val dat = DatDatabase(ApplicationProvider.getApplicationContext())
    private val snes = SystemRegistry.Ids.SNES

    @Test
    fun `super mario world pelo crc`() {
        val (game, rom) = dat.byCrc(0xB19ED489L, listOf(snes)).single()
        assertEquals("Super Mario World (USA)", game.name)
        assertEquals("Super Mario World", game.title)
        assertEquals("1991", game.year)
        assertEquals("Platform", game.genre)
        assertEquals(2, game.players)
        assertEquals(7, game.thumbs)
        assertEquals(524288L, rom.size)
        assertNotNull(rom.md5)
    }

    @Test
    fun `crc de outro console nao casa`() {
        assertTrue(dat.byCrc(0xB19ED489L, listOf(SystemRegistry.Ids.MEGA_DRIVE)).isEmpty())
    }

    @Test
    fun `titulo com artigo e reorganizado`() {
        val zelda = dat.byTitle(snes, "thelegendofzeldaalinktothepast")
        assertNotNull(zelda)
        assertEquals("The Legend of Zelda - A Link to the Past", zelda!!.title)
        assertEquals("USA", zelda.region) // prefere a versão americana
    }

    @Test
    fun `romset kof98 nos dats do mame 0_37 e do fbneo`() {
        val versions = dat.romsets(SystemRegistry.Ids.ARCADE, "kof98")
        assertEquals(listOf("mame2000", "fbneo"), versions.map { it.first.core })
        versions.forEach { (game, _) ->
            assertEquals("The King of Fighters '98 - The Slugfest", game.title)
            assertEquals("1998", game.year)
            assertEquals("neogeo", game.bios)
        }
        val (_, fb) = versions.last()
        assertTrue(fb.any { it.name == "242-p1.p1" && it.crc == 0x8893DF89L && !it.merge })
        assertTrue(fb.any { it.name == "sp-s2.sp1" && it.merge })
        assertTrue(dat.romsets(SystemRegistry.Ids.ARCADE, "neogeo").all { it.first.isBios })
        // Jogos sem BIOS e clones.
        assertEquals(null, dat.romset(SystemRegistry.Ids.ARCADE, "sf2")!!.first.bios)
        assertEquals("mslug2", dat.romsets(SystemRegistry.Ids.ARCADE, "mslug2").first().first.romset)
    }
}
