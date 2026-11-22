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
}
