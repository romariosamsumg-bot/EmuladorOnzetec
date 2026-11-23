package com.retroplay.data.backup

import androidx.room.withTransaction
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.data.library.PlayStatsEntity
import com.retroplay.data.library.ProfileEntity
import com.retroplay.domain.settings.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** O que um backup contém (para a mensagem de sucesso). */
data class BackupSummary(val files: Int, val settings: Int, val profiles: Int)

class BackupException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_FORMAT, CORRUPTED }
}

/**
 * Backup completo num .zip (para pendrive): saves de bateria, save states (com miniaturas),
 * opções dos núcleos, configurações, perfis com favoritos e histórico, e os nomes/capas
 * escolhidos. Jogos, núcleos e capas baixadas não entram (o usuário já tem / são baixados de novo).
 */
class BackupManager(
    private val filesDir: File,
    private val db: LibraryDatabase,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class Manifest(val format: Int, val createdAt: Long, val app: String = "RetroPlay")

    @Serializable
    data class SettingsFile(val format: Int = FORMAT, val settings: Map<String, String>)

    @Serializable
    data class ProfileJson(val id: Long, val name: String, val color: Int, val createdAt: Long)

    @Serializable
    data class StatsJson(val profileId: Long, val gameKey: String, val favorite: Boolean, val playCount: Int, val lastPlayedAt: Long?, val playTimeMs: Long)

    @Serializable
    data class CustomJson(val gameKey: String, val customTitle: String?, val coverOverride: String?)

    @Serializable
    data class LibraryJson(val profiles: List<ProfileJson>, val stats: List<StatsJson>, val customizations: List<CustomJson>)

    suspend fun export(out: OutputStream): BackupSummary {
        val raw = settings.current().raw
        val library = LibraryJson(
            profiles = db.profiles().all().map { ProfileJson(it.id, it.name, it.color, it.createdAt) },
            stats = db.stats().all().map { StatsJson(it.profileId, it.gameKey, it.favorite, it.playCount, it.lastPlayedAt, it.playTimeMs) },
            customizations = db.games().customizations().map { CustomJson(it.gameKey, it.customTitle, it.coverOverride) },
        )
        var files = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.text(MANIFEST, json.encodeToString(Manifest.serializer(), Manifest(FORMAT, clock())))
            zip.text(SETTINGS, json.encodeToString(SettingsFile.serializer(), SettingsFile(settings = raw)))
            zip.text(LIBRARY, json.encodeToString(LibraryJson.serializer(), library))
            DIRS.forEach { dir ->
                val root = File(filesDir, dir)
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    zip.putNextEntry(ZipEntry("files/" + f.relativeTo(filesDir).invariantSeparatorsPath).apply { time = f.lastModified() })
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        }
        return BackupSummary(files, raw.size, library.profiles.size)
    }

    /**
     * Restaura por cima do que existe (saves com o mesmo nome são substituídos; os demais ficam).
     * Só aceita arquivos dentro de states/, saves/ e coreoptions/ (nada de "../").
     */
    suspend fun restore(input: InputStream): BackupSummary {
        var manifest: Manifest? = null
        var settingsFile: SettingsFile? = null
        var library: LibraryJson? = null
        var files = 0
        val staging = File(filesDir, "restore-tmp").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    when {
                        entry.isDirectory -> Unit
                        entry.name == MANIFEST -> manifest = json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString())
                        entry.name == SETTINGS -> settingsFile = json.decodeFromString(SettingsFile.serializer(), zip.readBytes().decodeToString())
                        entry.name == LIBRARY -> library = json.decodeFromString(LibraryJson.serializer(), zip.readBytes().decodeToString())
                        entry.name.startsWith("files/") -> {
                            val rel = entry.name.removePrefix("files/")
                            val safe = rel.split('/').none { it == ".." || it.isEmpty() } && DIRS.any { rel.startsWith("$it/") }
                            if (safe) {
                                val dest = File(staging, rel)
                                dest.parentFile?.mkdirs()
                                dest.outputStream().use { zip.copyTo(it) }
                                if (entry.time > 0) dest.setLastModified(entry.time)
                                files++
                            }
                        }
                    }
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPTED, "Backup ilegível", e)
        }
        val m = manifest ?: run { staging.deleteRecursively(); throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Não é um backup do Onzetec Games") }
        if (m.format > FORMAT) { staging.deleteRecursively(); throw BackupException(BackupException.Reason.NEWER_FORMAT, "Backup de uma versão mais nova") }

        // Arquivos: copia do staging para o lugar definitivo só depois de ler o zip inteiro.
        staging.walkTopDown().filter { it.isFile }.forEach { f ->
            val dest = File(filesDir, f.relativeTo(staging).path)
            dest.parentFile?.mkdirs()
            f.copyTo(dest, overwrite = true)
            dest.setLastModified(f.lastModified())
        }
        staging.deleteRecursively()

        settingsFile?.let { settings.importRaw(it.settings, replace = true) }
        library?.let { lib ->
            db.withTransaction {
                db.profiles().upsert(lib.profiles.map { ProfileEntity(it.id, it.name, it.color, it.createdAt) })
                db.stats().upsert(lib.stats.map { PlayStatsEntity(it.profileId, it.gameKey, it.favorite, it.playCount, it.lastPlayedAt, it.playTimeMs) })
                lib.customizations.forEach { db.games().applyCustomization(it.gameKey, it.customTitle, it.coverOverride) }
            }
        }
        return BackupSummary(files, settingsFile?.settings?.size ?: 0, library?.profiles?.size ?: 0)
    }

    /** Só as configurações, num arquivo .json legível. */
    suspend fun exportSettings(out: OutputStream): Int {
        val raw = settings.current().raw
        out.bufferedWriter().use { it.write(json.encodeToString(SettingsFile.serializer(), SettingsFile(settings = raw))) }
        return raw.size
    }

    suspend fun importSettings(input: InputStream): Int {
        val file = try {
            json.decodeFromString(SettingsFile.serializer(), input.bufferedReader().readText())
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, "Arquivo de configurações inválido", e)
        }
        if (file.format > FORMAT) throw BackupException(BackupException.Reason.NEWER_FORMAT, "Arquivo de uma versão mais nova")
        settings.importRaw(file.settings, replace = true)
        return file.settings.size
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    companion object {
        const val FORMAT = 1
        const val MANIFEST = "retroplay-backup.json"
        const val SETTINGS = "settings.json"
        const val LIBRARY = "library.json"
        val DIRS = listOf("states", "saves", "coreoptions")
    }
}
