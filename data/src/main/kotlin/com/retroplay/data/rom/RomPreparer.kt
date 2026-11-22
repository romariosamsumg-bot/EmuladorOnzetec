package com.retroplay.data.rom

import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Erros ao preparar um arquivo de jogo para o núcleo. */
class RomPrepareException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason { NOT_FOUND, CORRUPTED, NO_ROM_INSIDE, NO_SPACE }
}

/**
 * Entrega ao núcleo um caminho que ele consiga abrir.
 * - Arquivo comum: usado como está.
 * - .zip de console doméstico: extrai a ROM para um cache temporário (snes9x/genesis_plus_gx
 *   não leem zip). Romsets de arcade (Neo Geo) NUNCA passam por aqui: o núcleo lê o zip.
 */
class RomPreparer(
    private val cacheDir: File,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    fun prepare(rom: File, acceptedExtensions: Set<String>): File {
        if (!rom.isFile) throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado: ${rom.name}")
        if (rom.extension.lowercase() != "zip") return rom
        try {
            ZipFile(rom).use { zip ->
                val entry = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .firstOrNull { it.name.substringAfterLast('.', "").lowercase() in acceptedExtensions - setOf("zip", "7z") }
                    ?: throw RomPrepareException(RomPrepareException.Reason.NO_ROM_INSIDE, "Nenhuma ROM compatível dentro de ${rom.name}")
                // Só o nome do arquivo: evita "zip slip" (../../).
                val safeName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                val outDir = File(cacheDir, "rom-run").apply { mkdirs() }
                if (entry.size > 0 && freeSpace(outDir) < entry.size * 2) {
                    throw RomPrepareException(RomPrepareException.Reason.NO_SPACE, "Sem espaço para extrair ${rom.name}")
                }
                // Limpa extrações anteriores: só um jogo roda por vez.
                outDir.listFiles()?.forEach { it.delete() }
                val out = File(outDir, safeName)
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                return out
            }
        } catch (e: ZipException) {
            throw RomPrepareException(RomPrepareException.Reason.CORRUPTED, "Arquivo ${rom.name} corrompido", e)
        } catch (e: IOException) {
            throw RomPrepareException(RomPrepareException.Reason.CORRUPTED, "Falha ao ler ${rom.name}", e)
        }
    }
}
