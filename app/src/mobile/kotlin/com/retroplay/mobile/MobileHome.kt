package com.retroplay.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.retroplay.R
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.HomeRow
import com.retroplay.domain.library.RowType
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.feature.home.HomeState
import com.retroplay.feature.home.HomeViewModel
import com.retroplay.feature.home.genreName
import com.retroplay.feature.home.rowTitle
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.feature.profiles.ProfileAvatar
import com.retroplay.navigation.TopLevelDestination
import com.retroplay.ui.components.consoleCover
import java.io.File
import com.retroplay.core.ui.R as UiR

@Composable
fun MobileHome(nav: MobileNav, profile: Profile?, onSwitchProfile: (() -> Unit)?, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchGamesEffect(viewModel.launcher)
    val update = com.retroplay.feature.update.rememberUpdateBanner(hiltViewModel())
    MobileHomeContent(
        update = update,
        onMedia = nav::media,
        state = state,
        profile = profile,
        onSwitchProfile = onSwitchProfile,
        onPlay = viewModel::play,
        onOpenGame = { nav.game(it.id) },
        onOpenConsole = { nav.console(it.id.value) },
        onAddGames = nav::library,
        onBrowseConsoles = { nav.tab(TopLevelDestination.CONSOLES) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeContent(
    state: HomeState,
    update: com.retroplay.feature.update.UpdateBannerModel? = null,
    onMedia: () -> Unit = {},
    profile: Profile?,
    onSwitchProfile: (() -> Unit)?,
    onPlay: (Game) -> Unit,
    onOpenGame: (Game) -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
    onAddGames: () -> Unit,
    onBrowseConsoles: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painterResource(R.drawable.logo_onzetec),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(36.dp).testTag("home_logo"),
                    )
                },
                actions = {
                    IconButton(onClick = onMedia, modifier = Modifier.testTag("home_media")) {
                        Icon(painterResource(UiR.drawable.ic_nav_media), contentDescription = stringResource(R.string.media_title))
                    }
                    IconButton(onClick = onAddGames, modifier = Modifier.testTag("home_add_games")) {
                        Icon(painterResource(UiR.drawable.ic_nav_transfer), contentDescription = stringResource(R.string.mobile_add_games))
                    }
                    if (profile != null && onSwitchProfile != null) {
                        IconButton(onClick = onSwitchProfile, modifier = Modifier.testTag("home_profile")) {
                            ProfileAvatar(profile, 30.dp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().testTag("screen_home")) {
            when {
                !state.loaded -> Unit
                state.rows.isEmpty() -> EmptyLibrary(onAddGames, onOpenConsole)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    update?.let { u -> item(key = "update") { MobileUpdateBanner(u) } }
                    state.hero?.let { hero ->
                        val system = hero.systemId?.let { SystemRegistry.byId(it) }
                        if (system != null) item(key = "hero") { HeroCard(hero, system, onPlay, onOpenGame) }
                    }
                    items(state.rows, key = { it.type.key }) { row ->
                        HomeRowSection(row, state.continueThumbs, onOpenGame, onOpenConsole)
                    }
                    item(key = "browse") {
                        TextButton(onClick = onBrowseConsoles, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.mobile_browse_consoles))
                        }
                    }
                }
            }
        }
    }
}

/** Destaque: cena do jogo de fundo, nome e "Jogar". */
@Composable
private fun HeroCard(game: Game, system: GameSystem, onPlay: (Game) -> Unit, onOpenGame: (Game) -> Unit) {
    val background = MaterialTheme.colorScheme.background
    // Em pé: 16:10; deitado (tela baixa) o destaque fica com no máximo 240 dp de altura.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
    val h = minOf(maxWidth * 0.62f, 240.dp)
    Box(
        Modifier.fillMaxWidth().height(h)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenGame(game) }
            .testTag("hero"),
    ) {
        Box(Modifier.fillMaxSize().systemGradient(system, background))
        val art = listOf(CoverType.SNAP, CoverType.TITLE, CoverType.BOXART).firstOrNull { it in game.availableCovers }
        if (art != null) {
            AsyncImage(model = CoverUrls.url(system, game, art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to background.copy(alpha = 0.95f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (game.lastPlayedAt != null) R.string.hero_continue else R.string.hero_featured),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(game.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("hero_title"))
            Caption(listOfNotNull(system.displayName, game.year, game.genre?.let { genreName(it) }).joinToString(" · "))
            Spacer(Modifier.height(4.dp))
            Button(onClick = { onPlay(game) }, modifier = Modifier.testTag("hero_play")) {
                Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play))
            }
        }
    }
    }
}

@Composable
private fun HomeRowSection(row: HomeRow, thumbs: Map<Long, String>, onOpenGame: (Game) -> Unit, onOpenConsole: (GameSystem) -> Unit) {
    val rowSystem = (row.type as? RowType.BySystem)?.let { SystemRegistry.byId(it.system) }
    Column(Modifier.testTag("row_${row.type.key}")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(rowTitle(row.type, rowSystem), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (rowSystem != null) {
                TextButton(onClick = { onOpenConsole(rowSystem) }, modifier = Modifier.testTag("see_all_${rowSystem.id.value}")) {
                    Text(stringResource(R.string.see_all))
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.games, key = { it.id }) { game ->
                val system = game.systemId?.let { SystemRegistry.byId(it) } ?: return@items
                if (row.type == RowType.ContinuePlaying) {
                    ContinueTile(game, system, thumbs[game.id]) { onOpenGame(game) }
                } else {
                    GameTile(game, system, onClick = { onOpenGame(game) }, width = 112.dp)
                }
            }
        }
    }
}

/** "Continuar jogando": miniatura do último save (16:9) e o nome. */
@Composable
private fun ContinueTile(game: Game, system: GameSystem, thumbnail: String?, onClick: () -> Unit) {
    Column(Modifier.width(200.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).testTag("continue_${game.id}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).systemGradient(system, MaterialTheme.colorScheme.background)) {
            val model: Any? = thumbnail?.let { File(it) } ?: CoverUrls.url(system, game, CoverType.SNAP.takeIf { it in game.availableCovers } ?: game.cover)
            if (model != null) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(UiR.drawable.ic_play), null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Text(game.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Caption(system.displayName)
    }
}

/** Biblioteca vazia: como colocar jogos no celular e os consoles aceitos. */
@Composable
private fun EmptyLibrary(onAddGames: () -> Unit, onOpenConsole: (GameSystem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("home_empty"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            EmptyState(
                icon = UiR.drawable.ic_nav_transfer,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.mobile_empty_body),
                action = stringResource(R.string.mobile_add_games),
                onAction = onAddGames,
                tag = "home_empty_state",
            )
        }
        item { Text(stringResource(R.string.home_supported_systems), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(SystemRegistry.all, key = { it.id.value }) { system ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenConsole(system) }) {
                        ConsoleArt(system, Modifier.fillMaxWidth().aspectRatio(3f / 2f).clip(RoundedCornerShape(12.dp)))
                        Text(system.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/** Ilustração do console (ou o degradê com o nome, se não houver). */
@Composable
fun ConsoleArt(system: GameSystem, modifier: Modifier = Modifier) {
    val cover = consoleCover(system)
    Box(modifier.systemGradient(system, MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        if (cover != null) {
            Image(painterResource(cover), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(system.shortName, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Aviso de versão nova no topo da Início do celular. */
@Composable
private fun MobileUpdateBanner(model: com.retroplay.feature.update.UpdateBannerModel) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp).testTag("update_banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("⬆ " + model.title, style = MaterialTheme.typography.titleSmall)
            Caption(model.body)
            model.progress?.let { p ->
                androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        if (model.action != null) {
            Spacer(Modifier.width(10.dp))
            Button(onClick = { com.retroplay.ui.feel.UiFeel.select(); model.onAction() }, modifier = Modifier.testTag("update_action")) { Text(model.action) }
        }
    }
}
