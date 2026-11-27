package com.retroplay.mobile

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.retroplay.R
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.system.GameSystem
import com.retroplay.feature.console.coreErrorMessage
import com.retroplay.domain.library.DeleteResult
import com.retroplay.feature.details.DetailsState
import com.retroplay.feature.details.GameDetailsViewModel
import com.retroplay.feature.details.coverLabel
import com.retroplay.feature.details.formatDuration
import com.retroplay.feature.details.openTrailer
import com.retroplay.feature.details.romsetMessage
import com.retroplay.feature.home.genreName
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.feature.settings.PinDialog
import com.retroplay.ui.components.GameCover
import com.retroplay.core.ui.R as UiR

/** Detalhes do jogo em coluna: cena de fundo, capa, "Jogar", informações e opções numa folha inferior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDetails(nav: MobileNav, viewModel: GameDetailsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletePin by viewModel.deletePin.collectAsStateWithLifecycle()
    val download by viewModel.launcher.coreDownload.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    LaunchGamesEffect(viewModel.launcher)
    LaunchedEffect(viewModel) {
        viewModel.deleteResults.collect { result ->
            val msg = when (result) {
                DeleteResult.DELETED -> R.string.delete_done
                DeleteResult.NO_PERMISSION -> R.string.delete_no_permission
                DeleteResult.NOT_FOUND -> R.string.delete_not_found
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            if (result != DeleteResult.NO_PERMISSION) nav.back()
        }
    }
    when (val s = state) {
        DetailsState.Loading -> Box(Modifier.fillMaxSize())
        DetailsState.Missing -> LaunchedEffect(Unit) { nav.back() }
        is DetailsState.Ready -> {
            val game = s.game
            var options by rememberSaveable { mutableStateOf(false) }
            var renaming by rememberSaveable { mutableStateOf(false) }
            var askPin by rememberSaveable { mutableStateOf(false) }
            var confirmDelete by rememberSaveable { mutableStateOf(false) }
            DetailsBody(
                game = game,
                system = s.system,
                coreDownload = download,
                latestSaveText = s.latestSave?.let {
                    stringResource(
                        if (it.isAuto) R.string.details_save_auto else R.string.details_save_slot,
                        DateUtils.getRelativeTimeSpanString(it.savedAt).toString(), it.slot,
                    )
                },
                summary = summary,
                onBack = { nav.back() },
                onContinue = s.latestSave?.let { save -> { viewModel.continueFrom(game, save) } },
                onPlay = { viewModel.play(game) },
                onFavorite = { viewModel.toggleFavorite(game) },
                onOptions = { options = true },
                onTrailer = { openTrailer(context, game.title, s.system.displayName) },
            )
            if (options) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                // Fecha a folha com a animação e só depois abre a próxima janela.
                fun closeThen(action: () -> Unit) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { options = false; action() }
                }
                ModalBottomSheet(onDismissRequest = { options = false }, sheetState = sheetState, modifier = Modifier.testTag("options_sheet")) {
                    Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                        Text(stringResource(R.string.game_options), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                        Caption(stringResource(R.string.options_core, s.system.defaultCore.displayName), Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        SheetItem(stringResource(R.string.game_settings), "options_settings") { closeThen { nav.gameSettings(game.id) } }
                        SheetItem(stringResource(R.string.rename), "options_rename") { closeThen { renaming = true } }
                        SheetItem(stringResource(R.string.delete), "details_delete", danger = true) {
                            closeThen { if (deletePin != null) askPin = true else confirmDelete = true }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(stringResource(R.string.options_cover), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                        (listOf<CoverType?>(null) + game.availableCovers).forEach { type ->
                            ListItem(
                                headlineContent = { Text(stringResource(coverLabel(type))) },
                                leadingContent = { RadioButton(selected = game.coverOverride == type, onClick = null) },
                                modifier = Modifier.testTag("cover_${type?.name ?: "AUTO"}").clickableRow { viewModel.setCover(game, type) },
                            )
                        }
                        if (game.availableCovers.isEmpty()) Caption(stringResource(R.string.options_no_cover), Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            if (renaming) RenameDialog(game.title, onSave = { viewModel.rename(game, it); renaming = false }, onDismiss = { renaming = false })
            if (askPin && deletePin != null) {
                PinDialog(
                    stringResource(R.string.pin_enter),
                    onSubmit = { (it == deletePin).also { ok -> if (ok) { askPin = false; confirmDelete = true } } },
                    onDismiss = { askPin = false },
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.delete_title, game.title)) },
                    text = { Text(stringResource(R.string.delete_body, game.fileName)) },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete = false; viewModel.delete(game) }, modifier = Modifier.testTag("delete_confirm")) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete_cancel")) { Text(stringResource(R.string.cancel)) } },
                    modifier = Modifier.testTag("delete_dialog"),
                )
            }
        }
    }
}

@Composable
private fun DetailsBody(
    game: Game,
    system: GameSystem,
    coreDownload: CoreInstallState?,
    latestSaveText: String?,
    summary: com.retroplay.domain.library.GameSummary?,
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onOptions: () -> Unit,
    onTrailer: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen_details")) {
        // Cena do jogo no topo, com a capa sobreposta.
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Box(Modifier.fillMaxWidth().height(230.dp).systemGradient(system, background)) {
                val art = listOf(CoverType.SNAP, CoverType.TITLE).firstOrNull { it in game.availableCovers }
                if (art != null) AsyncImage(model = CoverUrls.url(system, game, art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to background.copy(alpha = 0.35f), 0.4f to Color.Transparent, 1f to background)))
            }
            Row(Modifier.statusBarsPadding().padding(4.dp).fillMaxWidth()) {
                RoundIcon("←", "back", onBack)
                Spacer(Modifier.weight(1f))
                // UI otimista: a estrela muda na hora (com som e vibração); o banco confirma depois.
                var favorite by remember(game.id, game.isFavorite) { mutableStateOf(game.isFavorite) }
                RoundIcon(if (favorite) "★" else "☆", "details_favorite") {
                    favorite = !favorite
                    com.retroplay.ui.feel.UiFeel.toggle(favorite)
                    onFavorite()
                }
                RoundIcon("⋮", "details_options", onOptions)
            }
            GameCover(
                system = system, game = game, title = game.title,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 20.dp).width(120.dp).aspectRatio(TILE_RATIO).clip(RoundedCornerShape(10.dp)),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(system.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(game.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("details_title"))
            val players = game.players?.let { pluralStringResource(R.plurals.players, it, it) }
            val meta = listOfNotNull(game.year, game.genre?.let { genreName(it) }, players, game.developer).joinToString(" · ")
            if (meta.isNotEmpty()) Caption(meta)
            Spacer(Modifier.height(4.dp))
            if (onContinue != null) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("details_continue")) {
                    Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.continue_from_save))
                }
                OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth().testTag("details_play")) { Text(stringResource(R.string.play_from_start)) }
            } else {
                Button(onClick = { com.retroplay.ui.feel.UiFeel.select(); onPlay() }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("details_play")) {
                    Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.play))
                }
            }
            when (coreDownload) {
                is CoreInstallState.Downloading -> Caption(
                    stringResource(R.string.details_core_downloading, ((coreDownload.fraction ?: 0f) * 100).toInt()),
                    Modifier.testTag("details_core"), color = MaterialTheme.colorScheme.primary,
                )
                CoreInstallState.Queued -> Caption(stringResource(R.string.core_queued), Modifier.testTag("details_core"))
                is CoreInstallState.Failed -> Caption(stringResource(coreErrorMessage(coreDownload.error)), Modifier.testTag("details_core"), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            latestSaveText?.let { Caption(it, Modifier.testTag("details_save"), color = MaterialTheme.colorScheme.primary) }
            game.lastPlayedAt?.let {
                Caption(stringResource(R.string.details_played, DateUtils.getRelativeTimeSpanString(it).toString(), formatDuration(game.playTimeMs)), Modifier.testTag("details_played"))
            }
            romsetMessage(game)?.let { Caption(it, Modifier.testTag("details_romset"), color = MaterialTheme.colorScheme.error) }
            if (game.status == IdentStatus.UNIDENTIFIED) Caption(stringResource(R.string.details_unidentified))
            if (summary != null) {
                Text(summary.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp).testTag("details_summary"))
                Caption(stringResource(R.string.details_summary_source, summary.source))
            }
            Text(stringResource(R.string.details_scenes), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).testTag("details_scenes"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onTrailer, modifier = Modifier.testTag("details_trailer")) { Text(stringResource(R.string.details_trailer)) }
                listOf(CoverType.TITLE, CoverType.SNAP).filter { it in game.availableCovers }.forEach { t ->
                    AsyncImage(
                        model = CoverUrls.url(system, game, t), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(180.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            Caption(listOfNotNull(game.region, game.fileName).joinToString(" · "), Modifier.padding(top = 8.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun RoundIcon(symbol: String, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(2.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).testTag(tag)) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

@Composable
private fun SheetItem(text: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        modifier = Modifier.testTag(tag).clickableRow { com.retroplay.ui.feel.UiFeel.select(); onClick() },
    )
}

/** Renomear com o teclado do celular; "Nome original" volta ao nome do banco de dados. */
@Composable
private fun RenameDialog(current: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(text, { text = it.take(60) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rename_field"))
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim().ifEmpty { null }) }, modifier = Modifier.testTag("rename_save")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.rename_reset)) } },
    )
}

internal fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
