package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Imagens de disco sintéticas (montadas aqui, byte a byte) para testar a leitura do código. */
object TestDiscs {
    private fun le32(b: ByteArray, o: Int, v: Int) { for (i in 0..3) b[o + i] = (v shr (8 * i)).toByte() }
    private fun le16(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }

    private fun dirRecord(name: String, lba: Int, size: Int): ByteArray {
        val n = name.toByteArray(Charsets.ISO_8859_1)
        val len = 33 + n.size + (if (n.size % 2 == 0) 1 else 0)
        return ByteArray(len).also { r ->
            r[0] = len.toByte(); le32(r, 2, lba); le32(r, 10, size); r[32] = n.size.toByte(); n.copyInto(r, 33)
        }
    }

    /** ISO 9660 mínima (setores de 2048) com SYSTEM.CNF apontando para [boot]. */
    fun iso(boot: String = "cdrom:\\SLUS_005.94;1"): ByteArray {
        val img = ByteArray(24 * 2048)
        val pvd = 16 * 2048
        img[pvd] = 1; "CD001".toByteArray().copyInto(img, pvd + 1)
        dirRecord("\u0000", 20, 2048).copyInto(img, pvd + 156)
        val cnf = "BOOT = $boot\r\nTCB = 4\r\nEVENT = 10\r\n".toByteArray()
        var pos = 20 * 2048
        for (r in listOf(dirRecord("\u0000", 20, 2048), dirRecord("\u0001", 20, 2048), dirRecord("PSX.EXE;1", 22, 2048), dirRecord("SYSTEM.CNF;1", 21, cnf.size))) {
            r.copyInto(img, pos); pos += r.size
        }
        cnf.copyInto(img, 21 * 2048)
        return img
    }

    /** A mesma imagem em formato cru (2352 bytes por setor, Mode 2), como as trilhas .bin do .cue. */
    fun raw(iso: ByteArray = iso()): ByteArray {
        val sectors = iso.size / 2048
        val out = ByteArray(sectors * 2352)
        for (s in 0 until sectors) {
            val o = s * 2352
            out[o] = 0; for (i in 1..10) out[o + i] = 0xFF.toByte(); out[o + 11] = 0
            out[o + 15] = 2
            iso.copyInto(out, o + 24, s * 2048, s * 2048 + 2048)
        }
        return out
    }

    /** .pbp com PARAM.SFO contendo DISC_ID. */
    fun pbp(discId: String = "SLUS00594"): ByteArray {
        val keys = "DISC_ID\u0000TITLE\u0000".toByteArray()
        val values = (discId + "\u0000").toByteArray().copyOf(16) + "Jogo\u0000".toByteArray().copyOf(8)
        val sfo = ByteArray(20 + 2 * 16 + keys.size + values.size)
        sfo[1] = 'P'.code.toByte(); sfo[2] = 'S'.code.toByte(); sfo[3] = 'F'.code.toByte()
        val keyTable = 20 + 32
        val dataTable = keyTable + keys.size
        le32(sfo, 8, keyTable); le32(sfo, 12, dataTable); le32(sfo, 16, 2)
        le16(sfo, 20, 0); le32(sfo, 24, discId.length + 1); le32(sfo, 32, 0)
        le16(sfo, 36, 8); le32(sfo, 40, 5); le32(sfo, 48, 16)
        keys.copyInto(sfo, keyTable); values.copyInto(sfo, dataTable)
        val head = ByteArray(40)
        head[1] = 'P'.code.toByte(); head[2] = 'B'.code.toByte(); head[3] = 'P'.code.toByte()
        le32(head, 8, 40); le32(head, 12, 40 + sfo.size)
        return head + sfo + ByteArray(64)
    }

    fun reader(bytes: ByteArray) = DiscImages.Reader { o, l -> bytes.copyOfRange(o.toInt().coerceAtMost(bytes.size), (o + l).toInt().coerceAtMost(bytes.size)) }
}

class DiscImagesTest {

    @Test
    fun le_codigo_do_disco_em_iso_e_em_imagem_crua() {
        val iso = TestDiscs.iso()
        assertEquals("SLUS-00594", DiscImages.isoSerial(TestDiscs.reader(iso), iso.size.toLong()))
        val raw = TestDiscs.raw(iso)
        assertEquals("SLUS-00594", DiscImages.isoSerial(TestDiscs.reader(raw), raw.size.toLong()))
        val sub = TestDiscs.iso("cdrom:\\GAME\\SCES_012.37;1")
        assertEquals("SCES-01237", DiscImages.isoSerial(TestDiscs.reader(sub), sub.size.toLong()))
        assertNull(DiscImages.isoSerial(TestDiscs.reader(ByteArray(40_000)), 40_000))
    }

    @Test
    fun le_codigo_do_pbp() {
        assertEquals("SLUS-00594", DiscImages.pbpSerial(TestDiscs.reader(TestDiscs.pbp())))
        assertNull(DiscImages.pbpSerial(TestDiscs.reader(ByteArray(100))))
    }

    @Test
    fun listas_do_cue_e_do_m3u_e_numero_do_disco() {
        val cue = "FILE \"Final Fantasy VII (USA) (Disc 1).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\n    INDEX 01 00:00:00\r\nFILE Faixa2.bin BINARY\r\n"
        assertEquals(listOf("Final Fantasy VII (USA) (Disc 1).bin", "Faixa2.bin"), DiscImages.cueFiles(cue))
        val m3u = "﻿#EXTM3U\nFF7 (Disc 1).cue\r\n\nsub/FF7 (Disc 2).cue\n"
        assertEquals(listOf("FF7 (Disc 1).cue", "FF7 (Disc 2).cue"), DiscImages.m3uEntries(m3u))
        assertEquals(2, DiscImages.discNumber("FF7 (Disc 2).cue"))
        assertEquals(3, DiscImages.discNumber("FF7 (Disco 3).chd"))
        assertNull(DiscImages.discNumber("Crash Bandicoot (USA).cue"))
        assertEquals("SCUS-94163", DiscImages.normalizeSerial("SCUS-94163-0"))
        assertEquals("SLPS-01234", DiscImages.normalizeSerial("slps_012.34"))
    }
}

/** Identificação de discos e BIOS do PlayStation pelo [RomIdentifier]. */
class DiscIdentifierTest {
    private val psx = SystemRegistry.Ids.PLAYSTATION
    private fun dg(id: Long, name: String, title: String) = DatGame(id, psx, name, title, "USA", null, null, null, null, 1, null)
    private val ff7d1 = dg(1, "Final Fantasy VII (USA) (Disc 1)", "Final Fantasy VII")
    private val ff7d2 = dg(2, "Final Fantasy VII (USA) (Disc 2)", "Final Fantasy VII")
    private val crash = dg(3, "Crash Bandicoot (USA)", "Crash Bandicoot")

    private val dat = object : DatLookup {
        override fun byCrc(crc: Long, systems: Collection<SystemId>) = emptyList<Pair<DatGame, DatRom>>()
        override fun romset(system: SystemId, setName: String): Pair<DatGame, List<DatRom>>? = null
        override fun byTitle(system: SystemId, normalizedTitle: String) = if (normalizedTitle == "crashbandicoot") crash else null
        override fun bySerial(system: SystemId, serial: String) = when (serial) {
            "SCUS-94163" -> listOf(ff7d1, ff7d2)
            else -> emptyList()
        }
    }
    private val identifier = RomIdentifier(SystemRegistry, dat)

    /** Pasta em memória: nome → conteúdo. */
    private class Folder(val files: Map<String, ByteArray>, val crcOf: Map<String, Long> = emptyMap()) {
        fun target(name: String): ScanTarget = object : ScanTarget {
            override val fileName = name
            override val size = files.getValue(name).size.toLong()
            override val parentNames = listOf("PlayStation")
            override val siblingNames = emptySet<String>()
            override fun archiveEntries() = emptyList<ArchiveEntry>()
            override fun crc32(skipBytes: Long) = crcOf[name] ?: 0L
            override fun md5(skipBytes: Long) = ByteArray(0)
            override fun read(offset: Long, length: Int): ByteArray {
                val b = files.getValue(name)
                return b.copyOfRange(offset.toInt().coerceAtMost(b.size), (offset + length).toInt().coerceAtMost(b.size))
            }
            override fun sibling(name: String) = files.keys.firstOrNull { it.equals(name, true) }?.let { target(it) }
            override val folderFiles get() = files.keys.toList()
        }
    }

    private fun game(o: IdentifyOutcome) = (o as IdentifyOutcome.GameResult).identification

    @Test
    fun cue_com_trilha_bin_identifica_pelo_codigo_e_marca_o_disco() {
        val raw = TestDiscs.raw(TestDiscs.iso("cdrom:\\SCUS_941.63;1"))
        val folder = Folder(mapOf(
            "FF7 (Disc 2).cue" to "FILE \"FF7 (Disc 2).bin\" BINARY\n TRACK 01 MODE2/2352\n".toByteArray(),
            "FF7 (Disc 2).bin" to raw,
        ))
        val id = game(identifier.identify(folder.target("FF7 (Disc 2).cue")))
        assertEquals(psx, id.system)
        assertEquals(IdentStatus.IDENTIFIED, id.status)
        assertEquals("Final Fantasy VII (USA) (Disc 2)", id.dat?.name)
        assertEquals("Final Fantasy VII (Disco 2)", id.dat?.title)
    }

    @Test
    fun m3u_vira_um_jogo_so_e_os_discos_dele_somem() {
        val raw = TestDiscs.raw(TestDiscs.iso("cdrom:\\SCUS_941.63;1"))
        val folder = Folder(mapOf(
            "FF7.m3u" to "FF7 (Disc 1).cue\nFF7 (Disc 2).cue\n".toByteArray(),
            "FF7 (Disc 1).cue" to "FILE \"FF7 (Disc 1).bin\" BINARY\n".toByteArray(),
            "FF7 (Disc 1).bin" to raw,
            "FF7 (Disc 2).cue" to "FILE \"FF7 (Disc 2).bin\" BINARY\n".toByteArray(),
            "FF7 (Disc 2).bin" to raw,
        ))
        val id = game(identifier.identify(folder.target("FF7.m3u")))
        assertEquals("Final Fantasy VII", id.dat?.title)
        assertEquals("Final Fantasy VII (USA) (Disc 1)", id.dat?.name)
        assertEquals(IdentifyOutcome.NotAGame, identifier.identify(folder.target("FF7 (Disc 1).cue")))
        assertEquals(IdentifyOutcome.NotAGame, identifier.identify(folder.target("FF7 (Disc 2).cue")))
    }

    @Test
    fun chd_e_pbp_e_iso() {
        val folder = Folder(mapOf(
            "Crash Bandicoot (USA).chd" to ByteArray(100),
            "Jogo.pbp" to TestDiscs.pbp("SCUS94163"),
            "Desconhecido.iso" to TestDiscs.iso("cdrom:\\SLUS_999.99;1"),
        ))
        assertEquals(crash, game(identifier.identify(folder.target("Crash Bandicoot (USA).chd"))).dat)
        assertEquals(ff7d1.name, game(identifier.identify(folder.target("Jogo.pbp"))).dat?.name)
        val unknown = game(identifier.identify(folder.target("Desconhecido.iso")))
        assertEquals(psx, unknown.system)
        assertEquals(IdentStatus.UNIDENTIFIED, unknown.status)
    }

    @Test
    fun bios_do_playstation_nao_vira_jogo_de_mega_drive() {
        val folder = Folder(
            mapOf("scph5501.bin" to ByteArray(512 * 1024), "bios-renomeada.bin" to ByteArray(512 * 1024), "sonic.bin" to ByteArray(512 * 1024)),
            crcOf = mapOf("bios-renomeada.bin" to 0x8D8CB7E4L, "scph5501.bin" to 0x8D8CB7E4L, "sonic.bin" to 0x1234L),
        )
        val bios = identifier.identify(folder.target("bios-renomeada.bin")) as IdentifyOutcome.Bios
        assertEquals(psx, bios.bios.system)
        assertEquals("scph5501", bios.bios.setName)
        assertTrue(bios.bios.complete)
        assertTrue(identifier.identify(folder.target("scph5501.bin")) is IdentifyOutcome.Bios)
        // ROM de Mega Drive de 512 KB com outro nome continua sendo jogo.
        assertTrue(identifier.identify(folder.target("sonic.bin")) is IdentifyOutcome.GameResult)
    }

    private fun zipTarget(name: String, entries: List<String>, parent: String = "PlayStation") = object : ScanTarget {
        override val fileName = name
        override val size = 1L
        override val parentNames = listOf(parent)
        override val siblingNames = emptySet<String>()
        override fun archiveEntries() = entries.map { ArchiveEntry(it, 1000, 0x1234L) }
        override fun crc32(skipBytes: Long) = 0L
        override fun md5(skipBytes: Long) = ByteArray(0)
    }

    @Test
    fun zip_com_disco_vira_playstation_pelo_nome_de_dentro() {
        val single = game(identifier.identify(zipTarget("crash.zip", listOf("Crash Bandicoot (USA).cue", "Crash Bandicoot (USA).bin"))))
        assertEquals(psx, single.system)
        assertEquals(crash, single.dat)
        // Fora da pasta PlayStation também (sem dica de pasta).
        assertEquals(psx, game(identifier.identify(zipTarget("x.7z", listOf("Jogo.chd"), parent = "Downloads"))).system)
        // .zip de SNES continua sendo SNES.
        val snes = identifier.identify(zipTarget("mario.zip", listOf("Super Mario World (USA).sfc"), parent = "Super Nintendo"))
        assertEquals(SystemRegistry.Ids.SNES, game(snes).system)
    }
}
