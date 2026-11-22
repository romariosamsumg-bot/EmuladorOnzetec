package com.retroplay.data.scan

import com.retroplay.domain.library.ArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Lê índices de .zip/.7z e calcula CRC32/MD5 de arquivos comuns. Nunca grava nada.
 * Usa os construtores com SeekableByteChannel (estáveis entre versões do commons-compress).
 */
@Suppress("DEPRECATION")
object ArchiveReader {

    /** Lê só o diretório central do .zip (ou o cabeçalho do .7z): rápido mesmo para romsets enormes. */
    fun entries(channel: SeekableByteChannel, extension: String): List<ArchiveEntry> = when (extension.lowercase()) {
        "zip" -> ZipFile(channel).use { zip ->
            zip.entries.asSequence().filter { !it.isDirectory }
                .map { ArchiveEntry(it.name, it.size, it.crc.takeIf { c -> c >= 0 }) }.toList()
        }
        "7z" -> SevenZFile(channel).use { sz ->
            sz.entries.filter { !it.isDirectory }
                .map { ArchiveEntry(it.name, it.size, if (it.hasCrc) it.crcValue else null) }
        }
        else -> emptyList()
    }

    /** CRC de uma entrada de .7z sem CRC no índice: descompacta só ela (limite de [maxBytes]). */
    fun sevenZipEntryCrc(channel: SeekableByteChannel, entryName: String, maxBytes: Long = 64L * 1024 * 1024): Long? =
        SevenZFile(channel).use { sz ->
            val entry = sz.entries.firstOrNull { it.name == entryName } ?: return null
            if (entry.size > maxBytes) return null
            crc32(sz.getInputStream(entry), 0)
        }

    fun crc32(input: InputStream, skip: Long): Long {
        val crc = CRC32()
        input.use { stream ->
            skipFully(stream, skip)
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    fun md5(input: InputStream, skip: Long): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        input.use { stream ->
            skipFully(stream, skip)
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest()
    }

    private fun skipFully(stream: InputStream, bytes: Long) {
        var left = bytes
        while (left > 0) {
            val n = stream.skip(left)
            if (n <= 0) { if (stream.read() < 0) return; left-- } else left -= n
        }
    }
}
