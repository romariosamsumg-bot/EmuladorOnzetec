package com.retroplay.data.library

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.retroplay.data.scan.CopyFolderWorker
import com.retroplay.data.scan.LibraryScanWorker
import com.retroplay.domain.library.CopyState
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.DefaultFolders
import com.retroplay.domain.library.DeleteResult
import com.retroplay.domain.library.BiosStatus
import com.retroplay.data.scan.FileAccess
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomLibraryRepository(
    private val db: LibraryDatabase,
    private val workManager: WorkManager,
    private val gamesFolder: GamesFolderManager,
    private val files: FileAccess,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryRepository {

    override fun observeGames(system: SystemId): Flow<List<Game>> = db.games().observeBySystem(system.value).map { it.map(::toGame) }
    override fun observeAll(): Flow<List<Game>> = db.games().observeAll().map { it.map(::toGame) }
    override fun observeNeedsSystem(): Flow<List<Game>> = db.games().observeNeedsSystem().map { it.map(::toGame) }
    override fun observeCount(): Flow<Int> = db.games().observeCount()

    override fun observeFolders(): Flow<List<GameFolder>> = db.folders().observe().map { list ->
        list.map { GameFolder(it.id, it.uri, GameSource.valueOf(it.source), it.displayName, it.isDefault, it.lastScanAt, it.gameCount) }
    }

    override fun observeScan(): Flow<ScanState> =
        combine(workManager.getWorkInfosForUniqueWorkFlow(LibraryScanWorker.UNIQUE_NAME), db.folders().observe()) { infos, folders ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            when {
                running != null -> ScanState.Scanning(
                    running.progress.getInt(LibraryScanWorker.KEY_PROCESSED, 0),
                    running.progress.getInt(LibraryScanWorker.KEY_TOTAL, 0),
                    running.progress.getString(LibraryScanWorker.KEY_CURRENT),
                )
                infos.any { it.state == WorkInfo.State.ENQUEUED } -> ScanState.Scanning(0, 0, null)
                infos.lastOrNull()?.state == WorkInfo.State.FAILED -> ScanState.Failed
                else -> ScanState.Idle(folders.mapNotNull { it.lastScanAt }.maxOrNull())
            }
        }.distinctUntilChanged()

    override suspend fun game(id: Long): Game? = db.games().byId(id)?.let(::toGame)
    override fun observeGame(id: Long): Flow<Game?> = db.games().observeById(id).map { it?.let(::toGame) }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = db.games().setFavorite(id, favorite)
    override suspend fun setCover(id: Long, cover: CoverType?) = db.games().setCover(id, cover?.name)
    override suspend fun recordPlayStarted(id: Long, atMillis: Long) = db.games().recordPlayStarted(id, atMillis)
    override suspend fun addPlayTime(id: Long, millis: Long) {
        if (millis > 0) db.games().addPlayTime(id, millis)
    }

    override suspend fun rename(id: Long, title: String?) = db.games().rename(id, title?.trim()?.takeIf { it.isNotEmpty() })
    override fun observeDuplicates(): Flow<List<Game>> = db.games().observeDuplicates().map { it.map(::toGame) }
    override fun observeBios(): Flow<List<BiosStatus>> = db.bios().observe().map { list ->
        list.map { BiosStatus(SystemId(it.system), it.setName, it.uri, it.complete) }
    }
    override suspend fun rebuild() {
        db.games().deleteAll()
        db.bios().deleteAll()
        scanNow()
    }
    override suspend fun totalBytes(): Long = db.games().totalBytes()

    override suspend fun deleteGame(id: Long): DeleteResult = withContext(Dispatchers.IO) {
        val game = db.games().byId(id) ?: return@withContext DeleteResult.NOT_FOUND
        val ok = try {
            files.delete(game.uri)
        } catch (e: SecurityException) {
            return@withContext DeleteResult.NO_PERMISSION
        } catch (e: Exception) {
            false
        }
        if (!ok) return@withContext DeleteResult.NO_PERMISSION
        db.games().delete(listOf(id))
        DeleteResult.DELETED
    }

    override suspend fun addFolder(uri: String, source: GameSource, displayName: String, isDefault: Boolean): GameFolder {
        val existing = db.folders().byUri(uri)
        val entity = existing ?: FolderEntity(uri = uri, source = source.name, displayName = displayName, isDefault = isDefault, addedAt = clock())
        val id = if (existing != null) existing.id else db.folders().upsert(entity)
        return GameFolder(id, uri, source, displayName, isDefault, entity.lastScanAt)
    }

    override suspend fun removeFolder(id: Long) {
        db.games().deleteByFolder(id)
        db.bios().deleteByFolder(id)
        db.folders().delete(id)
    }

    override fun scanNow() {
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>().build()
        // Se já há uma varredura rodando, a nova entra na fila e roda em seguida (pega pastas novas).
        workManager.enqueueUniqueWork(LibraryScanWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun copyFolderToDevice(folderId: Long) {
        val request = OneTimeWorkRequestBuilder<CopyFolderWorker>()
            .setInputData(workDataOf(CopyFolderWorker.KEY_FOLDER to folderId))
            .build()
        workManager.enqueueUniqueWork(CopyFolderWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun observeCopy(): Flow<CopyState> =
        workManager.getWorkInfosForUniqueWorkFlow(CopyFolderWorker.UNIQUE_NAME).map { infos ->
            val info = infos.lastOrNull() ?: return@map CopyState.Idle
            when (info.state) {
                WorkInfo.State.RUNNING -> CopyState.Copying(
                    info.progress.getInt(CopyFolderWorker.KEY_DONE, 0),
                    info.progress.getInt(CopyFolderWorker.KEY_TOTAL, 0),
                    info.progress.getString(CopyFolderWorker.KEY_CURRENT),
                )
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CopyState.Copying(0, 0, null)
                WorkInfo.State.SUCCEEDED -> CopyState.Finished(info.outputData.getInt(CopyFolderWorker.KEY_DONE, 0))
                WorkInfo.State.FAILED -> CopyState.Failed(
                    info.outputData.getString(CopyFolderWorker.KEY_ERROR) == CopyFolderWorker.ERROR_NO_SPACE,
                    info.outputData.getInt(CopyFolderWorker.KEY_DONE, 0),
                )
                WorkInfo.State.CANCELLED -> CopyState.Idle
            }
        }.distinctUntilChanged()

    override suspend fun assignSystem(gameId: Long, system: SystemId) {
        db.games().assignSystem(gameId, system.value)
        scanNow()
    }

    /** Registra as pastas padrão: a pasta privada do app e, com permissão, a pasta Jogos. */
    override suspend fun ensureDefaultFolders(): DefaultFolders = withContext(Dispatchers.IO) {
        addFolder(gamesFolder.appDir.absolutePath, GameSource.FILE, APP_FOLDER_LABEL, isDefault = true)
        val ok = gamesFolder.hasAllFilesAccess() && gamesFolder.ensureCreated()
        if (ok) addFolder(gamesFolder.gamesDir.absolutePath, GameSource.FILE, GamesFolderManager.FOLDER_NAME, isDefault = true)
        DefaultFolders(gamesFolder.gamesDir.absolutePath, ok, gamesFolder.appDir.absolutePath)
    }

    companion object {
        const val APP_FOLDER_LABEL = "app"

        fun toGame(e: GameEntity) = Game(
            id = e.id,
            uri = e.uri,
            source = GameSource.valueOf(e.source),
            folderId = e.folderId,
            fileName = e.fileName,
            sizeBytes = e.sizeBytes,
            systemId = e.system?.let(::SystemId),
            title = e.customTitle ?: e.title,
            datName = e.datName,
            region = e.region,
            year = e.year,
            developer = e.developer,
            genre = e.genre,
            players = e.players,
            thumbs = e.thumbs,
            thumbName = e.thumbName,
            coverOverride = e.coverOverride?.let { runCatching { CoverType.valueOf(it) }.getOrNull() },
            status = IdentStatus.valueOf(e.status),
            romsetStatus = RomsetStatus.valueOf(e.romsetStatus),
            missingFiles = e.missingFiles,
            isFavorite = e.isFavorite,
            playCount = e.playCount,
            lastPlayedAt = e.lastPlayedAt,
            addedAt = e.addedAt,
            playTimeMs = e.playTimeMs,
        )
    }
}
