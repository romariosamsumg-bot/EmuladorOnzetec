package com.retroplay.feature.console

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import com.retroplay.navigation.ConsoleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ConsoleEvent {
    data class Launch(val game: LocalGame) : ConsoleEvent
    data class Message(val text: Int) : ConsoleEvent
}

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    catalog: SystemCatalog,
    private val cores: CoreRepository,
    private val roms: LocalRomSource,
) : ViewModel() {

    val system: GameSystem = catalog.byId(SystemId(savedStateHandle.toRoute<ConsoleDetailRoute>().systemId))
        ?: catalog.all.first()
    val core = system.defaultCore

    val coreState: StateFlow<CoreInstallState> = cores.observe(core)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoreInstallState.NotInstalled)

    /** null enquanto carrega. */
    private val _games = MutableStateFlow<List<LocalGame>?>(null)
    val games: StateFlow<List<LocalGame>?> = _games.asStateFlow()

    private val _events = Channel<ConsoleEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Jogo aguardando o núcleo terminar de baixar. */
    private var pendingLaunch: LocalGame? = null

    init {
        refresh()
        viewModelScope.launch {
            coreState.filterIsInstance<CoreInstallState.Installed>().collect {
                pendingLaunch?.let { game ->
                    pendingLaunch = null
                    _events.send(ConsoleEvent.Launch(game))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { _games.value = withContext(Dispatchers.IO) { roms.list(system) } }
    }

    fun downloadCore() = cores.install(core)

    /** Joga; se o núcleo ainda não existe, baixa primeiro e abre o jogo ao terminar. */
    fun play(game: LocalGame) {
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
                        LocalRomSource.ImportResult.Ok -> com.retroplay.R.string.import_ok
                        LocalRomSource.ImportResult.WrongType -> com.retroplay.R.string.import_wrong_type
                        LocalRomSource.ImportResult.NoSpace -> com.retroplay.R.string.error_no_space
                        LocalRomSource.ImportResult.Failed -> com.retroplay.R.string.import_failed
                    },
                ),
            )
            refresh()
        }
    }


}
