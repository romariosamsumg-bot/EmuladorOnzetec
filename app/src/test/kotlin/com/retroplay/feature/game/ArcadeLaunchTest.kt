package com.retroplay.feature.game

import com.retroplay.data.rom.RomCompanion
import com.retroplay.domain.library.BiosStatus
import com.retroplay.domain.library.DatGame
import com.retroplay.domain.library.DatLookup
import com.retroplay.domain.library.DatRom
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository.Companion.game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeLaunchTest {

    private val neo = SystemRegistry.ARCADE
    private val dat = object : DatLookup {
        override fun byCrc(crc: Long, systems: Collection<SystemId>) = emptyList<Pair<DatGame, DatRom>>()
        override fun byTitle(system: SystemId, normalizedTitle: String): DatGame? = null
        override fun romset(system: SystemId, setName: String): Pair<DatGame, List<DatRom>>? = when (setName) {
            "kof98" -> dg("kof98", null, "neogeo") to emptyList()
            "kof98a" -> dg("kof98a", "kof98", "neogeo") to emptyList()
            "sf2" -> dg("sf2", null, null) to emptyList()
            else -> null
        }
        override fun romsets(system: SystemId, setName: String) = when (setName) {
            // O mesmo romset com pai diferente no FBNeo (para conferir que vale o núcleo em uso).
            "kof98a" -> listOfNotNull(romset(system, setName), dg("kof98a", null, "neogeo", core = "fbneo") to emptyList())
            else -> listOfNotNull(romset(system, setName))
        }
        private fun dg(name: String, parent: String?, bios: String?, core: String = "mame2000") =
            DatGame(1, neo.id, name, name, null, null, null, null, 2, 0, null, romset = name, parent = parent, core = core, bios = bios)
    }
    private val bios = BiosStatus(neo.id, "neogeo", "/storage/emulated/0/Jogos/BIOS/neogeo.zip", complete = true)
    private val kof98 = game(1, neo.id, "KOF 98", "kof98.zip")
    private val kof98a = game(2, neo.id, "KOF 98 (alt)", "kof98a.zip")

    @Test
    fun sem_bios_nao_abre() {
        assertEquals(ArcadeCheck.BiosMissing, ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), emptyList()))
    }

    @Test
    fun clone_leva_o_pai_de_qualquer_pasta_da_biblioteca() {
        val r = ArcadeLaunch.check(neo, "kof98a.zip", dat, listOf(kof98, kof98a), listOf(bios), "mame2000") as ArcadeCheck.Ready
        // Pai primeiro; a BIOS vai junto para a pasta do jogo (o MAME só procura lá).
        assertEquals(listOf(RomCompanion(kof98.uri, "kof98.zip"), RomCompanion(bios.uri, "neogeo.zip")), r.companions)
        assertEquals(bios, r.bios)
        assertTrue(r.warnings.isEmpty())
        assertTrue(r.neoGeo)
        // No FBNeo esse clone não depende do pai.
        val fb = ArcadeLaunch.check(neo, "kof98a.zip", dat, listOf(kof98a), listOf(bios), "fbneo") as ArcadeCheck.Ready
        assertEquals(listOf(RomCompanion(bios.uri, "neogeo.zip")), fb.companions)
    }

    @Test
    fun clone_sem_pai_explica_o_que_falta() {
        assertEquals(ArcadeCheck.ParentMissing("kof98"), ArcadeLaunch.check(neo, "KOF98A.ZIP", dat, listOf(kof98a), listOf(bios)))
    }

    @Test
    fun bios_incompleta_avisa_mas_abre_e_prefere_a_completa() {
        val partial = bios.copy(uri = "/x/neogeo.zip", complete = false)
        val r = ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), listOf(partial)) as ArcadeCheck.Ready
        assertEquals(listOf(ArcadeWarning.BIOS_INCOMPLETE), r.warnings)
        val both = ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), listOf(partial, bios)) as ArcadeCheck.Ready
        assertEquals(bios, both.bios)
    }

    @Test
    fun bios_do_mame_037_nao_serve_para_o_fbneo() {
        // neogeo.zip antiga: completa para o MAME 2000, mas o FBNeo pede arquivos que ela não tem.
        val old = bios.copy(byCore = mapOf("mame2000" to true, "fbneo" to false))
        assertEquals(ArcadeCheck.BiosWrongVersion("fbneo", "neogeo.zip"), ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), listOf(old), "fbneo"))
        // No MAME 2000 abre normalmente.
        assertTrue(ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), listOf(old), "mame2000") is ArcadeCheck.Ready)
        // Com a nova também no disco, usa a que serve.
        val new = bios.copy(uri = "/y/neogeo.zip", byCore = mapOf("mame2000" to false, "fbneo" to true))
        val r = ArcadeLaunch.check(neo, "kof98.zip", dat, listOf(kof98), listOf(old, new), "fbneo") as ArcadeCheck.Ready
        assertEquals(new, r.bios)
        // BiosStatus: "ok" para todos os núcleos só se serve para todos.
        assertTrue(!old.completeForAll && !new.completeForAll && bios.completeForAll)
        assertEquals(listOf("fbneo"), old.failingCores)
    }

    @Test
    fun consoles_domesticos_nao_passam_pela_verificacao() {
        val r = ArcadeLaunch.check(SystemRegistry.SNES, "mario.sfc", dat, emptyList(), emptyList()) as ArcadeCheck.Ready
        assertTrue(r.companions.isEmpty())
    }

    @Test
    fun jogo_sem_bios_abre_sem_neogeo_zip() {
        val r = ArcadeLaunch.check(neo, "sf2.zip", dat, emptyList(), emptyList(), "mame2000") as ArcadeCheck.Ready
        assertTrue(r.companions.isEmpty())
        assertEquals(null, r.bios)
        assertTrue(!r.neoGeo)
    }

    @Test
    fun memoria() {
        val mb = 1024L * 1024
        assertTrue(ArcadeLaunch.isLowMemory(300 * mb, 400 * mb))
        assertFalse(ArcadeLaunch.isLowMemory(60 * mb, 400 * mb))
        assertFalse(ArcadeLaunch.isLowMemory(0, 10))
    }
}
