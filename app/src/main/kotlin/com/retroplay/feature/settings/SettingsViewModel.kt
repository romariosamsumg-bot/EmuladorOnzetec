package com.retroplay.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.retroplay.R
import com.retroplay.data.library.GamesFolderManager
import com.retroplay.data.rom.BiosInspector
import com.retroplay.data.settings.CoreOptionsCache
import com.retroplay.data.settings.StorageReport
import com.retroplay.data.settings.StorageUsage
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.library.BiosStatus
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.CoreOption
import com.retroplay.domain.settings.GameKey
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class SettingsCategory(val title: Int, val scoped: Boolean) {
    VIDEO(R.string.cat_video, true),
    AUDIO(R.string.cat_audio, true),
    CONTROLS(R.string.cat_controls, true),
    EMULATION(R.string.cat_emulation, true),
    BIOS(R.string.cat_bios, false),
    LIBRARY(R.string.cat_library, false),
    CORES(R.string.cat_cores, false),
    INTERFACE(R.string.cat_interface, false),
    NETWORK(R.string.cat_network, false),
    STORAGE(R.string.cat_storage, false),
    PARENTAL(R.string.cat_parental, false),
    ABOUT(R.string.cat_about, false),
}

/** Jogo cujas configurações estão sendo editadas (Detalhes → Opções → Configurações deste jogo). */
data class GameContext(val gameKey: String, val system: SystemId, val title: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val cores: CoreRepository,
    val catalog: SystemCatalog,
    private val coreOptionsCache: CoreOptionsCache,
    private val gamesFolder: GamesFolderManager,
    private val profileRepo: com.retroplay.domain.profile.ProfileRepository,
    // Preguiçoso: só abre o banco quando o usuário pede um backup.
    private val backup: dagger.Lazy<com.retroplay.data.backup.BackupManager>,
    private val smb: dagger.Lazy<com.retroplay.data.smb.SmbAccess>,
    private val smbCredentials: dagger.Lazy<com.retroplay.data.smb.SmbCredentialStore>,
) : ViewModel() {

    /** Testa a conexão e, se der certo, adiciona a pasta da rede e procura jogos. true = adicionada. */
    fun addSmbFolder(form: SmbForm, onDone: (Boolean) -> Unit = {}) {
        val loc = com.retroplay.data.smb.SmbLocation.fromInput(form.host, form.share, form.path)
        if (loc == null) {
            message(R.string.smb_invalid); onDone(false); return
        }
        viewModelScope.launch {
            val creds = com.retroplay.data.smb.SmbCredentials(form.user, form.password)
            runCatching { withContext(Dispatchers.IO) { smb.get().test(loc, creds) } }
                .onSuccess { count ->
                    withContext(Dispatchers.IO) { smbCredentials.get().put(loc, creds) }
                    library.addFolder(loc.uri(), com.retroplay.domain.library.GameSource.SMB, loc.label)
                    library.scanNow()
                    _formatted.send(R.string.smb_added to listOf(count))
                    onDone(true)
                }
                .onFailure { e ->
                    _messages.send(
                        when ((e as? com.retroplay.data.smb.SmbException)?.reason) {
                            com.retroplay.data.smb.SmbException.Reason.UNREACHABLE -> R.string.smb_unreachable
                            com.retroplay.data.smb.SmbException.Reason.LOGIN_FAILED -> R.string.smb_login_failed
                            com.retroplay.data.smb.SmbException.Reason.SHARE_NOT_FOUND -> R.string.smb_share_not_found
                            com.retroplay.data.smb.SmbException.Reason.PATH_NOT_FOUND -> R.string.smb_path_not_found
                            else -> R.string.smb_failed
                        },
                    )
                    onDone(false)
                }
        }
    }

    /** Mensagem com valores (ex.: "Backup salvo: 12 arquivos"). */
    private val _formatted = Channel<Pair<Int, List<Any>>>(Channel.BUFFERED)
    val formattedMessages = _formatted.receiveAsFlow()

    private fun backupError(e: Throwable) = when ((e as? com.retroplay.data.backup.BackupException)?.reason) {
        com.retroplay.data.backup.BackupException.Reason.NEWER_FORMAT -> R.string.backup_newer
        com.retroplay.data.backup.BackupException.Reason.NOT_A_BACKUP, com.retroplay.data.backup.BackupException.Reason.CORRUPTED -> R.string.backup_invalid
        null -> R.string.backup_failed
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { backup.get().export(it) } } }
                .onSuccess { _formatted.send(R.string.backup_done to listOf(it.files, it.profiles)) }
                .onFailure { _messages.send(R.string.backup_failed) }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { backup.get().restore(it) } } }
                .onSuccess { _formatted.send(R.string.restore_done to listOf(it.files, it.profiles)) }
                .onFailure { _messages.send(backupError(it)) }
        }
    }

    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { backup.get().exportSettings(it) } } }
                .onSuccess { _messages.send(R.string.settings_exported) }
                .onFailure { _messages.send(R.string.backup_failed) }
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { backup.get().importSettings(it) } } }
                .onSuccess { _messages.send(R.string.settings_imported) }
                .onFailure { _messages.send(backupError(it)) }
        }
    }

    val profiles: StateFlow<List<com.retroplay.domain.profile.Profile>> =
        profileRepo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeProfile: StateFlow<com.retroplay.domain.profile.Profile?> =
        profileRepo.active.map<com.retroplay.domain.profile.Profile, com.retroplay.domain.profile.Profile?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun switchProfile(id: Long) { viewModelScope.launch { profileRepo.switchTo(id) } }

    fun createProfile(name: String) {
        viewModelScope.launch { profileRepo.switchTo(profileRepo.create(name).id) }
    }

    /** Novo perfil completo (avatar, nome e cor) e já o ativa. */
    fun createProfile(name: String, avatar: String, accent: String) {
        viewModelScope.launch { profileRepo.switchTo(profileRepo.create(name, avatar, accent).id) }
    }

    fun updateActiveProfile(name: String, avatar: String, accent: String) {
        viewModelScope.launch { profileRepo.update(profileRepo.activeId(), name, avatar, accent) }
    }

    fun renameActiveProfile(name: String) { viewModelScope.launch { profileRepo.rename(profileRepo.activeId(), name) } }

    fun deleteActiveProfile() { viewModelScope.launch { profileRepo.delete(profileRepo.activeId()) } }

    /** null até o DataStore carregar (evita mostrar a tela antes do bloqueio por PIN). */
    val snapshot: StateFlow<SettingsSnapshot?> = settings.snapshots
        .map<SettingsSnapshot, SettingsSnapshot?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _game = MutableStateFlow<GameContext?>(null)
    val game: StateFlow<GameContext?> = _game.asStateFlow()

    val category: StateFlow<SettingsCategory> = handle.getStateFlow(KEY_CATEGORY, SettingsCategory.VIDEO)

    private val _scope = MutableStateFlow(ScopeRef.Global)
    val scope: StateFlow<ScopeRef> = _scope.asStateFlow()

    /** Todos os núcleos (sem repetir o genesis_plus_gx, que serve dois consoles). */
    val allCores: List<CoreDescriptor> = catalog.all.flatMap { it.cores }.distinctBy { it.id }

    val coreStates: StateFlow<Map<String, CoreInstallState>> =
        combine(allCores.map { core -> cores.observe(core) }) { states -> allCores.map { it.id }.zip(states).toMap() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val bios: StateFlow<List<BiosStatus>> = library.observeBios().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val duplicates: StateFlow<List<Game>> = library.observeDuplicates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folderCount: StateFlow<Int> = MutableStateFlow(0).also { flow ->
        viewModelScope.launch { library.observeFolders().collect { flow.value = it.size } }
    }

    /** A neogeo.zip tem UniBIOS? (null = sem BIOS ou ainda verificando). */
    val unibios: StateFlow<Boolean?> = bios.map { list ->
        val uri = list.firstOrNull { it.setName == "neogeo" }?.uri ?: return@map null
        withContext(Dispatchers.IO) {
            runCatching {
                val input = if (uri.startsWith("content://")) context.contentResolver.openInputStream(android.net.Uri.parse(uri))!! else File(uri).inputStream()
                BiosInspector.hasUniBios(input)
            }.getOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _storage = MutableStateFlow<StorageReport?>(null)
    val storage: StateFlow<StorageReport?> = _storage.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Status de "há atualização" por núcleo (preenchido ao verificar). */
    private val _updates = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val updates: StateFlow<Map<String, Boolean?>> = _updates.asStateFlow()

    init {
        // GameSettingsRoute(gameId): abre direto no nível do jogo.
        handle.get<Long>("gameId")?.let(::openForGame)
    }

    fun openForGame(gameId: Long) {
        viewModelScope.launch {
            val g = library.game(gameId) ?: return@launch
            val sys = g.systemId ?: return@launch
            val ctx = GameContext(GameKey.of(sys, g.fileName), sys, g.title)
            _game.value = ctx
            _scope.value = ScopeRef.game(ctx.gameKey)
        }
    }

    fun selectCategory(c: SettingsCategory) {
        handle[KEY_CATEGORY] = c
        if (c == SettingsCategory.STORAGE) refreshStorage()
    }

    fun selectScope(scope: ScopeRef) { if (_game.value == null) _scope.value = scope }

    /** Console do nível atual (para resolver valores herdados). */
    fun scopeSystem(): SystemId? = when (_scope.value.scope) {
        Scope.GLOBAL -> null
        Scope.SYSTEM -> SystemId(_scope.value.id!!)
        Scope.GAME -> _game.value?.system
    }

    fun scopeGameKey(): String? = if (_scope.value.scope == Scope.GAME) _scope.value.id else null

    fun <T : Any> set(def: SettingDef<T>, value: T?) {
        val target = if (def.scoped) _scope.value else ScopeRef.Global
        viewModelScope.launch { settings.set(def, target, value) }
    }

    fun <T : Any> setAt(def: SettingDef<T>, scope: ScopeRef, value: T?) {
        viewModelScope.launch { settings.set(def, scope, value) }
    }

    fun setCoreOptionAt(core: CoreDescriptor, key: String, scope: ScopeRef, value: String?) {
        viewModelScope.launch { settings.setRaw(AppSettings.coreOptionKey(core.id, key), scope, value) }
    }



    // ---------------------------------------------------------------- núcleos

    fun coreOptions(core: CoreDescriptor): List<CoreOption> = coreOptionsCache.load(core.id)

    fun resetCoreOptions(core: CoreDescriptor) {
        viewModelScope.launch {
            settings.clear(_scope.value, AppSettings.coreOptionPrefix(core.id))
            _messages.send(R.string.core_options_reset_done)
        }
    }

    fun installCore(core: CoreDescriptor) = cores.install(core)

    fun installDefaultCores() = catalog.all.map { it.defaultCore }.distinctBy { it.id }.forEach { cores.install(it) }

    fun removeCore(core: CoreDescriptor) {
        viewModelScope.launch { cores.remove(core) }
    }

    fun rollbackCore(core: CoreDescriptor) {
        viewModelScope.launch {
            _messages.send(if (cores.rollback(core)) R.string.core_rollback_done else R.string.core_rollback_none)
        }
    }

    suspend fun hasPrevious(core: CoreDescriptor) = cores.hasPrevious(core)

    fun checkUpdates() {
        viewModelScope.launch {
            val result = allCores.associate { it.id to cores.checkForUpdate(it) }
            _updates.value = result
            _messages.send(if (result.values.any { it == true }) R.string.core_updates_found else R.string.core_updates_none)
        }
    }

    // ---------------------------------------------------------------- biblioteca / bios / armazenamento

    fun scanNow() = library.scanNow()

    fun rebuildLibrary() {
        viewModelScope.launch { library.rebuild() }
    }

    fun deleteGame(game: Game) {
        viewModelScope.launch { library.deleteGame(game.id) }
    }

    /**
     * Copia a BIOS escolhida para Jogos/BIOS (ou a pasta do app) e verifica: neogeo.zip (Arcade)
     * ou uma BIOS de PlayStation (.bin, mantém o nome, ex.: scph5501.bin).
     */
    fun importBios(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = if (gamesFolder.hasAllFilesAccess() && gamesFolder.ensureCreated()) File(gamesFolder.gamesDir, GamesFolderManager.BIOS_FOLDER)
                    else File(gamesFolder.appDir, "neogeo")
                    dir.mkdirs()
                    val picked = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?.substringAfterLast('/')
                    val name = if (picked != null && picked.lowercase().endsWith(".bin")) picked else "neogeo.zip"
                    context.contentResolver.openInputStream(uri)!!.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
                    true
                }.getOrDefault(false)
            }
            library.scanNow()
            _messages.send(if (ok) R.string.bios_imported else R.string.import_failed)
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val games = library.totalBytes()
            _storage.value = withContext(Dispatchers.IO) { StorageUsage.report(context.filesDir, context.cacheDir, games) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SingletonImageLoader.get(context).let { it.memoryCache?.clear(); it.diskCache?.clear() }
                File(context.cacheDir, "rom-run").deleteRecursively()
            }
            refreshStorage()
            _messages.send(R.string.cache_cleared)
        }
    }

    fun message(res: Int) {
        viewModelScope.launch { _messages.send(res) }
    }

    private companion object {
        const val KEY_CATEGORY = "category"
    }
}
