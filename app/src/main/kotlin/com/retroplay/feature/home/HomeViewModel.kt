package com.retroplay.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.HomeRow
import com.retroplay.domain.library.HomeRowsBuilder
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.feature.play.PlayLauncher
import com.retroplay.domain.transfer.PhoneTransfer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(val hero: Game?, val rows: List<HomeRow>, val loaded: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: SystemCatalog,
    private val library: LibraryRepository,
    launcherFactory: PlayLauncher.Factory,
    val phone: PhoneTransfer,
) : ViewModel() {

    val systems: List<GameSystem> = catalog.all
    val launcher = launcherFactory.create(viewModelScope)

    /** Semente do destaque aleatório: estável durante a sessão. */
    private val heroSeed = System.nanoTime()

    val state: StateFlow<HomeState> = library.observeAll()
        .map { games -> HomeState(HomeRowsBuilder.hero(games, heroSeed), HomeRowsBuilder.build(games, catalog), loaded = true) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(null, emptyList(), loaded = false))

    fun system(game: Game): GameSystem? = game.systemId?.let { catalog.byId(it) }

    fun play(game: Game) = launcher.play(game)

    fun toggleFavorite(game: Game) {
        viewModelScope.launch { library.setFavorite(game.id, !game.isFavorite) }
    }
}
