package com.retroplay.data.library

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folder", indices = [Index(value = ["uri"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val source: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long? = null,
)

data class FolderWithCount(
    val id: Long,
    val uri: String,
    val source: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long?,
    @ColumnInfo(name = "game_count") val gameCount: Int,
)

@Entity(
    tableName = "game",
    indices = [Index(value = ["uri"], unique = true), Index("system"), Index("folder_id"), Index("status")],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val source: String,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    val system: String?,
    /** Console escolhido pelo usuário (lembrado nas próximas varreduras). */
    @ColumnInfo(name = "user_system") val userSystem: String? = null,
    /** Consoles possíveis quando ambíguo (separados por vírgula). */
    val candidates: String? = null,
    val title: String,
    @ColumnInfo(name = "dat_name") val datName: String?,
    val region: String?,
    val year: String?,
    val developer: String?,
    val genre: String?,
    val players: Int?,
    val thumbs: Int,
    @ColumnInfo(name = "thumb_name") val thumbName: String?,
    @ColumnInfo(name = "cover_override") val coverOverride: String? = null,
    val crc: Long?,
    val status: String,
    @ColumnInfo(name = "romset_status") val romsetStatus: String,
    @ColumnInfo(name = "missing_files") val missingFiles: Int,
    val corrupted: Boolean = false,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "play_time_ms") val playTimeMs: Long = 0,
)

/** BIOS encontradas na varredura (ex.: neogeo.zip). Usado na Fase 8. */
@Entity(tableName = "bios", indices = [Index(value = ["uri"], unique = true)])
data class BiosEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val system: String,
    @ColumnInfo(name = "set_name") val setName: String,
    val uri: String,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    val complete: Boolean,
)

@Dao
interface FolderDao {
    @Query(
        "SELECT f.id, f.uri, f.source, f.display_name, f.is_default, f.last_scan_at, " +
            "(SELECT COUNT(*) FROM game g WHERE g.folder_id = f.id) AS game_count FROM folder f ORDER BY f.is_default DESC, f.added_at",
    )
    fun observe(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folder ORDER BY is_default DESC, added_at")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folder WHERE uri = :uri")
    suspend fun byUri(uri: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity): Long

    @Query("DELETE FROM folder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE folder SET last_scan_at = :time WHERE id = :id")
    suspend fun markScanned(id: Long, time: Long)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM game WHERE system = :system ORDER BY title COLLATE NOCASE")
    fun observeBySystem(system: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE system IS NOT NULL ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM game WHERE status = 'NEEDS_SYSTEM' ORDER BY file_name COLLATE NOCASE")
    fun observeNeedsSystem(): Flow<List<GameEntity>>

    @Query("SELECT COUNT(*) FROM game WHERE system IS NOT NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM game WHERE id = :id")
    suspend fun byId(id: Long): GameEntity?

    @Query("SELECT * FROM game WHERE id = :id")
    fun observeById(id: Long): Flow<GameEntity?>

    @Query("UPDATE game SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE game SET cover_override = :cover WHERE id = :id")
    suspend fun setCover(id: Long, cover: String?)

    @Query("UPDATE game SET play_count = play_count + 1, last_played_at = :at WHERE id = :id")
    suspend fun recordPlayStarted(id: Long, at: Long)

    @Query("UPDATE game SET play_time_ms = play_time_ms + :ms WHERE id = :id")
    suspend fun addPlayTime(id: Long, ms: Long)

    @Query("SELECT * FROM game WHERE folder_id = :folderId")
    suspend fun byFolder(folderId: Long): List<GameEntity>

    @Upsert
    suspend fun upsert(games: List<GameEntity>)

    @Query("DELETE FROM game WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM game WHERE folder_id = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    /** Escolha do usuário; zera last_modified para o arquivo ser reidentificado na próxima varredura. */
    @Query("UPDATE game SET system = :system, user_system = :system, status = 'UNIDENTIFIED', last_modified = -1 WHERE id = :id")
    suspend fun assignSystem(id: Long, system: String)
}

@Dao
interface BiosDao {
    @Upsert
    suspend fun upsert(bios: BiosEntity)

    @Query("SELECT * FROM bios")
    fun observe(): Flow<List<BiosEntity>>

    @Query("SELECT * FROM bios WHERE folder_id = :folderId")
    suspend fun byFolder(folderId: Long): List<BiosEntity>

    @Query("DELETE FROM bios WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM bios WHERE folder_id = :folderId")
    suspend fun deleteByFolder(folderId: Long)
}

@Database(entities = [FolderEntity::class, GameEntity::class, BiosEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun games(): GameDao
    abstract fun bios(): BiosDao
}
