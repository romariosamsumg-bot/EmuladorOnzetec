package com.retroplay.feature.home

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import com.retroplay.ui.tv.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.retroplay.ui.tv.OutlinedButton
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
import com.retroplay.feature.transfer.PhoneTransferPanel
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransferState
import androidx.lifecycle.compose.LifecycleStartEffect
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
        val phoneState by viewModel.phone.state.collectAsStateWithLifecycle()
        val incoming by viewModel.phone.incoming.collectAsStateWithLifecycle()
        // Biblioteca vazia também é tela de importação: o QR code do celular fica ativo aqui.
        LifecycleStartEffect(viewModel.phone) {
            viewModel.phone.acquire()
            onStopOrDispose { viewModel.phone.release() }
        }
        EmptyHome(viewModel.systems, phoneState, incoming, onOpenTransfer, onOpenConsole)
    } else {
        HomeScreen(
            state = state,
            catalog = SystemRegistry,
            onPlay = viewModel::play,
            onToggleFavorite = viewModel::toggleFavorite,
            onOpenGame = onOpenGame,
            loadSummary = viewModel::summary,
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
    loadSummary: suspend (Game) -> com.retroplay.domain.library.GameSummary? = { null },
) {
    val memory = rememberFocusMemory()
    val heroPlay = remember { FocusRequester() }
    RestoreOrInitialFocus(memory, heroPlay, ready = state.loaded)
    // Jogo em foco nas fileiras: o painel do topo mostra as informações dele (como na Netflix).
    var focusedGame by remember { mutableStateOf<Game?>(null) }

    Column(Modifier.fillMaxSize().testTag("screen_home")) {
    androidx.compose.animation.AnimatedVisibility(visible = focusedGame != null) {
        focusedGame?.let { g -> g.systemId?.let { catalog.byId(it) }?.let { sys -> FocusedInfo(g, sys, loadSummary) } }
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.hero?.let { hero ->
            item(key = "hero") {
                val system = hero.systemId?.let { catalog.byId(it) }
                if (system != null) {
                    // No destaque o próprio banner já mostra o jogo: o painel some.
                    Box(Modifier.onFocusChanged { if (it.hasFocus) focusedGame = null }) {
                        Hero(hero, system, memory, heroPlay, onPlay, onToggleFavorite)
                    }
                }
            }
        }
        items(state.rows, key = { it.type.key }) { row ->
            GameRow(row, catalog, memory, onOpenGame, onOpenConsole, state.continueThumbs) { focusedGame = it }
        }
    }
    }
}

/**
 * Painel do jogo em foco: nome, console · ano · gênero · empresa, resumo (Wikipédia) e uma cena
 * do jogo ao fundo. O resumo só é buscado depois de o foco parar no jogo (rolar não dispara buscas).
 */
@Composable
private fun FocusedInfo(game: Game, system: GameSystem, loadSummary: suspend (Game) -> com.retroplay.domain.library.GameSummary?) {
    val background = MaterialTheme.colorScheme.background
    val summary by androidx.compose.runtime.produceState<com.retroplay.domain.library.GameSummary?>(null, game.id) {
        value = null
        kotlinx.coroutines.delay(450)
        value = loadSummary(game)
    }
    Box(Modifier.fillMaxWidth().height(190.dp).testTag("focus_info")) {
        val art = listOf(CoverType.SNAP, CoverType.TITLE).firstOrNull { it in game.availableCovers }
        if (art != null) {
            AsyncImage(
                model = CoverUrls.url(system, game, art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to background, 0.5f to background, 0.75f to background.copy(alpha = 0.55f), 1f to background.copy(alpha = 0.2f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to background)))
        Column(Modifier.padding(start = 48.dp, top = 20.dp, end = 48.dp).widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(game.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("focus_title"))
            Text(
                listOfNotNull(system.displayName, game.year, game.genre?.let { genreName(it) }, game.developer).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            summary?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("focus_summary"),
                )
            }
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
    val kenBurns = LocalRetroUiPrefs.current.let { it.animations == AnimationLevel.COMPLETAS && it.heroAnimated }
    Box(Modifier.fillMaxWidth().height(420.dp).testTag("hero")) {
        // Arte de fundo: snap > tela de título > capa (a Coil reduz ao tamanho do banner).
        val art = listOf(CoverType.SNAP, CoverType.TITLE, CoverType.BOXART).firstOrNull { it in game.availableCovers }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(system.accentColor), background))))
        if (art != null) {
            // O valor animado é lido só no desenho (graphicsLayer), nunca na composição: a
            // animação contínua não recompõe a tela a cada quadro enquanto o usuário navega.
            val scale = if (kenBurns) {
                val t = rememberInfiniteTransition(label = "kenburns")
                t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Reverse), label = "scale")
            } else null
            AsyncImage(
                model = CoverUrls.url(system, game, art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val s = scale?.value ?: 1f
                    scaleX = s
                    scaleY = s
                },
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
    continueThumbs: Map<Long, String> = emptyMap(),
    onFocusGame: (Game) -> Unit = {},
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
            items(row.games, key = { it.id }, contentType = { "game" }) { game ->
                val system = game.systemId?.let { catalog.byId(it) } ?: return@items
                val key = "${row.type.key}/${game.id}"
                val focusMod = Modifier.onFocusChanged { if (it.isFocused) onFocusGame(game) }
                if (row.type == RowType.ContinuePlaying) {
                    ContinueCard(system, game, continueThumbs[game.id], { onOpenGame(game) }, focusMod.rememberFocus(memory, key), "card_$key")
                    return@items
                }
                GameCard(
                    system = system,
                    game = game,
                    title = game.title,
                    onClick = { onOpenGame(game) },
                    cardModifier = focusMod.rememberFocus(memory, key),
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
internal fun rowTitle(type: RowType, system: GameSystem?): String = when (type) {
    RowType.ContinuePlaying -> stringResource(R.string.row_continue)
    RowType.Favorites -> stringResource(R.string.row_favorites)
    RowType.RecentlyAdded -> stringResource(R.string.row_recent)
    RowType.MostPlayed -> stringResource(R.string.row_most_played)
    RowType.Popular -> stringResource(R.string.row_popular)
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

/** Biblioteca vazia: passo a passo para importar jogos + QR code para enviar pelo celular. */
@Composable
private fun EmptyHome(
    systems: List<GameSystem>,
    phoneState: PhoneTransferState,
    incoming: List<IncomingFile>,
    onOpenTransfer: () -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val memory = rememberFocusMemory()
    RestoreOrInitialFocus(memory, focus)
    // Lista rolável: a TV tem só ~540 dp de altura; o foco rola até a fileira de consoles.
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_home"),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "intro") {
            Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Logo da marca (tools/brand/logo.png).
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.logo_onzetec),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(72.dp).testTag("home_logo"),
                    )
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.displaySmall)
                    listOf(R.string.home_step_1, R.string.home_step_2, R.string.home_step_3).forEachIndexed { i, step ->
                        Text(
                            "${i + 1}. " + stringResource(step),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.legal_notice_short), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = onOpenTransfer,
                        modifier = Modifier.focusRequester(focus).rememberFocus(memory, "transfer").testTag("home_transfer"),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(UiR.drawable.ic_nav_transfer), contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.nav_transfer))
                        }
                    }
                }
                Column(Modifier.width(420.dp).testTag("home_phone"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.home_phone_title), style = MaterialTheme.typography.titleLarge)
                    PhoneTransferPanel(phoneState, incoming, compact = true)
                }
            }
        }
        item(key = "systems") {
            Column {
                Text(stringResource(R.string.home_supported_systems), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp, top = 12.dp))
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
    }
}

/** Card largo de "Continuar jogando": miniatura do último save (ou a capa) + tempo jogado. */
@Composable
private fun ContinueCard(system: GameSystem, game: Game, thumbnail: String?, onClick: () -> Unit, cardModifier: Modifier, tag: String) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    Column(Modifier.width(240.dp)) {
        com.retroplay.ui.tv.Card(
            onClick = onClick,
            modifier = cardModifier.fillMaxWidth().aspectRatio(4f / 3f).testTag(tag),
            shape = androidx.tv.material3.CardDefaults.shape(shape),
            scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.08f),
            border = androidx.tv.material3.CardDefaults.border(
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = shape),
            ),
        ) {
            if (thumbnail != null) {
                AsyncImage(model = java.io.File(thumbnail), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                com.retroplay.ui.components.GameCover(system, game, game.title, Modifier.fillMaxSize())
            }
        }
        Text(game.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
        val minutes = game.playTimeMs / 60_000
        Text(
            if (minutes < 60) stringResource(R.string.duration_minutes, minutes) else stringResource(R.string.duration_hours, minutes / 60, minutes % 60),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
