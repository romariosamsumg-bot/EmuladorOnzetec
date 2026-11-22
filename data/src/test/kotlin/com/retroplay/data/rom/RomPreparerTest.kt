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
}
