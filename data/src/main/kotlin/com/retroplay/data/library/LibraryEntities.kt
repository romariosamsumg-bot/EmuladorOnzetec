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
    indices = [Index(value = ["uri"], unique = true), Index("system"), Index("folder_id"), Index("status"), Index("game_key")],
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
    /** Nome dado pelo usuário (sobrevive às varreduras). */
    @ColumnInfo(name = "custom_title") val customTitle: String? = null,
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
    @ColumnInfo(name = "added_at") val addedAt: Long,
    /** Chave estável (console + nome do arquivo) que liga o jogo ao histórico de cada perfil. */
    @ColumnInfo(name = "game_key") val gameKey: String? = null,
    /** Arcade: núcleo cujo DAT o romset satisfaz (o jogo abre nele, salvo escolha do usuário). */
    @ColumnInfo(name = "core_hint") val coreHint: String? = null,
)

/** Nome e capa escolhidos pelo usuário, pela chave estável do jogo. */
data class GameCustomization(
    @ColumnInfo(name = "game_key") val gameKey: String,
    @ColumnInfo(name = "custom_title") val customTitle: String?,
    @ColumnInfo(name = "cover_override") val coverOverride: String?,
)

/** Perfil de jogador. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Avatar escolhido; null = perfil ainda não configurado. */
    val avatar: String? = null,
    /** Cor do tema do perfil. */
    @ColumnInfo(defaultValue = "LARANJA") val accent: String = "LARANJA",
)

/**
 * Favoritos e histórico por perfil, ligados ao jogo pela chave estável: sobrevivem a uma nova
 * varredura e a "reconstruir a biblioteca".
 */
@Entity(tableName = "play_stats", primaryKeys = ["profile_id", "game_key"])
data class PlayStatsEntity(
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "game_key") val gameKey: String,
    val favorite: Boolean = false,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "play_time_ms") val playTimeMs: Long = 0,
)

/** Jogo + histórico do perfil ativo. */
data class GameWithStats(
    @androidx.room.Embedded val game: GameEntity,
    @ColumnInfo(name = "s_favorite") val favorite: Boolean?,
    @ColumnInfo(name = "s_play_count") val playCount: Int?,
    @ColumnInfo(name = "s_last_played_at") val lastPlayedAt: Long?,
    @ColumnInfo(name = "s_play_time_ms") val playTimeMs: Long?,
)

/** SELECT do jogo com o histórico do perfil :profile. */
private const val WITH_STATS = "SELECT g.*, s.favorite AS s_favorite, s.play_count AS s_play_count, " +
    "s.last_played_at AS s_last_played_at, s.play_time_ms AS s_play_time_ms FROM game g " +
    "LEFT JOIN play_stats s ON s.game_key = g.game_key AND s.profile_id = :profile"

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
    @Query("$WITH_STATS WHERE g.system = :system ORDER BY g.title COLLATE NOCASE")
    fun observeBySystem(system: String, profile: Long): Flow<List<GameWithStats>>

    @Query("$WITH_STATS WHERE g.system IS NOT NULL ORDER BY g.title COLLATE NOCASE")
    fun observeAll(profile: Long): Flow<List<GameWithStats>>

    @Query("$WITH_STATS WHERE g.status = 'NEEDS_SYSTEM' ORDER BY g.file_name COLLATE NOCASE")
    fun observeNeedsSystem(profile: Long): Flow<List<GameWithStats>>

    @Query("SELECT COUNT(*) FROM game WHERE system IS NOT NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM game WHERE id = :id")
    suspend fun byId(id: Long): GameEntity?

    @Query("$WITH_STATS WHERE g.id = :id")
    suspend fun byIdWithStats(id: Long, profile: Long): GameWithStats?

    @Query("$WITH_STATS WHERE g.id = :id")
    fun observeById(id: Long, profile: Long): Flow<GameWithStats?>

    @Query("UPDATE game SET custom_title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String?)

    /** Mesmo jogo em mais de um arquivo (mesmo CRC ou mesmo nome no DAT). */
    @Query(
        "$WITH_STATS WHERE g.system IS NOT NULL AND (" +
            "(g.crc IS NOT NULL AND g.crc IN (SELECT crc FROM game WHERE crc IS NOT NULL GROUP BY system, crc HAVING COUNT(*) > 1)) OR " +
            "(g.dat_name IS NOT NULL AND g.dat_name IN (SELECT dat_name FROM game WHERE dat_name IS NOT NULL GROUP BY system, dat_name HAVING COUNT(*) > 1))" +
            ") ORDER BY g.title COLLATE NOCASE, g.file_name",
    )
    fun observeDuplicates(profile: Long): Flow<List<GameWithStats>>

    @Query("DELETE FROM game")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM game")
    suspend fun totalBytes(): Long

    @Query("UPDATE game SET cover_override = :cover WHERE id = :id")
    suspend fun setCover(id: Long, cover: String?)

    @Query("SELECT * FROM game WHERE folder_id = :folderId")
    suspend fun byFolder(folderId: Long): List<GameEntity>

    @Upsert
    suspend fun upsert(games: List<GameEntity>)

    @Query("DELETE FROM game WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM game WHERE folder_id = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    /** Personalizações do usuário (para o backup). */
    @Query("SELECT game_key, custom_title, cover_override FROM game WHERE game_key IS NOT NULL AND (custom_title IS NOT NULL OR cover_override IS NOT NULL)")
    suspend fun customizations(): List<GameCustomization>

    @Query("UPDATE game SET custom_title = :title, cover_override = :cover WHERE game_key = :key")
    suspend fun applyCustomization(key: String, title: String?, cover: String?)

    /** Escolha do usuário; zera last_modified para o arquivo ser reidentificado na próxima varredura. */
    @Query("UPDATE game SET system = :system, user_system = :system, game_key = :key, status = 'UNIDENTIFIED', last_modified = -1 WHERE id = :id")
    suspend fun assignSystem(id: Long, system: String, key: String)
}

@Dao
interface StatsDao {
    @Query("INSERT OR IGNORE INTO play_stats(profile_id, game_key, favorite, play_count, play_time_ms) SELECT :profile, game_key, 0, 0, 0 FROM game WHERE id = :id AND game_key IS NOT NULL")
    suspend fun ensure(profile: Long, id: Long)

    @Query("UPDATE play_stats SET favorite = :favorite WHERE profile_id = :profile AND game_key = (SELECT game_key FROM game WHERE id = :id)")
    suspend fun updateFavorite(profile: Long, id: Long, favorite: Boolean)

    @Query("UPDATE play_stats SET play_count = play_count + 1, last_played_at = :at WHERE profile_id = :profile AND game_key = (SELECT game_key FROM game WHERE id = :id)")
    suspend fun updatePlayStarted(profile: Long, id: Long, at: Long)

    @Query("UPDATE play_stats SET play_time_ms = play_time_ms + :ms WHERE profile_id = :profile AND game_key = (SELECT game_key FROM game WHERE id = :id)")
    suspend fun updatePlayTime(profile: Long, id: Long, ms: Long)

    @androidx.room.Transaction
    suspend fun setFavorite(profile: Long, id: Long, favorite: Boolean) {
        ensure(profile, id)
        updateFavorite(profile, id, favorite)
    }

    @androidx.room.Transaction
    suspend fun recordPlayStarted(profile: Long, id: Long, at: Long) {
        ensure(profile, id)
        updatePlayStarted(profile, id, at)
    }

    @androidx.room.Transaction
    suspend fun addPlayTime(profile: Long, id: Long, ms: Long) {
        ensure(profile, id)
        updatePlayTime(profile, id, ms)
    }

    @Query("SELECT * FROM play_stats")
    suspend fun all(): List<PlayStatsEntity>

    @Upsert
    suspend fun upsert(stats: List<PlayStatsEntity>)

    @Query("DELETE FROM play_stats WHERE profile_id = :profile")
    suspend fun deleteProfile(profile: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile ORDER BY id")
    fun observe(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile ORDER BY id")
    suspend fun all(): List<ProfileEntity>

    @androidx.room.Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Upsert
    suspend fun upsert(profiles: List<ProfileEntity>)

    @Query("UPDATE profile SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE profile SET name = :name, avatar = :avatar, accent = :accent WHERE id = :id")
    suspend fun update(id: Long, name: String, avatar: String, accent: String)

    @Query("DELETE FROM profile WHERE id = :id")
    suspend fun delete(id: Long)
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

    @Query("DELETE FROM bios")
    suspend fun deleteAll()

    @Query("DELETE FROM bios WHERE folder_id = :folderId")
    suspend fun deleteByFolder(folderId: Long)
}

@Database(
    entities = [FolderEntity::class, GameEntity::class, BiosEntity::class, ProfileEntity::class, PlayStatsEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun games(): GameDao
    abstract fun bios(): BiosDao
    abstract fun profiles(): ProfileDao
    abstract fun stats(): StatsDao
}

/** v1 → v2: nome personalizado do jogo. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game ADD COLUMN custom_title TEXT")
    }
}

/** Perfil inicial, criado em bancos novos e na migração 2 → 3. */
internal const val DEFAULT_PROFILE_SQL =
    "INSERT OR IGNORE INTO profile(id, name, color, created_at) VALUES (1, 'Jogador 1', -7639809, 0)"

/** Cria o perfil 1 em bancos novos. */
val CREATE_DEFAULT_PROFILE = object : RoomDatabase.Callback() {
    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(DEFAULT_PROFILE_SQL)
    }
}

/**
 * v2 → v3: perfis. Favoritos e histórico saem da tabela game e vão para play_stats (perfil 1),
 * ligados pela chave estável game_key (calculada aqui com a mesma regra do app).
 */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
        db.execSQL(DEFAULT_PROFILE_SQL)
        db.execSQL("CREATE TABLE IF NOT EXISTS `play_stats` (`profile_id` INTEGER NOT NULL, `game_key` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `play_count` INTEGER NOT NULL, `last_played_at` INTEGER, `play_time_ms` INTEGER NOT NULL, PRIMARY KEY(`profile_id`, `game_key`))")

        db.execSQL("ALTER TABLE game ADD COLUMN game_key TEXT")
        val keys = ArrayList<Pair<Long, String>>()
        db.query("SELECT id, system, file_name FROM game WHERE system IS NOT NULL").use { c ->
            while (c.moveToNext()) keys += c.getLong(0) to com.retroplay.domain.settings.GameKey.of(com.retroplay.domain.system.SystemId(c.getString(1)), c.getString(2))
        }
        keys.forEach { (id, key) -> db.execSQL("UPDATE game SET game_key = ? WHERE id = ?", arrayOf<Any>(key, id)) }

        db.execSQL(
            "INSERT OR REPLACE INTO play_stats(profile_id, game_key, favorite, play_count, last_played_at, play_time_ms) " +
                "SELECT 1, game_key, MAX(is_favorite), SUM(play_count), MAX(last_played_at), SUM(play_time_ms) FROM game " +
                "WHERE game_key IS NOT NULL AND (is_favorite = 1 OR play_count > 0 OR play_time_ms > 0) GROUP BY game_key",
        )

        val cols = "id, uri, source, folder_id, file_name, size_bytes, last_modified, system, user_system, candidates, title, custom_title, " +
            "dat_name, region, year, developer, genre, players, thumbs, thumb_name, cover_override, crc, status, romset_status, missing_files, corrupted, added_at, game_key"
        db.execSQL("CREATE TABLE IF NOT EXISTS `game_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `source` TEXT NOT NULL, `folder_id` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL, `system` TEXT, `user_system` TEXT, `candidates` TEXT, `title` TEXT NOT NULL, `custom_title` TEXT, `dat_name` TEXT, `region` TEXT, `year` TEXT, `developer` TEXT, `genre` TEXT, `players` INTEGER, `thumbs` INTEGER NOT NULL, `thumb_name` TEXT, `cover_override` TEXT, `crc` INTEGER, `status` TEXT NOT NULL, `romset_status` TEXT NOT NULL, `missing_files` INTEGER NOT NULL, `corrupted` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, `game_key` TEXT)")
        db.execSQL("INSERT INTO game_new ($cols) SELECT $cols FROM game")
        db.execSQL("DROP TABLE game")
        db.execSQL("ALTER TABLE game_new RENAME TO game")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_game_uri` ON `game` (`uri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_system` ON `game` (`system`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_folder_id` ON `game` (`folder_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_status` ON `game` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_game_key` ON `game` (`game_key`)")
    }
}

/**
 * v3 → v4 (app 1.2): o sistema "Neo Geo" virou "Arcade" (MAME 2000 + FBNeo). Renomeia o
 * sistema e as chaves estáveis (favoritos/histórico continuam ligados) e marca os romsets para
 * serem conferidos de novo contra os dois DATs na próxima varredura.
 */
val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game ADD COLUMN core_hint TEXT")
        db.execSQL("UPDATE game SET system = 'arcade', last_modified = -1 WHERE system = 'neogeo'")
        db.execSQL("UPDATE game SET user_system = 'arcade' WHERE user_system = 'neogeo'")
        db.execSQL("UPDATE game SET candidates = REPLACE(candidates, 'neogeo', 'arcade') WHERE candidates LIKE '%neogeo%'")
        db.execSQL("UPDATE game SET game_key = 'arcade/' || SUBSTR(game_key, 8) WHERE game_key LIKE 'neogeo/%'")
        db.execSQL("UPDATE OR REPLACE play_stats SET game_key = 'arcade/' || SUBSTR(game_key, 8) WHERE game_key LIKE 'neogeo/%'")
        db.execSQL("UPDATE bios SET system = 'arcade' WHERE system = 'neogeo'")
    }
}

/** v4 → v5 (app 1.8): perfis com avatar e cor do tema. Perfis antigos ficam "não configurados". */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profile ADD COLUMN avatar TEXT")
        db.execSQL("ALTER TABLE profile ADD COLUMN accent TEXT NOT NULL DEFAULT 'LARANJA'")
    }
}
