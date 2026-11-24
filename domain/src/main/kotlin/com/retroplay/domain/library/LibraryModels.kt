package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId
import kotlinx.coroutines.flow.Flow

/** De onde vem o arquivo: caminho comum ou documento do seletor do sistema (SAF / pendrive). */
/** Origem da pasta: caminho comum, pasta do seletor do sistema (SAF) ou pasta da rede (SMB). */
enum class GameSource { FILE, DOCUMENT, SMB }

enum class IdentStatus {
    /** Casou com o DAT (nome oficial, ano, gênero...). */
    IDENTIFIED,

    /** Console conhecido, mas o arquivo não está no DAT (hack, tradução, dump diferente). */
    UNIDENTIFIED,

    /** Extensão ambígua e conteúdo não identificado: o usuário escolhe o console (uma vez). */
    NEEDS_SYSTEM,
}

/** Situação de um romset de arcade (Neo Geo) frente ao DAT do FBNeo. */
enum class RomsetStatus {
    NOT_APPLICABLE,
    COMPLETE,

    /** Faltam arquivos dentro do .zip. */
    INCOMPLETE,

    /** Arquivos com o nome certo e conteúdo diferente: romset de outra versão (ex.: MAME antigo). */
    WRONG_VERSION,

    /** Clone que depende do romset "pai", que não está na mesma pasta. */
    NEEDS_PARENT,

    /** O nome do .zip não existe no DAT do FBNeo. */
    UNKNOWN_SET,
}

enum class CoverType(val folder: String, val bit: Int) {
    BOXART("Named_Boxarts", 1),
    TITLE("Named_Titles", 2),
    SNAP("Named_Snaps", 4),
}

data class Game(
    val id: Long,
    /** Caminho absoluto (FILE) ou URI content:// (DOCUMENT). */
    val uri: String,
    val source: GameSource,
    val folderId: Long,
    val fileName: String,
    val sizeBytes: Long,
    val systemId: SystemId?,
    val title: String,
    val datName: String?,
    val region: String?,
    val year: String?,
    val developer: String?,
    val genre: String?,
    val players: Int?,
    /** Capas disponíveis (bits de [CoverType]). */
    val thumbs: Int,
    /** Nome usado nas capas (quando difere de [datName]). */
    val thumbName: String?,
    /** Capa escolhida pelo usuário (null = automática). */
    val coverOverride: CoverType?,
    val status: IdentStatus,
    val romsetStatus: RomsetStatus,
    val missingFiles: Int,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val addedAt: Long,
    val playTimeMs: Long,
    /** Arcade: núcleo indicado pelo romset (o DAT que ele satisfaz), ex.: "mame2000". */
    val coreHint: String? = null,
) {
    val availableCovers: List<CoverType> get() = CoverType.entries.filter { thumbs and it.bit != 0 }

    /** Capa a exibir: a escolhida pelo usuário, senão boxart → título → snap. */
    val cover: CoverType? get() = coverOverride?.takeIf { it in availableCovers } ?: availableCovers.firstOrNull()

    val coverName: String? get() = thumbName ?: datName
}

data class GameFolder(
    val id: Long,
    val uri: String,
    val source: GameSource,
    val displayName: String,
    val isDefault: Boolean,
    val lastScanAt: Long?,
    val gameCount: Int = 0,
)

/** Cópia de uma pasta (pendrive) para a TV. */
sealed interface CopyState {
    data object Idle : CopyState
    data class Copying(val done: Int, val total: Int, val currentFile: String?) : CopyState
    data class Finished(val copied: Int) : CopyState
    data class Failed(val noSpace: Boolean, val copied: Int) : CopyState
}

sealed interface ScanState {
    data class Idle(val lastFinishedAt: Long?) : ScanState
    data class Scanning(val processed: Int, val total: Int, val currentFile: String?) : ScanState
    data object Failed : ScanState
}

enum class DeleteResult { DELETED, NO_PERMISSION, NOT_FOUND }

/** BIOS encontrada numa pasta monitorada (ex.: neogeo.zip). */
data class BiosStatus(
    val system: SystemId,
    val setName: String,
    val uri: String,
    val complete: Boolean,
    /** Núcleo → serve para ele (vazio = não se sabe; vale [complete]). */
    val byCore: Map<String, Boolean> = emptyMap(),
) {
    /** Serve para o [core]? null = o app não sabe (BIOS sem versão no DAT desse núcleo). */
    fun completeFor(core: String?): Boolean? = core?.let { byCore[it] }

    /** Serve para todos os núcleos conhecidos? (card do console: "BIOS ok"). */
    val completeForAll: Boolean get() = if (byCore.isEmpty()) complete else byCore.values.all { it }

    /** Núcleos para os quais NÃO serve. */
    val failingCores: List<String> get() = byCore.filterValues { !it }.keys.sorted()
}

/** Situação das pastas padrão (Jogos e a pasta privada do app). */
data class DefaultFolders(
    /** Caminho da pasta Jogos (ex.: /storage/emulated/0/Jogos). */
    val gamesPath: String,
    /** true quando o app tem acesso e a pasta foi criada/registrada. */
    val gamesFolderReady: Boolean,
    val appPath: String,
)

interface LibraryRepository {
    /** Cria/registra as pastas padrão (chamar ao abrir o app e ao voltar das permissões). */
    suspend fun ensureDefaultFolders(): DefaultFolders

    fun observeGames(system: SystemId): Flow<List<Game>>
    fun observeAll(): Flow<List<Game>>
    fun observeNeedsSystem(): Flow<List<Game>>
    fun observeFolders(): Flow<List<GameFolder>>
    fun observeScan(): Flow<ScanState>
    fun observeCount(): Flow<Int>

    suspend fun game(id: Long): Game?
    fun observeGame(id: Long): Flow<Game?>

    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setCover(id: Long, cover: CoverType?)

    /** Registra que o jogo foi aberto agora (contagem + "continuar jogando"). */
    suspend fun recordPlayStarted(id: Long, atMillis: Long)
    suspend fun addPlayTime(id: Long, millis: Long)

    /** Apaga o arquivo do jogo (e o tira da biblioteca). */
    suspend fun deleteGame(id: Long): DeleteResult

    /** Nome personalizado (null volta ao nome do banco de dados). */
    suspend fun rename(id: Long, title: String?)

    fun observeDuplicates(): Flow<List<Game>>
    fun observeBios(): Flow<List<BiosStatus>>

    /** Apaga a lista (não os arquivos) e verifica tudo de novo. */
    suspend fun rebuild()

    suspend fun totalBytes(): Long
    suspend fun addFolder(uri: String, source: GameSource, displayName: String, isDefault: Boolean = false): GameFolder
    suspend fun removeFolder(id: Long)

    /** Varredura incremental em segundo plano (só arquivos novos ou alterados). */
    fun scanNow()

    /** Copia os jogos da pasta (ex.: pendrive) para a TV; depois a pasta sai da biblioteca. */
    fun copyFolderToDevice(folderId: Long)
    fun observeCopy(): Flow<CopyState>

    /** Escolha do usuário para arquivo ambíguo; vale também para as próximas varreduras. */
    suspend fun assignSystem(gameId: Long, system: SystemId)
}
