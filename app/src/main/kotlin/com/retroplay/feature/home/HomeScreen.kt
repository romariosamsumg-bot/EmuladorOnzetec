package com.retroplay.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.retroplay.R
import com.retroplay.core.ui.theme.AnimationLevel
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.HomeRow
import com.retroplay.domain.library.RowType
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.ui.components.FocusMemory
import com.retroplay.ui.components.GameCard
import com.retroplay.ui.components.RestoreOrInitialFocus
import com.retroplay.ui.components.SystemCard
import com.retroplay.ui.components.rememberFocus
import com.retroplay.ui.components.rememberFocusMemory
import com.retroplay.core.ui.R as UiR

@Composable
fun HomeEntry(
    onOpenTransfer: () -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
    onOpenGame: (Game) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchGamesEffect(viewModel.launcher)
    if (state.loaded && state.rows.isEmpty()) {
        EmptyHome(viewModel.systems, onOpenTransfer, onOpenConsole)
    } else {
        HomeScreen(
            state = state,
            catalog = SystemRegistry,
            onPlay = viewModel::play,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenGame = onOpenGame,
            onOpenConsole = onOpenConsole,
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeState,
    catalog: SystemCatalog,
    onPlay: (Game) -> Unit,
    onToggleFavorite: (Game) -> Unit,
    onOpenGame: (Game) -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
) {
    val memory = rememberFocusMemory()
    val heroPlay = remember { FocusRequester() }
    RestoreOrInitialFocus(memory, heroPlay, ready = state.loaded)

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_home"),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.hero?.let { hero ->
            item(key = "hero") {
                val system = hero.systemId?.let { catalog.byId(it) }
                if (system != null) {
                    Hero(hero, system, memory, heroPlay, onPlay, onToggleFavorite)
                }
            }
        }
        items(state.rows, key = { it.type.key }) { row ->
            GameRow(row, catalog, memory, onOpenGame, onOpenConsole)
        }
    }
}

@Composable
private fun Hero(
    game: Game,
    system: GameSystem,
    memory: FocusMemory,
    playRequester: FocusRequester,
    onPlay: (Game) -> Unit,
    onToggleFavorite: (Game) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val kenBurns = LocalRetroUiPrefs.current.animations == AnimationLevel.COMPLETAS
    Box(Modifier.fillMaxWidth().height(420.dp).testTag("hero")) {
        // Arte de fundo: snap > tela de título > capa (a Coil reduz ao tamanho do banner).
        val art = listOf(CoverType.SNAP, CoverType.TITLE, CoverType.BOXART).firstOrNull { it in game.availableCovers }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(system.accentColor), background))))
        if (art != null) {
            val scale = if (kenBurns) {
                val t = rememberInfiniteTransition(label = "kenburns")
                t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "scale").value
            } else 1f
            AsyncImage(
                model = CoverUrls.url(system, game, art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to background, 0.55f to background.copy(alpha = 0.6f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to background)))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 32.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(if (game.lastPlayedAt != null) R.string.hero_continue else R.string.hero_featured),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(game.title, style = MaterialTheme.typography.displayMedium, maxLines = 2, modifier = Modifier.testTag("hero_title"))
            Text(
                listOfNotNull(system.displayName, game.year, game.genre?.let { genreName(it) }).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onPlay(game) },
                    modifier = Modifier.focusRequester(playRequester).rememberFocus(memory, "hero_play").testTag("hero_play"),
                ) {
                    Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.play))
                }
                OutlinedButton(
                    onClick = { onToggleFavorite(game) },
                    modifier = Modifier.rememberFocus(memory, "hero_fav").testTag("hero_favorite"),
                ) {
                    Text(stringResource(if (game.isFavorite) R.string.remove_favorite else R.string.add_favorite))
                }
            }
        }
    }
}

@Composable
private fun GameRow(
    row: HomeRow,
    catalog: SystemCatalog,
    memory: FocusMemory,
    onOpenGame: (Game) -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
) {
    val rowSystem = (row.type as? RowType.BySystem)?.let { catalog.byId(it.system) }
    Column(Modifier.testTag("row_${row.type.key}")) {
        Text(
            rowTitle(row.type, rowSystem),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 48.dp, top = 8.dp),
        )
        LazyRow(
            // Volta ao último card da fileira ao subir/descer entre fileiras.
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(row.games, key = { it.id }) { game ->
                val system = game.systemId?.let { catalog.byId(it) } ?: return@items
                val key = "${row.type.key}/${game.id}"
                GameCard(
                    system = system,
                    game = game,
                    title = game.title,
                    onClick = { onOpenGame(game) },
                    cardModifier = Modifier.rememberFocus(memory, key),
                    testTag = "card_$key",
                )
            }
            if (rowSystem != null) {
                item(key = "all") {
                    OutlinedButton(
                        onClick = { onOpenConsole(rowSystem) },
                        modifier = Modifier.padding(top = 70.dp).rememberFocus(memory, "${row.type.key}/all").testTag("see_all_${rowSystem.id.value}"),
                    ) { Text(stringResource(R.string.see_all)) }
                }
            }
        }
    }
}

@Composable
private fun rowTitle(type: RowType, system: GameSystem?): String = when (type) {
    RowType.ContinuePlaying -> stringResource(R.string.row_continue)
    RowType.Favorites -> stringResource(R.string.row_favorites)
    RowType.RecentlyAdded -> stringResource(R.string.row_recent)
    RowType.MostPlayed -> stringResource(R.string.row_most_played)
    is RowType.BySystem -> system?.displayName.orEmpty()
    is RowType.ByGenre -> genreName(type.genre)
}

/** Gêneros vêm em inglês do libretro-database; traduz os mais comuns. */
@Composable
fun genreName(genre: String): String {
    val res = when (genre.lowercase()) {
        "action" -> R.string.genre_action
        "platform" -> R.string.genre_platform
        "sports" -> R.string.genre_sports
        "racing", "racing / driving" -> R.string.genre_racing
        "puzzle" -> R.string.genre_puzzle
        "role-playing (rpg)", "rpg" -> R.string.genre_rpg
        "shooter", "shoot'em up" -> R.string.genre_shooter
        "fighting", "beat'em up" -> R.string.genre_fighting
        "adventure" -> R.string.genre_adventure
        "strategy" -> R.string.genre_strategy
        "simulation" -> R.string.genre_simulation
        else -> null
    }
    return res?.let { stringResource(it) } ?: genre
}

/** Biblioteca vazia: passo a passo para importar jogos. */
@Composable
private fun EmptyHome(systems: List<GameSystem>, onOpenTransfer: () -> Unit, onOpenConsole: (GameSystem) -> Unit) {
    val focus = remember { FocusRequester() }
    val memory = rememberFocusMemory()
    RestoreOrInitialFocus(memory, focus)
    Column(Modifier.fillMaxSize().padding(top = 48.dp).testTag("screen_home")) {
        Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.displaySmall)
            listOf(R.string.home_step_1, R.string.home_step_2, R.string.home_step_3).forEachIndexed { i, step ->
                Text(
                    "${i + 1}. " + stringResource(step),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
            Text(stringResource(R.string.legal_notice_short), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onOpenTransfer,
                modifier = Modifier.focusRequester(focus).rememberFocus(memory, "transfer").testTag("home_transfer"),
                colors = ButtonDefaults.colors(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(UiR.drawable.ic_nav_transfer), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.nav_transfer))
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.home_supported_systems), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 48.dp))
        LazyRow(
            modifier = Modifier.focusRestorer().testTag("home_systems_row"),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(systems, key = { it.id.value }) { system ->
                SystemCard(system = system, onClick = { onOpenConsole(system) }, modifier = Modifier.rememberFocus(memory, "sys_${system.id.value}"))
            }
        }
    }
}
