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
) : ViewModel() {

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

    /** Copia a neogeo.zip escolhida para Jogos/BIOS (ou a pasta do app) e verifica. */
    fun importBios(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = if (gamesFolder.hasAllFilesAccess() && gamesFolder.ensureCreated()) File(gamesFolder.gamesDir, GamesFolderManager.BIOS_FOLDER)
                    else File(gamesFolder.appDir, "neogeo")
                    dir.mkdirs()
                    context.contentResolver.openInputStream(uri)!!.use { input -> File(dir, "neogeo.zip").outputStream().use { input.copyTo(it) } }
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
