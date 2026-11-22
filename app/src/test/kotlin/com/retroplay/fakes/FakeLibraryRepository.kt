package com.retroplay.fakes

import com.retroplay.data.di.LibraryRepositoryModule
import com.retroplay.domain.library.CopyState
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.DeleteResult
import com.retroplay.domain.library.DefaultFolders
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemId
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/** Biblioteca controlada pelo teste (sem Room nem WorkManager). */
class FakeLibraryRepository : LibraryRepository {
    val games = MutableStateFlow<List<Game>>(emptyList())
    val folders = MutableStateFlow<List<GameFolder>>(emptyList())
    val scan = MutableStateFlow<ScanState>(ScanState.Idle(null))
    val copy = MutableStateFlow<CopyState>(CopyState.Idle)
    var gamesFolderReady = false
    var scanRequests = 0
    val assigned = mutableListOf<Pair<Long, SystemId>>()
    val added = mutableListOf<String>()
    val copied = mutableListOf<Long>()

    override suspend fun ensureDefaultFolders() =
        DefaultFolders("/storage/emulated/0/Jogos", gamesFolderReady, "/storage/emulated/0/Android/data/com.retroplay.tv/files/roms")

    override fun observeGames(system: SystemId): Flow<List<Game>> = games.map { l -> l.filter { it.systemId == system } }
    override fun observeAll(): Flow<List<Game>> = games.map { l -> l.filter { it.systemId != null } }
    override fun observeNeedsSystem(): Flow<List<Game>> = games.map { l -> l.filter { it.status == IdentStatus.NEEDS_SYSTEM } }
    override fun observeFolders(): Flow<List<GameFolder>> = folders
    override fun observeScan(): Flow<ScanState> = scan
    override fun observeCount(): Flow<Int> = games.map { l -> l.count { it.systemId != null } }
    override fun observeCopy(): Flow<CopyState> = copy
    override suspend fun game(id: Long) = games.value.firstOrNull { it.id == id }
    override fun observeGame(id: Long): Flow<Game?> = games.map { l -> l.firstOrNull { it.id == id } }
    val deleted = mutableListOf<Long>()
    var deleteResult = DeleteResult.DELETED

    private fun update(id: Long, f: (Game) -> Game) {
        games.value = games.value.map { if (it.id == id) f(it) else it }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = update(id) { it.copy(isFavorite = favorite) }
    override suspend fun setCover(id: Long, cover: CoverType?) = update(id) { it.copy(coverOverride = cover) }
    override suspend fun recordPlayStarted(id: Long, atMillis: Long) = update(id) { it.copy(playCount = it.playCount + 1, lastPlayedAt = atMillis) }
    override suspend fun addPlayTime(id: Long, millis: Long) = update(id) { it.copy(playTimeMs = it.playTimeMs + millis) }
    val bios = MutableStateFlow<List<com.retroplay.domain.library.BiosStatus>>(emptyList())
    var rebuilt = 0
    override suspend fun rename(id: Long, title: String?) = update(id) { it.copy(title = title ?: it.title) }
    override fun observeDuplicates(): Flow<List<Game>> = games.map { l -> l.groupBy { it.datName }.filter { it.key != null && it.value.size > 1 }.values.flatten() }
    override fun observeBios(): Flow<List<com.retroplay.domain.library.BiosStatus>> = bios
    override suspend fun rebuild() { rebuilt++ }
    override suspend fun totalBytes(): Long = games.value.sumOf { it.sizeBytes }

    override suspend fun deleteGame(id: Long): DeleteResult {
        deleted += id
        if (deleteResult == DeleteResult.DELETED) games.value = games.value.filter { it.id != id }
        return deleteResult
    }

    override suspend fun addFolder(uri: String, source: GameSource, displayName: String, isDefault: Boolean): GameFolder {
        added += uri
        val f = GameFolder(folders.value.size + 10L, uri, source, displayName, isDefault, null)
        folders.value = folders.value + f
        return f
    }

    override suspend fun removeFolder(id: Long) {
        folders.value = folders.value.filter { it.id != id }
    }

    override fun scanNow() {
        scanRequests++
    }

    override fun copyFolderToDevice(folderId: Long) {
        copied += folderId
    }

    override suspend fun assignSystem(gameId: Long, system: SystemId) {
        assigned += gameId to system
        games.value = games.value.map { if (it.id == gameId) it.copy(systemId = system, status = IdentStatus.UNIDENTIFIED) else it }
    }

    companion object {
        fun game(
            id: Long,
            system: SystemId?,
            title: String,
            fileName: String = "$title.sfc",
            status: IdentStatus = IdentStatus.IDENTIFIED,
            romsetStatus: RomsetStatus = RomsetStatus.NOT_APPLICABLE,
            missing: Int = 0,
            thumbs: Int = 0,
            genre: String = "Platform",
            favorite: Boolean = false,
            playCount: Int = 0,
            lastPlayedAt: Long? = null,
        ) = Game(
            id = id, uri = "/storage/emulated/0/Jogos/$fileName", source = GameSource.FILE, folderId = 1, fileName = fileName,
            sizeBytes = 524288, systemId = system, title = title, datName = if (status == IdentStatus.IDENTIFIED) "$title (USA)" else null,
            region = "USA", year = "1991", developer = "Dev", genre = genre, players = 2, thumbs = thumbs, thumbName = null,
            coverOverride = null, status = status, romsetStatus = romsetStatus, missingFiles = missing,
            playCount = playCount, lastPlayedAt = lastPlayedAt, addedAt = id, playTimeMs = 0,
            isFavorite = favorite,
        )
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LibraryRepositoryModule::class])
object FakeLibraryModule {
    @Provides
    @Singleton
    fun fake(): FakeLibraryRepository = FakeLibraryRepository()

    @Provides
    @Singleton
    fun repo(fake: FakeLibraryRepository): LibraryRepository = fake
}
