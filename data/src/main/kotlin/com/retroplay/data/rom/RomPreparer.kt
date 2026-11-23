package com.retroplay.data.rom

import com.retroplay.data.scan.FileAccess
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Arquivo que precisa ficar na mesma pasta do jogo (ex.: romset "pai" de um clone de Neo Geo). */
data class RomCompanion(val uri: String, val fileName: String)

/** Erros ao preparar um arquivo de jogo para o núcleo. */
class RomPrepareException(val reason: Reason, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason { NOT_FOUND, CORRUPTED, NO_ROM_INSIDE, NO_SPACE }
}

/**
 * Entrega ao núcleo um caminho de arquivo que ele consiga abrir:
 * - arquivo comum no armazenamento: usado como está;
 * - .zip/.7z de console doméstico: extrai a ROM para um cache temporário (snes9x e
 *   genesis_plus_gx não leem compactados);
 * - romsets de arcade (Neo Geo): NUNCA são extraídos nem alterados; só copiados para o
 *   cache se vierem de um pendrive/SAF (o núcleo precisa de um caminho de arquivo);
 * - arquivos do seletor do sistema (content://) são copiados para o cache.
 */
class RomPreparer(
    private val cacheDir: File,
    private val files: FileAccess? = null,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    fun prepare(rom: File, acceptedExtensions: Set<String>, extractArchives: Boolean = true): File =
        prepare(rom.absolutePath, rom.name, acceptedExtensions, extractArchives)

    fun prepare(uri: String, fileName: String, acceptedExtensions: Set<String>, extractArchives: Boolean = true): File {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val outDir = File(cacheDir, "rom-run").apply { mkdirs() }
        val local: File = if (FileAccess.needsCopy(uri)) {
            // Copia do pendrive/SAF para o cache (o núcleo precisa de um caminho de arquivo).
            outDir.listFiles()?.forEach { it.delete() }
            val dest = File(outDir, safeName(fileName))
            copy(open(uri), dest)
            dest
        } else {
            File(uri).also { if (!it.isFile) throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado: $fileName") }
        }
        if (!extractArchives || ext !in setOf("zip", "7z")) return local
        val romExts = acceptedExtensions - setOf("zip", "7z")
        return try {
            if (ext == "zip") extractZip(local, romExts, outDir) else extract7z(local, romExts, outDir)
        } catch (e: RomPrepareException) {
            throw e
        } catch (e: ZipException) {
            throw RomPrepareException(RomPrepareException.Reason.CORRUPTED, "Arquivo $fileName corrompido", e)
        } catch (e: IOException) {
            throw RomPrepareException(RomPrepareException.Reason.CORRUPTED, "Falha ao ler $fileName", e)
        }
    }

    private fun extractZip(zipFile: File, romExts: Set<String>, outDir: File): File = ZipFile(zipFile).use { zip ->
        val entry = zip.entries().asSequence().filter { !it.isDirectory }
            .filter { it.name.substringAfterLast('.', "").lowercase() in romExts }
            .maxByOrNull { it.size }
            ?: throw RomPrepareException(RomPrepareException.Reason.NO_ROM_INSIDE, "Nenhuma ROM compatível dentro de ${zipFile.name}")
        checkSpace(outDir, entry.size)
        val out = File(outDir, safeName(entry.name))
        clean(outDir, keep = zipFile)
        zip.getInputStream(entry).use { copy(it, out) }
        out
    }

    private fun extract7z(file: File, romExts: Set<String>, outDir: File): File =
        @Suppress("DEPRECATION")
        SevenZFile(file).use { sz ->
            val entry = sz.entries.filter { !it.isDirectory }
                .filter { it.name.substringAfterLast('.', "").lowercase() in romExts }
                .maxByOrNull { it.size }
                ?: throw RomPrepareException(RomPrepareException.Reason.NO_ROM_INSIDE, "Nenhuma ROM compatível dentro de ${file.name}")
            checkSpace(outDir, entry.size)
            val out = File(outDir, safeName(entry.name))
            clean(outDir, keep = file)
            sz.getInputStream(entry).use { copy(it, out) }
            out
        }

    private fun open(uri: String): InputStream = try {
        (files ?: error("FileAccess necessário para content://")).openStream(uri)
    } catch (e: FileNotFoundException) {
        throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado", e)
    } catch (e: SecurityException) {
        throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Sem permissão para ler o arquivo", e)
    }

    private fun copy(input: InputStream, dest: File) {
        try {
            input.use { i -> dest.outputStream().use { i.copyTo(it, 256 * 1024) } }
        } catch (e: IOException) {
            dest.delete()
            if (e.message?.contains("ENOSPC") == true) throw RomPrepareException(RomPrepareException.Reason.NO_SPACE, "Sem espaço", e)
            throw RomPrepareException(RomPrepareException.Reason.CORRUPTED, "Falha ao copiar", e)
        }
    }

    private fun checkSpace(dir: File, size: Long) {
        if (size > 0 && freeSpace(dir) < size * 2) {
            throw RomPrepareException(RomPrepareException.Reason.NO_SPACE, "Sem espaço para extrair")
        }
    }

    /** Só um jogo roda por vez: limpa extrações anteriores (menos o próprio arquivo de origem). */
    private fun clean(dir: File, keep: File) {
        dir.listFiles()?.filter { it.canonicalPath != keep.canonicalPath }?.forEach { it.delete() }
    }

    /**
     * Romset de arcade: o FBNeo procura os romsets "pai" na mesma pasta do jogo. Se o jogo e
     * os companheiros já estão juntos numa pasta comum, nada muda. Senão, monta uma pasta no
     * cache com links simbólicos (arquivos comuns, sem cópia) ou cópias (pendrive/SAF).
     * Nunca extrai nem altera os .zip.
     */
    fun prepareArcade(uri: String, fileName: String, companions: List<RomCompanion> = emptyList()): File {
        val all = listOf(RomCompanion(uri, fileName)) + companions
        if (all.none { FileAccess.needsCopy(it.uri) }) {
            val main = File(uri)
            if (!main.isFile) throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado: $fileName")
            if (companions.all { File(it.uri).parentFile?.canonicalPath == main.parentFile?.canonicalPath }) return main
        }
        val outDir = File(cacheDir, "rom-run").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }
        all.forEach { stage(it.uri, File(outDir, safeName(it.fileName))) }
        return File(outDir, safeName(fileName))
    }

    /**
     * Deixa a BIOS onde o FBNeo procura: <system>/fbneo/neogeo.zip (o romset pode estar em
     * outra pasta, ou ter sido copiado do pendrive).
     */
    fun stageBios(uri: String, systemDir: File, fileName: String = "neogeo.zip"): File {
        val dest = File(systemDir, "fbneo/" + safeName(fileName))
        dest.parentFile?.mkdirs()
        val src = File(uri)
        val upToDate = !FileAccess.needsCopy(uri) && java.nio.file.Files.isSymbolicLink(dest.toPath()) &&
            runCatching { java.nio.file.Files.readSymbolicLink(dest.toPath()).toFile().canonicalPath == src.canonicalPath }.getOrDefault(false)
        if (!upToDate) {
            java.nio.file.Files.deleteIfExists(dest.toPath())
            stage(uri, dest)
        }
        return dest
    }

    /**
     * Disco (.cue/.m3u/.chd/.pbp/.iso): no armazenamento da TV, o núcleo abre direto (e acha as
     * trilhas .bin e os outros discos ao lado). De pendrive/SAF/rede, copia para o cache o
     * arquivo e tudo que ele cita (trilhas do .cue, discos do .m3u e as trilhas deles).
     */
    fun prepareDisc(uri: String, fileName: String): File {
        if (!FileAccess.needsCopy(uri)) {
            return File(uri).also { if (!it.isFile) throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado: $fileName") }
        }
        val access = files ?: error("FileAccess necessário para content://")
        val outDir = File(cacheDir, "rom-run").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }
        val pending = ArrayDeque(listOf(uri to fileName))
        val done = HashSet<String>()
        while (pending.isNotEmpty()) {
            val (u, name) = pending.removeFirst()
            if (!done.add(name.lowercase())) continue
            val dest = File(outDir, safeName(name))
            copy(open(u), dest)
            val refs = when (name.substringAfterLast('.').lowercase()) {
                "m3u" -> com.retroplay.domain.library.DiscImages.m3uEntries(dest.readText())
                "cue" -> com.retroplay.domain.library.DiscImages.cueFiles(dest.readText())
                else -> emptyList()
            }
            refs.forEach { pending += access.sibling(uri, it) to it }
        }
        return File(outDir, safeName(fileName))
    }

    /**
     * BIOS do PlayStation: o PCSX ReARMed procura "scph5501.bin" (e outras) na pasta do sistema.
     * Link simbólico para arquivos comuns; cópia para pendrive/SAF.
     */
    fun stageSystemFile(uri: String, systemDir: File, fileName: String): File {
        val dest = File(systemDir, safeName(fileName))
        val src = File(uri)
        val upToDate = !FileAccess.needsCopy(uri) && java.nio.file.Files.isSymbolicLink(dest.toPath()) &&
            runCatching { java.nio.file.Files.readSymbolicLink(dest.toPath()).toFile().canonicalPath == src.canonicalPath }.getOrDefault(false)
        if (!upToDate) {
            java.nio.file.Files.deleteIfExists(dest.toPath())
            stage(uri, dest)
        }
        return dest
    }

    /** Tamanho descompactado dos romsets (o FBNeo carrega tudo na RAM). */
    fun uncompressedBytes(files: List<File>): Long = files.sumOf { f ->
        runCatching {
            when (f.extension.lowercase()) {
                "zip" -> ZipFile(f).use { z -> z.entries().asSequence().sumOf { maxOf(it.size, 0L) } }
                "7z" -> @Suppress("DEPRECATION") SevenZFile(f).use { z -> z.entries.sumOf { maxOf(it.size, 0L) } }
                else -> f.length()
            }
        }.getOrDefault(f.length())
    }

    /** Arquivo comum: link simbólico (sem gastar espaço); pendrive/SAF ou falha do link: cópia. */
    private fun stage(uri: String, dest: File) {
        if (!FileAccess.needsCopy(uri)) {
            val src = File(uri)
            if (!src.isFile) throw RomPrepareException(RomPrepareException.Reason.NOT_FOUND, "Arquivo não encontrado: ${src.name}")
            val linked = runCatching { java.nio.file.Files.createSymbolicLink(dest.toPath(), src.toPath()) }.isSuccess
            if (!linked) {
                checkSpace(dest.parentFile!!, src.length() / 2)
                copy(src.inputStream(), dest)
            }
        } else {
            copy(open(uri), dest)
        }
    }

    /** Só o nome do arquivo: evita "zip slip" (../../). */
    private fun safeName(name: String) = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "rom" }
}
