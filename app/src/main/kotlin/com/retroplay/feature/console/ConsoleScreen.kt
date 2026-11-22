package com.retroplay.feature.console

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.components.SystemIcons
import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.system.GameSystem
import com.retroplay.feature.game.GameLauncher
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.core.ui.R as UiR

@Composable
fun ConsoleEntry(viewModel: ConsoleViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val coreState by viewModel.coreState.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val testRomTitle = stringResource(R.string.test_rom_title)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ConsoleEvent.Launch -> context.startActivity(
                    GameLauncher.intent(
                        context, viewModel.system, viewModel.core, event.game.path,
                        if (event.game.isTestRom) testRomTitle else event.game.title,
                    ),
                )
                is ConsoleEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    ConsoleScreen(
        system = viewModel.system,
        coreName = viewModel.core.displayName,
        coreState = coreState,
        games = games,
        romsFolder = LocalRomSourcePath.describe(viewModel.system),
        onDownloadCore = viewModel::downloadCore,
        onPlay = viewModel::play,
        onPickFile = {
            try {
                picker.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_picker, Toast.LENGTH_LONG).show()
            }
        },
    )
}

/** Caminho amigável da pasta de ROMs, para a dica na tela. */
object LocalRomSourcePath {
    fun describe(system: GameSystem) = "Android/data/com.retroplay.tv/files/roms/${system.id.value}"
}

@Composable
fun ConsoleScreen(
    system: GameSystem,
    coreName: String,
    coreState: CoreInstallState,
    games: List<LocalGame>?,
    romsFolder: String,
    onDownloadCore: () -> Unit,
    onPlay: (LocalGame) -> Unit,
    onPickFile: () -> Unit,
) {
    val firstGameFocus = rememberInitialFocus(ready = games != null)
    val downloadFocus = remember { FocusRequester() }
    val showDownload = coreState == CoreInstallState.NotInstalled || coreState is CoreInstallState.Failed
    // ← nos jogos vai para "Baixar núcleo" (quando existe) em vez do menu lateral.
    val toDownload = Modifier.focusProperties { left = if (showDownload) downloadFocus else FocusRequester.Default }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp).testTag("screen_console_${system.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        // Coluna esquerda: console e núcleo
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(Color(system.accentColor)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(SystemIcons.forKey(system.iconKey)), null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Text(system.displayName, style = MaterialTheme.typography.displaySmall)
            CorePanel(
                coreName, coreState, onDownloadCore,
                buttonModifier = Modifier.focusRequester(downloadFocus).focusProperties { right = firstGameFocus },
            )
        }
        // Coluna direita: jogos
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.console_games), style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).testTag("console_games"),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(games.orEmpty(), key = { _, g -> g.path }) { index, game ->
                    GameRow(
                        title = if (game.isTestRom) stringResource(R.string.test_rom_title) else game.title,
                        subtitle = if (game.isTestRom) stringResource(R.string.test_rom_subtitle) else game.path.substringAfterLast('/'),
                        iconRes = UiR.drawable.ic_play,
                        onClick = { onPlay(game) },
                        modifier = (if (index == 0) Modifier.focusRequester(firstGameFocus) else Modifier)
                            .then(toDownload)
                            .testTag(if (game.isTestRom) "game_testrom" else "game_$index"),
                    )
                }
                item(key = "pick") {
                    GameRow(
                        title = stringResource(R.string.pick_file),
                        subtitle = stringResource(R.string.pick_file_subtitle),
                        iconRes = UiR.drawable.ic_nav_transfer,
                        onClick = onPickFile,
                        modifier = (if (games.isNullOrEmpty()) Modifier.focusRequester(firstGameFocus) else Modifier)
                            .then(toDownload)
                            .testTag("pick_file"),
                    )
                }
            }
            Text(
                stringResource(R.string.roms_hint, romsFolder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CorePanel(
    coreName: String,
    state: CoreInstallState,
    onDownload: () -> Unit,
    buttonModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
            .testTag("core_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.console_core_label, coreName), style = MaterialTheme.typography.titleMedium)
        val (message, color) = when (state) {
            CoreInstallState.NotInstalled -> stringResource(R.string.core_state_not_installed) to MaterialTheme.colorScheme.onSurfaceVariant
            CoreInstallState.Queued -> stringResource(R.string.core_queued) to MaterialTheme.colorScheme.onSurfaceVariant
            is CoreInstallState.Downloading -> (
                if (state.totalBytes > 0) stringResource(R.string.core_downloading, size(state.downloadedBytes), size(state.totalBytes))
                else stringResource(R.string.core_downloading_unknown, size(state.downloadedBytes))
                ) to MaterialTheme.colorScheme.onSurface
            is CoreInstallState.Installed -> stringResource(R.string.core_installed, state.core.version.take(16), size(state.core.sizeBytes)) to
                MaterialTheme.colorScheme.primary
            is CoreInstallState.Failed -> stringResource(coreErrorMessage(state.error)) to MaterialTheme.colorScheme.error
        }
        Text(message, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.testTag("core_status"))
        if (state is CoreInstallState.Downloading || state == CoreInstallState.Queued) {
            ProgressBar((state as? CoreInstallState.Downloading)?.fraction ?: 0f)
        }
        if (state == CoreInstallState.NotInstalled || state is CoreInstallState.Failed) {
            Button(
                onClick = onDownload,
                modifier = buttonModifier.testTag("core_download"),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(if (state is CoreInstallState.Failed) R.string.core_retry else R.string.core_download))
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

fun coreErrorMessage(error: CoreError): Int = when (error) {
    CoreError.NO_NETWORK -> R.string.core_error_no_network
    CoreError.SERVER -> R.string.core_error_server
    CoreError.NO_SPACE -> R.string.error_no_space
    CoreError.CORRUPTED -> R.string.core_error_corrupted
    CoreError.UNSUPPORTED_ABI -> R.string.core_error_abi
    CoreError.UNKNOWN -> R.string.core_error_unknown
}

@Composable
private fun GameRow(title: String, subtitle: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(min = 520.dp, max = 720.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(iconRes), null, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}
