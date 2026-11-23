package com.retroplay.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomPreparerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val snesExt = setOf("sfc", "smc", "fig", "swc", "zip", "7z")

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val f = File(tmp.root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, d) -> z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() }
        }
        return f
    }

    private fun expect(reason: RomPrepareException.Reason, block: () -> Unit) {
        try {
            block(); fail("esperava $reason")
        } catch (e: RomPrepareException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun `arquivo comum e usado como esta`() {
        val rom = tmp.newFile("jogo.sfc")
        assertEquals(rom, RomPreparer(tmp.newFolder("cache")).prepare(rom, snesExt))
    }

    @Test
    fun `zip extrai a primeira rom compativel`() {
        val rom = zip("jogo.zip", "leiame.txt" to "oi".toByteArray(), "pasta/Jogo (BR).sfc" to byteArrayOf(1, 2, 3))
        val out = RomPreparer(tmp.newFolder("cache")).prepare(rom, snesExt)
        assertEquals("Jogo (BR).sfc", out.name)
        assertTrue(out.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `zip slip nao escapa do cache`() {
        val cache = tmp.newFolder("cache")
        val rom = zip("mau.zip", "../../../etc/x.sfc" to byteArrayOf(9))
        val out = RomPreparer(cache).prepare(rom, snesExt)
        assertTrue(out.canonicalPath.startsWith(cache.canonicalPath))
    }

    @Test
    fun `zip sem rom compativel`() {
        val rom = zip("x.zip", "foto.jpg" to byteArrayOf(1))
        expect(RomPrepareException.Reason.NO_ROM_INSIDE) { RomPreparer(tmp.newFolder("c")).prepare(rom, snesExt) }
    }

    @Test
    fun `zip corrompido`() {
        val rom = File(tmp.root, "ruim.zip").apply { writeBytes(ByteArray(100) { 7 }) }
        expect(RomPrepareException.Reason.CORRUPTED) { RomPreparer(tmp.newFolder("c")).prepare(rom, snesExt) }
    }

    @Test
    fun `arquivo inexistente`() {
        expect(RomPrepareException.Reason.NOT_FOUND) {
            RomPreparer(tmp.newFolder("c")).prepare(File(tmp.root, "nada.sfc"), snesExt)
        }
    }

    @Test
    fun `sem espaco para extrair`() {
        val rom = zip("g.zip", "g.sfc" to ByteArray(4096))
        expect(RomPrepareException.Reason.NO_SPACE) {
            RomPreparer(tmp.newFolder("c"), freeSpace = { 10 }).prepare(rom, snesExt)
        }
    }

    @Test
    fun `romset com o pai na mesma pasta fica onde esta e nao e extraido`() {
        val dir = tmp.newFolder("neo")
        val clone = File(dir, "kof98a.zip").apply { writeBytes(byteArrayOf(1)) }
        val parent = File(dir, "kof98.zip").apply { writeBytes(byteArrayOf(2)) }
        val out = RomPreparer(tmp.newFolder("cache")).prepareArcade(clone.path, clone.name, listOf(RomCompanion(parent.path, parent.name)))
        assertEquals(clone, out)
    }

    @Test
    fun `romset com o pai em outra pasta ganha links lado a lado no cache`() {
        val clone = File(tmp.newFolder("a"), "kof98a.zip").apply { writeBytes(byteArrayOf(1, 2)) }
        val parent = File(tmp.newFolder("b"), "kof98.zip").apply { writeBytes(byteArrayOf(3, 4, 5)) }
        val cache = tmp.newFolder("cache")
        val out = RomPreparer(cache).prepareArcade(clone.path, clone.name, listOf(RomCompanion(parent.path, parent.name)))
        assertEquals(File(cache, "rom-run/kof98a.zip"), out)
        assertTrue(java.nio.file.Files.isSymbolicLink(out.toPath()))
        assertEquals(3, File(cache, "rom-run/kof98.zip").readBytes().size)
        // Nada foi alterado na origem.
        assertEquals(2, clone.readBytes().size)
    }

    @Test
    fun `bios vai para system fbneo e e atualizada se a origem mudar`() {
        val sys = tmp.newFolder("system")
        val bios1 = File(tmp.newFolder("bios1"), "neogeo.zip").apply { writeBytes(ByteArray(10)) }
        val p = RomPreparer(tmp.newFolder("cache"))
        val dest = p.stageBios(bios1.path, sys)
        assertEquals(File(sys, "fbneo/neogeo.zip"), dest)
        assertEquals(10, dest.readBytes().size)
        assertEquals(dest, p.stageBios(bios1.path, sys)) // idempotente
        val bios2 = File(tmp.newFolder("bios2"), "neogeo.zip").apply { writeBytes(ByteArray(20)) }
        assertEquals(20, p.stageBios(bios2.path, sys).readBytes().size)
    }

    @Test
    fun `tamanho descompactado do romset`() {
        val z = zip("mslug.zip", "a.p1" to ByteArray(1000), "b.c1" to ByteArray(3000))
        assertEquals(4000L, RomPreparer(tmp.newFolder("c")).uncompressedBytes(listOf(z)))
    }

    @Test
    fun `romset inexistente`() {
        expect(RomPrepareException.Reason.NOT_FOUND) { RomPreparer(tmp.newFolder("c")).prepareArcade(File(tmp.root, "x.zip").path, "x.zip") }
    }

    // ------------------------------------------------------------ PlayStation em .zip/.7z

    @Test
    fun disco_em_zip_extrai_uma_vez_e_reaproveita() {
        val z = zip("Crash Bandicoot (USA).zip", "Crash Bandicoot (USA).cue" to "FILE \"Crash Bandicoot (USA).bin\" BINARY\n".toByteArray(),
            "Crash Bandicoot (USA).bin" to ByteArray(300_000) { (it % 251).toByte() })
        val cache = tmp.newFolder("cache")
        val prep = RomPreparer(cache)
        val progress = mutableListOf<Float>()
        val cue = prep.prepareDiscArchive(z.absolutePath, z.name, z.length()) { progress += it }
        assertEquals("Crash Bandicoot (USA).cue", cue.name)
        assertTrue(File(cue.parentFile, "Crash Bandicoot (USA).bin").length() == 300_000L)
        assertEquals(1f, progress.last(), 0.001f)
        // Segunda vez: não extrai de novo (o arquivo é o mesmo).
        val bin = File(cue.parentFile, "Crash Bandicoot (USA).bin")
        bin.setLastModified(1_000L)
        val again = prep.prepareDiscArchive(z.absolutePath, z.name, z.length())
        assertEquals(cue, again)
        assertEquals(1_000L, bin.lastModified())
    }

    @Test
    fun varios_discos_no_zip_ganham_m3u_e_so_ficam_os_recentes() {
        val ff7 = zip("FF7.zip",
            "FF7 (Disc 2).chd" to ByteArray(10), "FF7 (Disc 1).chd" to ByteArray(10), "FF7 (Disc 3).chd" to ByteArray(10))
        val cache = tmp.newFolder("c2")
        val prep = RomPreparer(cache)
        val m3u = prep.prepareDiscArchive(ff7.absolutePath, ff7.name, ff7.length())
        assertEquals("discos.m3u", m3u.name)
        assertEquals(listOf("FF7 (Disc 1).chd", "FF7 (Disc 2).chd", "FF7 (Disc 3).chd"), m3u.readLines().filter { it.isNotBlank() })
        // Guarda só os 2 mais recentes: o terceiro jogo apaga o mais antigo.
        val a = zip("A.zip", "A.iso" to ByteArray(10))
        val b = zip("B.zip", "B.pbp" to ByteArray(10))
        prep.prepareDiscArchive(a.absolutePath, a.name, a.length())
        Thread.sleep(20)
        prep.prepareDiscArchive(b.absolutePath, b.name, b.length())
        val kept = File(cache, RomPreparer.DISC_CACHE).listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf("A", "B"), kept)
    }

    @Test
    fun disco_em_7z_e_zip_sem_disco() {
        val f = File(tmp.root, "Jogo.7z")
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(f).use { out ->
            val data = ByteArray(5_000) { 7 }
            val e = out.createArchiveEntry(File(tmp.root, "x"), "Jogo.iso")
            e.size = data.size.toLong()
            out.putArchiveEntry(e); out.write(data); out.closeArchiveEntry()
        }
        val iso = RomPreparer(tmp.newFolder("c3")).prepareDiscArchive(f.absolutePath, f.name, f.length())
        assertEquals("Jogo.iso", iso.name)
        assertEquals(5_000L, iso.length())
        val docs = zip("Leia.zip", "leia-me.txt" to "oi".toByteArray())
        expect(RomPrepareException.Reason.NO_ROM_INSIDE) { RomPreparer(tmp.newFolder("c4")).prepareDiscArchive(docs.absolutePath, docs.name, docs.length()) }
    }
}
