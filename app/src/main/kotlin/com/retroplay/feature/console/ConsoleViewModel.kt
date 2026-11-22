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
import com.retroplay.domain.library.GameFilter
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.library.applyFilter
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.feature.play.LaunchRequest
import com.retroplay.feature.play.PlayLauncher
import com.retroplay.navigation.ConsoleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    catalog: SystemCatalog,
    private val cores: CoreRepository,
    private val library: LibraryRepository,
    private val roms: LocalRomSource,
    launcherFactory: PlayLauncher.Factory,
) : ViewModel() {

    val system: GameSystem = catalog.byId(SystemId(handle.toRoute<ConsoleDetailRoute>().systemId)) ?: catalog.all.first()
    val core = system.defaultCore
    val launcher = launcherFactory.create(viewModelScope)
    val hasTestRom = system.id == SystemRegistry.Ids.SNES

    val coreState: StateFlow<CoreInstallState> = cores.observe(core)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoreInstallState.NotInstalled)

    val filter: StateFlow<GameFilter> = handle.getStateFlow(KEY_FILTER, GameFilter.AZ)

    /** null enquanto carrega. */
    val games: StateFlow<List<Game>?> = combine(library.observeGames(system.id), filter) { list, f -> list.applyFilter(f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val scan: StateFlow<ScanState> = library.observeScan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState.Idle(null))

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setFilter(f: GameFilter) { handle[KEY_FILTER] = f }

    fun downloadCore() = cores.install(core)

    fun playTestRom(title: String) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) { roms.testRom() }
            launcher.play(LaunchRequest(system, core, f.absolutePath, f.name, title, gameId = null))
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { roms.import(system, uri) }
            _messages.send(
                when (result) {
                    LocalRomSource.ImportResult.Ok -> R.string.import_ok
                    LocalRomSource.ImportResult.WrongType -> R.string.import_wrong_type
                    LocalRomSource.ImportResult.NoSpace -> R.string.error_no_space
                    LocalRomSource.ImportResult.Failed -> R.string.import_failed
                },
            )
        }
    }

    private companion object {
        const val KEY_FILTER = "filter"
    }
}
