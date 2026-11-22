package com.retroplay.data.scan

import com.retroplay.data.testing.CrcForge
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `le crcs do indice do zip sem descompactar`() {
        val rom = CrcForge.fake("snes", 0xB19ED489L)
        val zip = File(tmp.root, "x.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("pasta/jogo.sfc")); z.write(rom); z.closeEntry()
            z.putNextEntry(ZipEntry("leiame.txt")); z.write("oi".toByteArray()); z.closeEntry()
        }
        val entries = FileInputStream(zip).channel.use { ArchiveReader.entries(it, "zip") }
        assertEquals(2, entries.size)
        assertEquals(0xB19ED489L, entries.first { it.name.endsWith(".sfc") }.crc)
        assertEquals(rom.size.toLong(), entries.first { it.name.endsWith(".sfc") }.size)
    }

    @Test
    fun `le crcs do indice do 7z`() {
        val rom = CrcForge.fake("md", 0x12345678L)
        val f = File(tmp.root, "x.7z")
        SevenZOutputFile(f).use { out ->
            val e = out.createArchiveEntry(File(tmp.root, "dummy").apply { writeBytes(rom) }, "Sonic.md")
            out.putArchiveEntry(e); out.write(rom); out.closeArchiveEntry()
        }
        val entries = FileInputStream(f).channel.use { ArchiveReader.entries(it, "7z") }
        assertEquals("Sonic.md", entries.single().name)
        // Quando o índice não traz o CRC, ele é calculado descompactando só essa entrada.
        val crc = entries.single().crc ?: FileInputStream(f).channel.use { ArchiveReader.sevenZipEntryCrc(it, "Sonic.md") }
        assertEquals(0x12345678L, crc)
    }

    @Test
    fun `crc pula cabecalho de copiadora e md5 confere`() {
        val body = CrcForge.fake("hdr", 0xCAFEBABEL)
        val withHeader = ByteArray(512) { 7 } + body
        assertEquals(0xCAFEBABEL, ArchiveReader.crc32(withHeader.inputStream(), 512))
        val expected = MessageDigest.getInstance("MD5").digest(body)
        assertTrue(expected.contentEquals(ArchiveReader.md5(withHeader.inputStream(), 512)))
    }
}
