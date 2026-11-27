package com.retroplay.feature.console

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.ui.tv.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.retroplay.ui.tv.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.retroplay.ui.tv.OutlinedButton
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.core.ui.components.SystemIcons
import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFilter
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.GameSystem
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.ui.components.FocusMemory
import com.retroplay.ui.components.GameCard
import com.retroplay.ui.components.RestoreOrInitialFocus
import com.retroplay.ui.components.rememberFocus
import com.retroplay.ui.components.rememberFocusMemory
import com.retroplay.ui.components.requestFocusWhenReady

@Composable
fun ConsoleEntry(onOpenGame: (Game) -> Unit, viewModel: ConsoleViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val coreState by viewModel.coreState.collectAsStateWithLifecycle()
    val selectedCore by viewModel.selectedCore.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    LaunchGamesEffect(viewModel.launcher)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    ConsoleScreen(
        system = viewModel.system,
        coreName = selectedCore.displayName,
        coreState = coreState,
        games = games,
        filter = filter,
        scanning = scan is ScanState.Scanning,
        onFilter = viewModel::setFilter,
        onDownloadCore = viewModel::downloadCore,
        onOpenGame = onOpenGame,
        onPickFile = {
            try {
                picker.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_picker, Toast.LENGTH_LONG).show()
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ConsoleScreen(
    system: GameSystem,
    coreName: String,
    coreState: CoreInstallState,
    games: List<Game>?,
    filter: GameFilter,
    scanning: Boolean,
    onFilter: (GameFilter) -> Unit,
    onDownloadCore: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onPickFile: () -> Unit,
) {
    val memory = rememberFocusMemory()
    val firstItem = remember { FocusRequester() }
    RestoreOrInitialFocus(memory, firstItem, ready = games != null)
    val list = games.orEmpty()

    LazyVerticalGrid(
        columns = GridCells.Adaptive((if (system.coverAspect > 1f) 220.dp else 150.dp) * LocalRetroUiPrefs.current.cardScale),
        modifier = Modifier.fillMaxSize().testTag("screen_console_${system.id.value}"),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Header(system, coreName, coreState, memory, onDownloadCore)
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            Row(
                Modifier.focusRestorer().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameFilter.entries.forEach { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { onFilter(f) },
                        modifier = Modifier.rememberFocus(memory, "filter_${f.name}").testTag("filter_${f.name}"),
                    ) { Text(stringResource(filterLabel(f))) }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onPickFile, modifier = Modifier.rememberFocus(memory, "pick").testTag("pick_file")) {
                    Text(stringResource(R.string.pick_file))
                }
            }
        }
        items(list, key = { it.id }) { game ->
            val first = game == list.first()
            GameCard(
                system = system, game = game, title = game.title, onClick = { onOpenGame(game) },
                cardModifier = (if (first) Modifier.focusRequester(firstItem) else Modifier).rememberFocus(memory, "g${game.id}"),
                fillWidth = true,
                testTag = "game_${game.id}",
            )
        }
        if (games != null && list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Text(
                    when (filter) {
                        GameFilter.FAVORITES -> stringResource(R.string.console_no_favorites, "Jogos/${system.displayName}")
                        GameFilter.POPULAR -> stringResource(R.string.console_no_popular)
                        else -> stringResource(R.string.roms_hint, "Jogos/${system.displayName}")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp).testTag("console_empty"),
                )
            }
        }
        if (scanning) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "scanning") {
                Text(
                    stringResource(R.string.library_scanning_short),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp).testTag("console_scanning"),
                )
            }
        }
    }
    // Se não há nada para focar na grade (lista vazia), o foco vai para os filtros.
    LaunchedEffect(games) {
        if (games != null && list.isEmpty()) memory.requester("filter_${filter.name}").requestFocusWhenReady()
    }
}

@Composable
private fun Header(system: GameSystem, coreName: String, state: CoreInstallState, memory: FocusMemory, onDownload: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Color(system.accentColor)), contentAlignment = Alignment.Center) {
            Icon(painterResource(SystemIcons.forKey(system.iconKey)), null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(system.displayName, style = MaterialTheme.typography.displaySmall)
            val (message, color) = when (state) {
                CoreInstallState.NotInstalled -> stringResource(R.string.core_state_not_installed) to MaterialTheme.colorScheme.onSurfaceVariant
                CoreInstallState.Queued -> stringResource(R.string.core_queued) to MaterialTheme.colorScheme.onSurfaceVariant
                is CoreInstallState.Downloading -> (
                    if (state.totalBytes > 0) stringResource(R.string.core_downloading, size(state.downloadedBytes), size(state.totalBytes))
                    else stringResource(R.string.core_downloading_unknown, size(state.downloadedBytes))
                    ) to MaterialTheme.colorScheme.onSurface
                is CoreInstallState.Installed -> stringResource(R.string.core_installed, state.core.version.take(16), size(state.core.sizeBytes)) to
                    MaterialTheme.colorScheme.onSurfaceVariant
                is CoreInstallState.Failed -> stringResource(coreErrorMessage(state.error)) to MaterialTheme.colorScheme.error
            }
            Text(
                stringResource(R.string.console_core_label, coreName) + " · " + message,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.testTag("core_status"),
            )
            if (state is CoreInstallState.Downloading) {
                Box(Modifier.width(420.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(state.fraction ?: 0f).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        if (state == CoreInstallState.NotInstalled || state is CoreInstallState.Failed) {
            Button(
                onClick = onDownload,
                modifier = Modifier.rememberFocus(memory, "core_download").testTag("core_download"),
                colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(stringResource(if (state is CoreInstallState.Failed) R.string.core_retry else R.string.core_download))
            }
        }
    }
}

private fun filterLabel(f: GameFilter) = when (f) {
    GameFilter.AZ -> R.string.filter_az
    GameFilter.POPULAR -> R.string.filter_popular
    GameFilter.RECENT -> R.string.filter_recent
    GameFilter.MOST_PLAYED -> R.string.filter_most_played
    GameFilter.FAVORITES -> R.string.filter_favorites
}

fun coreErrorMessage(error: CoreError): Int = when (error) {
    CoreError.NO_NETWORK -> R.string.core_error_no_network
    CoreError.SERVER -> R.string.core_error_server
    CoreError.NO_SPACE -> R.string.error_no_space
    CoreError.CORRUPTED -> R.string.core_error_corrupted
    CoreError.UNSUPPORTED_ABI -> R.string.core_error_abi
    CoreError.UNKNOWN -> R.string.core_error_unknown
}
