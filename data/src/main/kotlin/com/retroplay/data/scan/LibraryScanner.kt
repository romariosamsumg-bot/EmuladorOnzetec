package com.retroplay.data.scan

import androidx.room.withTransaction
import com.retroplay.data.library.BiosEntity
import com.retroplay.data.library.FolderEntity
import com.retroplay.data.library.GameEntity
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.domain.library.ArchiveEntry
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.IdentifyOutcome
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.settings.GameKey
import com.retroplay.domain.library.ScanTarget
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemDetector
import com.retroplay.domain.system.SystemId
import kotlinx.coroutines.yield

data class ScanSummary(val scanned: Int, val added: Int, val updated: Int, val removed: Int, val unreadableFolders: Int)

/**
 * Varre as pastas monitoradas de forma incremental: só (re)identifica arquivos novos ou
 * alterados, grava em lotes e remove da biblioteca o que sumiu da pasta.
 */
class LibraryScanner(
    private val db: LibraryDatabase,
    private val files: FileAccess,
    private val identifier: RomIdentifier,
    catalog: SystemCatalog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val extensions = catalog.all.flatMap { it.extensions }.toSet()

    suspend fun scan(onProgress: suspend (processed: Int, total: Int, current: String?) -> Unit = { _, _, _ -> }): ScanSummary {
        val folders = db.folders().all()
        // 1ª passada: lista tudo (rápido) para ter o total e mostrar progresso real.
        val listing = folders.associateWith { folder ->
            runCatching {
                files.walk(folder.uri, GameSource.valueOf(folder.source))
                    .filter { SystemDetector.extensionOf(it.name) in extensions }
                    .toList()
            }.getOrNull()
        }
        val total = listing.values.sumOf { it?.size ?: 0 }
        var processed = 0
        var added = 0
        var updated = 0
        var removed = 0
        onProgress(0, total, null)
        // Romsets "pai" de clones de arcade podem estar em qualquer pasta da biblioteca: ao abrir
        // o jogo, o app monta os arquivos lado a lado para o núcleo.
        val allNames = listing.values.filterNotNull().flatten().map { it.name.substringBeforeLast('.').lowercase() }.toSet()

        for ((folder, found) in listing) {
            if (found == null) continue // pasta inacessível (pendrive removido?): mantém os jogos
            val existing = db.games().byFolder(folder.id).associateBy { it.uri }
            val existingBios = db.bios().byFolder(folder.id).associateBy { it.uri }
            val batch = ArrayList<GameEntity>(BATCH)
            val toDelete = ArrayList<Long>()

            for (file in found) {
                yield()
                processed++
                val old = existing[file.uri]
                // Clones à espera do pai são reavaliados (o pai pode ter chegado depois).
                if (old != null && old.sizeBytes == file.size && old.lastModified == file.lastModified && old.romsetStatus != RomsetStatus.NEEDS_PARENT.name) {
                    if (processed % 25 == 0) onProgress(processed, total, file.name)
                    continue
                }
                onProgress(processed, total, file.name)
                val target = FileScanTarget(file, files, allNames)
                val outcome = try {
                    identifier.identify(target, old?.userSystem?.let(::SystemId))
                } catch (e: Exception) {
                    null // arquivo ilegível/corrompido
                }
                when (outcome) {
                    is IdentifyOutcome.Bios -> {
                        db.bios().upsert(
                            BiosEntity(existingBios[file.uri]?.id ?: 0, outcome.bios.system.value, outcome.bios.setName, file.uri, folder.id, outcome.bios.complete),
                        )
                        old?.let { toDelete += it.id }
                    }
                    IdentifyOutcome.NotAGame -> old?.let { toDelete += it.id }
                    is IdentifyOutcome.GameResult -> {
                        batch += toEntity(folder, file, outcome, old)
                        if (old == null) added++ else updated++
                    }
                    null -> {
                        batch += corruptedEntity(folder, file, old)
                        if (old == null) added++ else updated++
                    }
                }
                if (batch.size >= BATCH) flush(batch)
            }
            flush(batch)
            val present = found.map { it.uri }.toSet()
            toDelete += existing.values.filter { it.uri !in present }.map { it.id }
            if (toDelete.isNotEmpty()) {
                db.games().delete(toDelete)
                removed += toDelete.size
            }
            existingBios.values.filter { it.uri !in present }.map { it.id }.takeIf { it.isNotEmpty() }?.let { db.bios().delete(it) }
            db.folders().markScanned(folder.id, clock())
        }
        onProgress(total, total, null)
        return ScanSummary(processed, added, updated, removed, listing.values.count { it == null })
    }

    private suspend fun flush(batch: MutableList<GameEntity>) {
        if (batch.isEmpty()) return
        db.withTransaction { db.games().upsert(batch.toList()) }
        batch.clear()
    }

    private fun toEntity(folder: FolderEntity, file: FoundFile, outcome: IdentifyOutcome.GameResult, old: GameEntity?): GameEntity {
        val id = outcome.identification
        val dat = id.dat
        return GameEntity(
            id = old?.id ?: 0,
            uri = file.uri,
            source = folder.source,
            folderId = folder.id,
            fileName = file.name,
            sizeBytes = file.size,
            lastModified = file.lastModified,
            system = id.system?.value,
            userSystem = old?.userSystem,
            candidates = id.candidates.takeIf { it.isNotEmpty() }?.joinToString(",") { it.value },
            title = dat?.title ?: file.name.substringBeforeLast('.'),
            customTitle = old?.customTitle,
            datName = dat?.name,
            region = dat?.region,
            year = dat?.year,
            developer = dat?.developer,
            genre = dat?.genre,
            players = dat?.players,
            thumbs = dat?.thumbs ?: 0,
            thumbName = dat?.thumbName,
            coverOverride = old?.coverOverride,
            crc = id.crc,
            status = id.status.name,
            romsetStatus = id.romsetStatus.name,
            missingFiles = id.missingFiles,
            corrupted = false,
            addedAt = old?.addedAt ?: clock(),
            gameKey = id.system?.let { GameKey.of(it, file.name) },
            coreHint = id.coreHint,
        )
    }

    private fun corruptedEntity(folder: FolderEntity, file: FoundFile, old: GameEntity?): GameEntity {
        val ext = SystemDetector.extensionOf(file.name)
        val system = old?.userSystem ?: old?.system ?: if (ext == "bin") "megadrive" else null
        return GameEntity(
            id = old?.id ?: 0, uri = file.uri, source = folder.source, folderId = folder.id, fileName = file.name,
            sizeBytes = file.size, lastModified = file.lastModified,
            system = system,
            userSystem = old?.userSystem, title = file.name.substringBeforeLast('.'), customTitle = old?.customTitle, datName = null, region = null,
            year = null, developer = null, genre = null, players = null, thumbs = 0, thumbName = null, crc = null,
            status = (if (old?.userSystem != null) IdentStatus.UNIDENTIFIED else IdentStatus.NEEDS_SYSTEM).name,
            romsetStatus = RomsetStatus.NOT_APPLICABLE.name, missingFiles = 0, corrupted = true,
            addedAt = old?.addedAt ?: clock(), gameKey = system?.let { GameKey.of(SystemId(it), file.name) },
        )
    }

    private companion object {
        const val BATCH = 50
    }
}

/** Adapta um [FoundFile] ao [ScanTarget] do domínio (leitura sob demanda). */
class FileScanTarget(
    private val file: FoundFile,
    private val access: FileAccess,
    override val siblingNames: Set<String> = emptySet(),
) : ScanTarget {
    override val fileName get() = file.name
    override val size get() = file.size
    override val parentNames get() = file.parents
    override fun archiveEntries(): List<ArchiveEntry> = access.openChannel(file.uri).use {
        ArchiveReader.entries(it, SystemDetector.extensionOf(file.name).orEmpty())
    }
    override fun crc32(skipBytes: Long) = ArchiveReader.crc32(access.openStream(file.uri), skipBytes)
    override fun md5(skipBytes: Long) = ArchiveReader.md5(access.openStream(file.uri), skipBytes)
    override fun archiveEntryCrc(entryName: String): Long? =
        if (SystemDetector.extensionOf(file.name) == "7z") access.openChannel(file.uri).use { ArchiveReader.sevenZipEntryCrc(it, entryName) }
        else null
}
