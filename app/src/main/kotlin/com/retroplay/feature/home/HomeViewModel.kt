package com.retroplay.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.HomeRow
import com.retroplay.domain.library.HomeRowsBuilder
import com.retroplay.domain.library.RowType
import com.retroplay.data.saves.SaveStateStore
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
import kotlinx.coroutines.flow.combine
import com.retroplay.domain.library.arranged
import com.retroplay.domain.library.visible
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.feature.settings.parseHidden
import com.retroplay.feature.settings.parseRowOrder
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val hero: Game?,
    val rows: List<HomeRow>,
    val loaded: Boolean,
    /** Miniatura do último save de cada jogo da fileira "Continuar jogando". */
    val continueThumbs: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalog: SystemCatalog,
    private val library: LibraryRepository,
    launcherFactory: PlayLauncher.Factory,
    val phone: PhoneTransfer,
    private val saves: SaveStateStore,
    settings: SettingsRepository,
) : ViewModel() {

    val systems: List<GameSystem> = catalog.all
    val launcher = launcherFactory.create(viewModelScope)

    /** Semente do destaque aleatório: estável durante a sessão. */
    private val heroSeed = System.nanoTime()

    val state: StateFlow<HomeState> = combine(library.observeAll(), settings.snapshots) { all, snap ->
            val games = all.visible(snap.value(AppSettings.HIDE_UNIDENTIFIED))
            val rows = HomeRowsBuilder.build(games, catalog).arranged(
                parseRowOrder(snap.value(AppSettings.ROW_ORDER)),
                parseHidden(snap.value(AppSettings.HIDDEN_ROWS)),
            )
            val thumbs = rows.firstOrNull { it.type == RowType.ContinuePlaying }?.games.orEmpty().mapNotNull { g ->
                val sys = g.systemId ?: return@mapNotNull null
                saves.latest(sys, g.fileName)?.thumbnailPath?.let { g.id to it }
            }.toMap()
            HomeState(HomeRowsBuilder.hero(games, heroSeed), rows, loaded = true, continueThumbs = thumbs)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(null, emptyList(), loaded = false))

    fun system(game: Game): GameSystem? = game.systemId?.let { catalog.byId(it) }

    fun play(game: Game) = launcher.play(game)

    fun toggleFavorite(game: Game) {
        viewModelScope.launch { library.setFavorite(game.id, !game.isFavorite) }
    }
}
