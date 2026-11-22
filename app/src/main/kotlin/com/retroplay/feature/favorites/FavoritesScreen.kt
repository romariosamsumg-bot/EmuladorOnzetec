package com.retroplay.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.retroplay.R
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFilter
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.applyFilter
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.GameCard
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.ui.components.RestoreOrInitialFocus
import com.retroplay.ui.components.rememberFocus
import com.retroplay.ui.components.rememberFocusMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.core.ui.R as UiR

@HiltViewModel
class FavoritesViewModel @Inject constructor(library: LibraryRepository) : ViewModel() {
    val favorites: StateFlow<List<Game>?> = library.observeAll()
        .map { it.applyFilter(GameFilter.FAVORITES) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun FavoritesScreen(onOpenGame: (Game) -> Unit, onBrowse: () -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val list = favorites ?: return
    if (list.isEmpty()) {
        PlaceholderScreen(
            title = stringResource(R.string.favorites_title),
            body = stringResource(R.string.favorites_body),
            iconRes = UiR.drawable.ic_nav_favorites,
            testTag = "screen_favorites",
            actionLabel = stringResource(R.string.favorites_browse),
            onAction = onBrowse,
        )
        return
    }
    val memory = rememberFocusMemory()
    val first = remember { FocusRequester() }
    RestoreOrInitialFocus(memory, first)
    Column(Modifier.fillMaxSize().padding(top = 40.dp).testTag("screen_favorites")) {
        Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 48.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp * LocalRetroUiPrefs.current.cardScale),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(list, key = { _, g -> g.id }) { i, game ->
                val system = game.systemId?.let { SystemRegistry.byId(it) } ?: return@itemsIndexed
                GameCard(
                    system = system, game = game, title = game.title, onClick = { onOpenGame(game) },
                    cardModifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).rememberFocus(memory, "fav_${game.id}"),
                    testTag = "fav_${game.id}",
                )
            }
        }
    }
}
