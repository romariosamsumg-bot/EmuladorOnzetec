package com.retroplay.data.transfer

import com.retroplay.data.scan.FileAccess
import com.retroplay.data.scan.FileScanTarget
import com.retroplay.data.scan.FoundFile
import com.retroplay.domain.library.IdentifyOutcome
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import java.io.File

/** Onde um arquivo recebido foi parar. */
sealed interface Placement {
    data class Game(val file: File, val system: SystemId?) : Placement
    data class Bios(val file: File) : Placement
    data object NotAGame : Placement
}

/**
 * Decide a pasta de cada arquivo recebido: identifica o console (mesmo identificador da
 * biblioteca) e move para <raiz>/<console>/ — "Jogos/Super Nintendo", "Jogos/BIOS"...
 * Sem console definido, o arquivo fica na raiz e a TV pergunta depois.
 *
 * @param root pasta raiz (Jogos, ou a pasta do app sem a permissão de arquivos)
 * @param folderFor nome da subpasta de cada console
 */
class TransferDestination(
    private val root: () -> File,
    private val folderFor: (SystemId) -> String,
    private val biosFolder: String,
    private val identifier: RomIdentifier,
    private val files: FileAccess,
    private val catalog: SystemCatalog,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {
    /** Arquivo temporário na mesma partição do destino (mover depois é instantâneo). */
    fun tempFile(name: String): File {
        val dir = File(root(), TEMP_DIR).apply { mkdirs() }
        return File(dir, "${System.nanoTime()}-$name.part")
    }

    fun freeBytes(): Long = freeSpace(root().apply { mkdirs() })

    fun accept(temp: File, name: String, requested: SystemId?): Placement {
        val target = FileScanTarget(FoundFile(temp.absolutePath, name, temp.length(), temp.lastModified(), emptyList()), files)
        val outcome = runCatching { identifier.identify(target, requested?.takeIf { catalog.byId(it) != null }) }.getOrNull()
        val placement: Pair<File, Placement> = when (outcome) {
            is IdentifyOutcome.Bios -> File(root(), biosFolder).let { it to Placement.Bios(it) }
            IdentifyOutcome.NotAGame -> {
                temp.delete()
                return Placement.NotAGame
            }
            is IdentifyOutcome.GameResult -> {
                val system = outcome.identification.system ?: requested
                val dir = system?.let { File(root(), folderFor(it)) } ?: root()
                dir to Placement.Game(dir, system)
            }
            // Ilegível (ex.: zip corrompido): vai para o console escolhido no celular; sem escolha,
            // fica na raiz e a TV pergunta o console depois.
            null -> {
                val dir = requested?.let { File(root(), folderFor(it)) } ?: root()
                dir to Placement.Game(dir, requested)
            }
        }
        val dir = placement.first.apply { mkdirs() }
        val dest = uniqueName(dir, name)
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = false)
            temp.delete()
        }
        return when (val p = placement.second) {
            is Placement.Bios -> Placement.Bios(dest)
            is Placement.Game -> Placement.Game(dest, p.system)
            Placement.NotAGame -> p
        }
    }

    /** "jogo.sfc" → "jogo (2).sfc" se já existir. */
    private fun uniqueName(dir: File, name: String): File {
        var f = File(dir, name)
        var n = 2
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return f
    }

    companion object {
        const val TEMP_DIR = ".recebendo"
    }
}
