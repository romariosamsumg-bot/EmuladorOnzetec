package com.retroplay.data.core

import com.retroplay.domain.core.InstalledCore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Onde os núcleos ficam no disco:
 * `<filesDir>/cores/<abi>/<core>_libretro_android.so` + `<core>.json` (versão, hash, tamanho).
 */
class CoreStorage(private val root: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun abiDir(abi: String): File = File(root, abi)
    fun libraryFile(coreId: String, abi: String): File = File(abiDir(abi), "${coreId}_libretro_android.so")
    private fun metadataFile(coreId: String, abi: String): File = File(abiDir(abi), "$coreId.json")
    fun tempDir(): File = File(root, ".tmp").apply { mkdirs() }

    fun read(coreId: String, abi: String): InstalledCore? {
        val lib = libraryFile(coreId, abi)
        val meta = metadataFile(coreId, abi)
        if (!lib.isFile || !meta.isFile) return null
        val m = runCatching { json.decodeFromString(Metadata.serializer(), meta.readText()) }.getOrNull() ?: return null
        // Arquivo apagado/alterado por fora → tratar como não instalado.
        if (lib.length() != m.sizeBytes) return null
        return InstalledCore(m.coreId, m.abi, lib.absolutePath, m.version, m.sha256, m.sizeBytes, m.installedAtMillis)
    }

    fun write(core: InstalledCore) {
        val meta = Metadata(core.coreId, core.abi, core.version, core.sha256, core.sizeBytes, core.installedAtMillis)
        val target = metadataFile(core.coreId, core.abi)
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(Metadata.serializer(), meta))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun previous(f: File) = File(f.parentFile, f.name + ".prev")

    /** Guarda a versão atual como "anterior" antes de instalar uma nova (para poder voltar). */
    fun backupCurrent(coreId: String, abi: String) {
        val lib = libraryFile(coreId, abi)
        val meta = metadataFile(coreId, abi)
        if (!lib.isFile || !meta.isFile) return
        previous(lib).delete(); previous(meta).delete()
        lib.renameTo(previous(lib)); meta.renameTo(previous(meta))
    }

    fun hasPrevious(coreId: String, abi: String) =
        previous(libraryFile(coreId, abi)).isFile && previous(metadataFile(coreId, abi)).isFile

    /** Troca a versão atual pela anterior. */
    fun rollback(coreId: String, abi: String): Boolean {
        if (!hasPrevious(coreId, abi)) return false
        val lib = libraryFile(coreId, abi)
        val meta = metadataFile(coreId, abi)
        lib.delete(); meta.delete()
        return previous(lib).renameTo(lib) && previous(meta).renameTo(meta)
    }

    fun delete(coreId: String, abi: String) {
        libraryFile(coreId, abi).delete()
        metadataFile(coreId, abi).delete()
    }

    @Serializable
    private data class Metadata(
        val coreId: String,
        val abi: String,
        val version: String,
        val sha256: String,
        val sizeBytes: Long,
        val installedAtMillis: Long,
    )
}
