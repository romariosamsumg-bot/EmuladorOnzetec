package com.retroplay.feature.details

import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.DeleteResult
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.feature.play.PlayLauncher
import com.retroplay.navigation.GameDetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.retroplay.data.saves.SaveStateStore
import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import javax.inject.Inject

/** null = carregando; Missing = o jogo não existe mais. */
sealed interface DetailsState {
    data object Loading : DetailsState
    data object Missing : DetailsState
    data class Ready(val game: Game, val system: GameSystem, val latestSave: SaveSlot? = null) : DetailsState
}

@HiltViewModel
class GameDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val catalog: SystemCatalog,
    launcherFactory: PlayLauncher.Factory,
    private val saves: SaveStateStore,
    settings: SettingsRepository,
    private val info: com.retroplay.domain.library.GameInfoSource,
) : ViewModel() {

    /** Resumo do jogo (Wikipédia), carregado uma vez por título; null enquanto/sem resultado. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summary: StateFlow<com.retroplay.domain.library.GameSummary?> by lazy {
        state.map { (it as? DetailsState.Ready)?.let { r -> r.game.title to r.system.displayName } }
            .distinctUntilChanged()
            .mapLatest { key -> key?.let { (t, s) -> runCatching { info.summary(t, s) }.getOrNull() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    /** PIN exigido para apagar (Controle parental), ou null. */
    val deletePin: StateFlow<String?> = settings.snapshots
        .map { s -> s.value(AppSettings.PARENTAL_PIN).takeIf { it.isNotEmpty() && s.value(AppSettings.PARENTAL_DELETE) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val gameId = savedStateHandle.toRoute<GameDetailsRoute>().gameId
    val launcher = launcherFactory.create(viewModelScope)

    val state: StateFlow<DetailsState> = library.observeGame(gameId)
        .map { game ->
            val system = game?.systemId?.let { catalog.byId(it) }
            if (game == null || system == null) DetailsState.Missing
            else DetailsState.Ready(game, system, withContext(Dispatchers.IO) { saves.latest(system.id, game.fileName) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState.Loading)

    private val _deleted = Channel<DeleteResult>(Channel.BUFFERED)
    val deleteResults = _deleted.receiveAsFlow()

    fun play(game: Game) = launcher.play(game)

    /** Abre o jogo já no save mais recente. */
    fun continueFrom(game: Game, slot: SaveSlot) = launcher.play(game, loadSlot = slot.slot)

    fun toggleFavorite(game: Game) {
        viewModelScope.launch { library.setFavorite(game.id, !game.isFavorite) }
    }

    fun setCover(game: Game, cover: CoverType?) {
        viewModelScope.launch { library.setCover(game.id, cover) }
    }

    fun rename(game: Game, title: String?) {
        viewModelScope.launch { library.rename(game.id, title?.trim()?.takeIf { it.isNotEmpty() }) }
    }

    fun delete(game: Game) {
        viewModelScope.launch { _deleted.send(library.deleteGame(game.id)) }
    }
}
