package com.retroplay.feature.details

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.retroplay.feature.settings.PinDialog
import com.retroplay.feature.settings.TextInputDialog
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
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.library.DeleteResult
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.system.GameSystem
import com.retroplay.feature.console.coreErrorMessage
import com.retroplay.feature.home.genreName
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.ui.components.GameCover
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.core.ui.R as UiR

@Composable
fun GameDetailsEntry(onBack: () -> Unit, onOpenSettings: (Long) -> Unit = {}, viewModel: GameDetailsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deletePin by viewModel.deletePin.collectAsStateWithLifecycle()
    val download by viewModel.launcher.coreDownload.collectAsStateWithLifecycle()
    LaunchGamesEffect(viewModel.launcher)
    LaunchedEffect(viewModel) {
        viewModel.deleteResults.collect { result ->
            val msg = when (result) {
                DeleteResult.DELETED -> R.string.delete_done
                DeleteResult.NO_PERMISSION -> R.string.delete_no_permission
                DeleteResult.NOT_FOUND -> R.string.delete_not_found
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            if (result != DeleteResult.NO_PERMISSION) onBack()
        }
    }
    when (val s = state) {
        DetailsState.Loading -> Box(Modifier.fillMaxSize())
        DetailsState.Missing -> LaunchedEffect(Unit) { onBack() }
        is DetailsState.Ready -> GameDetailsScreen(
            game = s.game,
            system = s.system,
            coreDownload = download,
            latestSave = s.latestSave,
            onContinue = { s.latestSave?.let { viewModel.continueFrom(s.game, it) } },
            onPlay = { viewModel.play(s.game) },
            onToggleFavorite = { viewModel.toggleFavorite(s.game) },
            onSetCover = { viewModel.setCover(s.game, it) },
            onDelete = { viewModel.delete(s.game) },
            deletePin = deletePin,
            onOpenSettings = { onOpenSettings(s.game.id) },
            onRename = { viewModel.rename(s.game, it) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailsScreen(
    game: Game,
    system: GameSystem,
    coreDownload: CoreInstallState?,
    latestSave: SaveSlot? = null,
    onContinue: () -> Unit = {},
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetCover: (CoverType?) -> Unit,
    onDelete: () -> Unit,
    deletePin: String? = null,
    onOpenSettings: () -> Unit = {},
    onRename: (String?) -> Unit = {},
) {
    val playFocus = rememberInitialFocus()
    var showOptions by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val background = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().testTag("screen_details")) {
        val art = listOf(CoverType.SNAP, CoverType.TITLE).firstOrNull { it in game.availableCovers }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(system.accentColor).copy(alpha = 0.5f), background))))
        if (art != null) {
            AsyncImage(
                model = CoverUrls.url(system, game, art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to background, 0.6f to background.copy(alpha = 0.75f), 1f to background.copy(alpha = 0.3f))))

        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            GameCover(
                system = system,
                game = game,
                title = game.title,
                modifier = Modifier.width(240.dp).height(320.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(system.displayName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(game.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, modifier = Modifier.testTag("details_title"))
                val genre = game.genre?.let { genreName(it) }
                val players = game.players?.let { pluralStringResource(R.plurals.players, it, it) }
                Text(
                    listOfNotNull(game.year, genre, players, game.developer).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(game.region, game.fileName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (latestSave != null) {
                    Text(
                        stringResource(
                            if (latestSave.isAuto) R.string.details_save_auto else R.string.details_save_slot,
                            DateUtils.getRelativeTimeSpanString(latestSave.savedAt).toString(),
                            latestSave.slot,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("details_save"),
                    )
                }
                if (game.lastPlayedAt != null) {
                    Text(
                        stringResource(
                            R.string.details_played,
                            DateUtils.getRelativeTimeSpanString(game.lastPlayedAt!!).toString(),
                            formatDuration(game.playTimeMs),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("details_played"),
                    )
                }
                if (game.status == IdentStatus.UNIDENTIFIED) {
                    Text(stringResource(R.string.details_unidentified), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                romsetMessage(game)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("details_romset"))
                }
                Spacer(Modifier.height(12.dp))
                // Quebra de linha quando os botões não cabem (ex.: "Continuar do save" + "Jogar do início").
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (latestSave != null) {
                        // "Continuar do save" vem primeiro e recebe o foco.
                        Button(onClick = onContinue, modifier = Modifier.focusRequester(playFocus).testTag("details_continue")) {
                            Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.continue_from_save))
                        }
                        OutlinedButton(onClick = onPlay, modifier = Modifier.testTag("details_play")) {
                            Text(stringResource(R.string.play_from_start))
                        }
                    } else {
                        Button(onClick = onPlay, modifier = Modifier.focusRequester(playFocus).testTag("details_play")) {
                            Icon(painterResource(UiR.drawable.ic_play), null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.play))
                        }
                    }
                    OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.testTag("details_favorite")) {
                        Text(stringResource(if (game.isFavorite) R.string.remove_favorite else R.string.add_favorite))
                    }
                    OutlinedButton(onClick = { showOptions = true }, modifier = Modifier.testTag("details_options")) {
                        Text(stringResource(R.string.game_options))
                    }
                    OutlinedButton(onClick = { if (deletePin != null) askPin = true else confirmDelete = true }, modifier = Modifier.testTag("details_delete")) {
                        Text(stringResource(R.string.delete))
                    }
                }
                when (coreDownload) {
                    is CoreInstallState.Downloading -> Text(
                        stringResource(R.string.details_core_downloading, ((coreDownload.fraction ?: 0f) * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("details_core"),
                    )
                    CoreInstallState.Queued -> Text(stringResource(R.string.core_queued), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("details_core"))
                    is CoreInstallState.Failed -> Text(
                        stringResource(coreErrorMessage(coreDownload.error)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("details_core"),
                    )
                    else -> Unit
                }
            }
        }
    }

    if (showOptions) {
        OptionsDialog(
            game, system,
            onSetCover = { onSetCover(it); showOptions = false },
            onOpenSettings = { showOptions = false; onOpenSettings() },
            onRename = { showOptions = false; renaming = true },
            onDismiss = { showOptions = false },
        )
    }
    if (renaming) {
        RenameDialog(game.title, onSave = { onRename(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (askPin && deletePin != null) {
        PinDialog(
            stringResource(R.string.pin_enter),
            onSubmit = { (it == deletePin).also { ok -> if (ok) { askPin = false; confirmDelete = true } } },
            onDismiss = { askPin = false },
        )
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(game, onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun romsetMessage(game: Game): String? = when (game.romsetStatus) {
    RomsetStatus.INCOMPLETE -> pluralStringResource(R.plurals.romset_incomplete, game.missingFiles, game.missingFiles)
    RomsetStatus.WRONG_VERSION -> stringResource(R.string.romset_wrong_version)
    RomsetStatus.NEEDS_PARENT -> stringResource(R.string.romset_needs_parent)
    RomsetStatus.UNKNOWN_SET -> stringResource(R.string.romset_unknown)
    else -> null
}

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) stringResource(R.string.duration_minutes, minutes)
    else stringResource(R.string.duration_hours, minutes / 60, minutes % 60)
}

@Composable
private fun DialogColumn(tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun OptionsDialog(
    game: Game,
    system: GameSystem,
    onSetCover: (CoverType?) -> Unit,
    onOpenSettings: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = rememberInitialFocus()
        DialogColumn("options_dialog") {
            Text(stringResource(R.string.game_options), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.options_core, system.defaultCore.displayName),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenSettings, modifier = Modifier.focusRequester(first).testTag("options_settings")) { Text(stringResource(R.string.game_settings)) }
                Button(onClick = onRename, modifier = Modifier.testTag("options_rename")) { Text(stringResource(R.string.rename)) }
            }
            Text(stringResource(R.string.options_cover), style = MaterialTheme.typography.titleMedium)
            val choices = listOf<CoverType?>(null) + game.availableCovers
            choices.forEach { type ->
                val selected = game.coverOverride == type
                Button(
                    onClick = { onSetCover(type) },
                    modifier = Modifier.fillMaxWidth().testTag("cover_${type?.name ?: "AUTO"}"),
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text((if (selected) "✓ " else "") + stringResource(coverLabel(type)))
                }
            }
            if (game.availableCovers.isEmpty()) {
                Text(stringResource(R.string.options_no_cover), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Renomear: teclado em grade + salvar / voltar ao nome original. */
@Composable
private fun RenameDialog(current: String, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        stringResource(R.string.rename_title), current,
        onSave = { onSave(it) }, onDismiss = onDismiss, maxLength = 60,
        extraAction = stringResource(R.string.rename_reset) to { onSave(null) },
    )
}

private fun coverLabel(type: CoverType?) = when (type) {
    null -> R.string.cover_auto
    CoverType.BOXART -> R.string.cover_boxart
    CoverType.TITLE -> R.string.cover_title
    CoverType.SNAP -> R.string.cover_snap
}

@Composable
private fun ConfirmDeleteDialog(game: Game, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val cancel = rememberInitialFocus()
        DialogColumn("delete_dialog") {
            Text(stringResource(R.string.delete_title, game.title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.delete_body, game.fileName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Foco inicial em "Cancelar": evita apagar sem querer.
                OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel).testTag("delete_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = onConfirm, modifier = Modifier.testTag("delete_confirm")) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}
