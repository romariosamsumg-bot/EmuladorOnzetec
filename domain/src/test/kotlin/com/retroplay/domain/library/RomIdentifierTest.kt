package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomIdentifierTest {

    private val snes = SystemRegistry.Ids.SNES
    private val md = SystemRegistry.Ids.MEGA_DRIVE
    private val sms = SystemRegistry.Ids.MASTER_SYSTEM
    private val neo = SystemRegistry.Ids.ARCADE

    private fun dg(id: Long, system: SystemId, name: String, romset: String? = null, parent: String? = null, bios: Boolean = false) =
        DatGame(id, system, name, name.substringBefore(" ("), null, null, null, null, null, 1, null, romset, parent, bios)

    private val smw = dg(1, snes, "Super Mario World (USA)")
    private val sonic = dg(2, md, "Sonic The Hedgehog (USA, Europe)")
    private val alexMd5 = byteArrayOf(1, 2, 3)
    private val alex = dg(3, sms, "Alex Kidd (USA)")
    private val alexJp = dg(4, sms, "Alex Kidd (Japan)")
    private val kof98 = dg(10, neo, "King of Fighters '98", romset = "kof98")
    private val kof98k = dg(11, neo, "King of Fighters '98 (Korea)", romset = "kof98k", parent = "kof98")
    private val biosGame = dg(12, neo, "Neo Geo", romset = "neogeo", bios = true)

    private val dat = object : DatLookup {
        val crcs = mapOf(
            0xB19ED489L to listOf(smw to DatRom("smw.sfc", 524288, 0xB19ED489L, false)),
            0xF9394E97L to listOf(sonic to DatRom("sonic.md", 524288, 0xF9394E97L, false)),
            0xAAAA0000L to listOf(
                alex to DatRom("a.sms", 1, 0xAAAA0000L, false, alexMd5),
                alexJp to DatRom("b.sms", 1, 0xAAAA0000L, false, byteArrayOf(9)),
            ),
        )
        val sets = mapOf(
            "kof98" to (kof98 to listOf(
                DatRom("242-p1.p1", 1, 0x8893DF89L, false),
                DatRom("242-c1.c1", 1, 0xE564ECD6L, false),
                DatRom("sp-s2.sp1", 1, 0x9036D879L, true),
            )),
            "kof98k" to (kof98k to listOf(
                DatRom("242-pk1.p1", 1, 0x11111111L, false),
                DatRom("242-c1.c1", 1, 0xE564ECD6L, true),
                DatRom("sp-s2.sp1", 1, 0x9036D879L, true),
            )),
            "neogeo" to (biosGame to listOf(DatRom("sp-s2.sp1", 1, 0x9036D879L, false))),
        )

        override fun byCrc(crc: Long, systems: Collection<SystemId>) =
            crcs[crc].orEmpty().filter { it.first.system in systems }

        override fun romset(system: SystemId, setName: String) = if (system == neo) sets[setName] else null

        override fun byTitle(system: SystemId, normalizedTitle: String) =
            if (system == snes && normalizedTitle == "supermarioworld") smw else null
    }

    private val identifier = RomIdentifier(SystemRegistry, dat)

    private class Target(
        override val fileName: String,
        override val size: Long = 524288,
        private val crc: Long = 0,
        private val entries: List<ArchiveEntry> = emptyList(),
        override val parentNames: List<String> = emptyList(),
        override val siblingNames: Set<String> = emptySet(),
        private val md5: ByteArray = byteArrayOf(),
    ) : ScanTarget {
        var skipped = -1L
        var md5Calls = 0
        override fun archiveEntries() = entries
        override fun crc32(skipBytes: Long): Long { skipped = skipBytes; return crc }
        override fun md5(skipBytes: Long): ByteArray { md5Calls++; return md5 }
    }

    private fun game(o: IdentifyOutcome) = (o as IdentifyOutcome.GameResult).identification

    @Test
    fun `rom comum casa pelo crc`() {
        val id = game(identifier.identify(Target("smw.sfc", crc = 0xB19ED489L)))
        assertEquals(snes, id.system)
        assertEquals(smw, id.dat)
        assertEquals(IdentStatus.IDENTIFIED, id.status)
    }

    @Test
    fun `smc com cabecalho de copiadora pula 512 bytes`() {
        val t = Target("smw.smc", size = 524288 + 512, crc = 0xB19ED489L)
        assertEquals(smw, game(identifier.identify(t)).dat)
        assertEquals(512L, t.skipped)
    }

    @Test
    fun `bin vai para o mega drive`() {
        assertEquals(sonic, game(identifier.identify(Target("sonic.bin", crc = 0xF9394E97L))).dat)
    }

    @Test
    fun `rom desconhecida de extensao unica fica nao identificada mas no console certo`() {
        val id = game(identifier.identify(Target("meu hack.sfc", crc = 0x1234)))
        assertEquals(snes, id.system)
        assertEquals(IdentStatus.UNIDENTIFIED, id.status)
    }

    @Test
    fun `sem crc casa pelo titulo do arquivo`() {
        val id = game(identifier.identify(Target("Super Mario World (U) [!].sfc", crc = 0x1)))
        assertEquals(smw, id.dat)
    }

    @Test
    fun `zip com rom dentro usa o crc do indice`() {
        val entries = listOf(ArchiveEntry("leiame.txt", 10, 0x1), ArchiveEntry("Sonic.md", 524288, 0xF9394E97L))
        val id = game(identifier.identify(Target("sonic.zip", entries = entries)))
        assertEquals(md, id.system)
        assertEquals(sonic, id.dat)
    }

    @Test
    fun `zip ambiguo sem casamento pede o console`() {
        val entries = listOf(ArchiveEntry("jogo.bin", 1, 0x77))
        val id = game(identifier.identify(Target("jogo.zip", entries = entries)))
        // .bin só existe no Mega Drive entre os consoles suportados.
        assertEquals(md, id.system)
        val ambiguous = game(identifier.identify(Target("x.zip", entries = listOf(ArchiveEntry("a.sfc", 1, 1), ArchiveEntry("b.sms", 2, 2)))))
        assertEquals(sms, ambiguous.system) // pega a maior entrada (b.sms)
    }

    @Test
    fun `pasta com nome do console desfaz ambiguidade e escolha do usuario e lembrada`() {
        // .7z sem índice com CRC e sem dica → precisa perguntar
        val entries = listOf(ArchiveEntry("jogo.smc", 1, null))
        assertEquals(snes, game(identifier.identify(Target("jogo.7z", entries = entries))).system)
        // arquivo sem extensão de console dentro, numa pasta "Neo Geo" → romset desconhecido do Neo Geo
        val unknownSet = game(identifier.identify(Target("meujogo.zip", entries = listOf(ArchiveEntry("x.p1", 1, 5)), parentNames = listOf("Jogos", "Neo Geo"))))
        assertEquals(neo, unknownSet.system)
        assertEquals(RomsetStatus.UNKNOWN_SET, unknownSet.romsetStatus)
    }

    @Test
    fun `crc repetido entre jogos usa md5 para desempatar`() {
        val t = Target("alex.sms", crc = 0xAAAA0000L, md5 = alexMd5)
        assertEquals(alex, game(identifier.identify(t)).dat)
        assertEquals(1, t.md5Calls)
    }

    @Test
    fun `romset neo geo completo`() {
        val entries = listOf(ArchiveEntry("242-p1.p1", 1, 0x8893DF89L), ArchiveEntry("242-c1.c1", 1, 0xE564ECD6L))
        val id = game(identifier.identify(Target("kof98.zip", entries = entries)))
        assertEquals(neo, id.system)
        assertEquals(kof98, id.dat)
        assertEquals(RomsetStatus.COMPLETE, id.romsetStatus)
    }

    @Test
    fun `romset incompleto e de outra versao`() {
        val incomplete = game(identifier.identify(Target("kof98.zip", entries = listOf(ArchiveEntry("242-p1.p1", 1, 0x8893DF89L)))))
        assertEquals(RomsetStatus.INCOMPLETE, incomplete.romsetStatus)
        assertEquals(1, incomplete.missingFiles)
        val other = game(
            identifier.identify(
                Target("kof98.zip", entries = listOf(ArchiveEntry("242-p1.p1", 1, 0xDEADL), ArchiveEntry("242-c1.c1", 1, 0xE564ECD6L))),
            ),
        )
        assertEquals(RomsetStatus.WRONG_VERSION, other.romsetStatus)
    }

    @Test
    fun `clone sem o pai na pasta avisa`() {
        val entries = listOf(ArchiveEntry("242-pk1.p1", 1, 0x11111111L))
        assertEquals(RomsetStatus.NEEDS_PARENT, game(identifier.identify(Target("kof98k.zip", entries = entries))).romsetStatus)
        assertEquals(
            RomsetStatus.COMPLETE,
            game(identifier.identify(Target("kof98k.zip", entries = entries, siblingNames = setOf("kof98")))).romsetStatus,
        )
    }

    @Test
    fun `neogeo zip e reconhecido como bios e nao como jogo`() {
        val o = identifier.identify(Target("neogeo.zip", entries = listOf(ArchiveEntry("sp-s2.sp1", 1, 0x9036D879L))))
        assertTrue(o is IdentifyOutcome.Bios)
        assertTrue((o as IdentifyOutcome.Bios).bios.complete)
    }

    @Test
    fun `bios com arquivos bin nao vira jogo de mega drive`() {
        val o = identifier.identify(
            Target("neogeo.zip", entries = listOf(ArchiveEntry("sp-s2.sp1", 1, 0x9036D879L), ArchiveEntry("sp1-u4.bin", 1, 0x77))),
        )
        assertTrue(o is IdentifyOutcome.Bios)
    }

    @Test
    fun `zip de mega drive com nome igual a um romset continua mega drive`() {
        val id = game(identifier.identify(Target("kof98.zip", entries = listOf(ArchiveEntry("jogo.md", 524288, 0xF9394E97L)))))
        assertEquals(sonic, id.dat)
    }

    @Test
    fun `arquivos que nao sao jogos sao ignorados`() {
        assertEquals(IdentifyOutcome.NotAGame, identifier.identify(Target("foto.jpg")))
        assertEquals(IdentifyOutcome.NotAGame, identifier.identify(Target("docs.zip", entries = listOf(ArchiveEntry("a.pdf", 1, 1)))))
    }

    @Test
    fun `escolha do usuario restringe a busca`() {
        val id = game(identifier.identify(Target("jogo.zip", entries = listOf(ArchiveEntry("a.bin", 1, 9))), userSystem = md))
        assertEquals(md, id.system)
        assertNull(id.dat)
    }

    @Test
    fun `normalizacao de titulos`() {
        assertEquals("supermarioworld", RomIdentifier.normalizeTitle("Super Mario World (USA) [!].sfc"))
        assertEquals("thelegendofzeldaalinktothepast", RomIdentifier.normalizeTitle("Legend of Zelda, The - A Link to the Past (U).smc"))
    }
}
