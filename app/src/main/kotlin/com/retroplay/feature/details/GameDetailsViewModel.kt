package com.retroplay.feature.details

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
import javax.inject.Inject

/** null = carregando; Missing = o jogo não existe mais. */
sealed interface DetailsState {
    data object Loading : DetailsState
    data object Missing : DetailsState
    data class Ready(val game: Game, val system: GameSystem) : DetailsState
}

@HiltViewModel
class GameDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: LibraryRepository,
    private val catalog: SystemCatalog,
    launcherFactory: PlayLauncher.Factory,
) : ViewModel() {

    private val gameId = savedStateHandle.toRoute<GameDetailsRoute>().gameId
    val launcher = launcherFactory.create(viewModelScope)

    val state: StateFlow<DetailsState> = library.observeGame(gameId)
        .map { game ->
            val system = game?.systemId?.let { catalog.byId(it) }
            if (game == null || system == null) DetailsState.Missing else DetailsState.Ready(game, system)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsState.Loading)

    private val _deleted = Channel<DeleteResult>(Channel.BUFFERED)
    val deleteResults = _deleted.receiveAsFlow()

    fun play(game: Game) = launcher.play(game)

    fun toggleFavorite(game: Game) {
        viewModelScope.launch { library.setFavorite(game.id, !game.isFavorite) }
    }

    fun setCover(game: Game, cover: CoverType?) {
        viewModelScope.launch { library.setCover(game.id, cover) }
    }

    fun delete(game: Game) {
        viewModelScope.launch { _deleted.send(library.deleteGame(game.id)) }
    }
}
