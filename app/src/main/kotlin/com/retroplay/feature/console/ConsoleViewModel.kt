package com.retroplay.feature.console

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.retroplay.R
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.navigation.ConsoleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Item da lista de jogos da página do console. */
data class ConsoleGame(
    val key: String,
    val title: String,
    val uri: String,
    val fileName: String,
    val game: Game?,
    val isTestRom: Boolean = false,
)

sealed interface ConsoleEvent {
    data class Launch(val game: ConsoleGame) : ConsoleEvent
    data class Message(val text: Int) : ConsoleEvent
}

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: SystemCatalog,
    private val cores: CoreRepository,
    private val library: LibraryRepository,
    private val roms: LocalRomSource,
) : ViewModel() {

    val system: GameSystem = catalog.byId(SystemId(savedStateHandle.toRoute<ConsoleDetailRoute>().systemId))
        ?: catalog.all.first()
    val core = system.defaultCore

    val coreState: StateFlow<CoreInstallState> = cores.observe(core)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoreInstallState.NotInstalled)

    /** null enquanto carrega. */
    val games: StateFlow<List<ConsoleGame>?> = library.observeGames(system.id)
        .map { list ->
            val test = if (system.id == SystemRegistry.Ids.SNES) {
                val f = withContext(Dispatchers.IO) { roms.testRom() }
                listOf(ConsoleGame("testrom", "", f.absolutePath, f.name, null, isTestRom = true))
            } else emptyList()
            test + list.map { ConsoleGame("g${it.id}", it.title, it.uri, it.fileName, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val scan: StateFlow<ScanState> = library.observeScan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState.Idle(null))

    private val _events = Channel<ConsoleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Jogo aguardando o núcleo terminar de baixar. */
    private var pendingLaunch: ConsoleGame? = null

    init {
        viewModelScope.launch {
            coreState.filterIsInstance<CoreInstallState.Installed>().collect {
                pendingLaunch?.let { game ->
                    pendingLaunch = null
                    _events.send(ConsoleEvent.Launch(game))
                }
            }
        }
    }

    fun downloadCore() = cores.install(core)

    /** Joga; se o núcleo ainda não existe, baixa primeiro e abre o jogo ao terminar. */
    fun play(game: ConsoleGame) {
        viewModelScope.launch {
            if (cores.installed(core) != null) {
                _events.send(ConsoleEvent.Launch(game))
            } else {
                pendingLaunch = game
                cores.install(core)
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { roms.import(system, uri) }
            _events.send(
                ConsoleEvent.Message(
                    when (result) {
                        LocalRomSource.ImportResult.Ok -> R.string.import_ok
                        LocalRomSource.ImportResult.WrongType -> R.string.import_wrong_type
                        LocalRomSource.ImportResult.NoSpace -> R.string.error_no_space
                        LocalRomSource.ImportResult.Failed -> R.string.import_failed
                    },
                ),
            )
        }
    }
}
