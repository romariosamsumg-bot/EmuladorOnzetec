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
    fun `romset kof98 do fbneo`() {
        val (game, roms) = dat.romset(SystemRegistry.Ids.NEO_GEO, "kof98")!!
        assertEquals("The King of Fighters '98 - The Slugfest", game.title)
        assertEquals("1998", game.year)
        assertTrue(roms.any { it.name == "242-p1.p1" && it.crc == 0x8893DF89L && !it.merge })
        assertTrue(roms.any { it.name == "sp-s2.sp1" && it.merge })
        assertTrue(dat.romset(SystemRegistry.Ids.NEO_GEO, "neogeo")!!.first.isBios)
    }
}
