package com.retroplay.mobile

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.R
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.library.GameFilter
import com.retroplay.domain.library.ScanState
import com.retroplay.feature.console.ConsoleViewModel
import com.retroplay.feature.console.coreErrorMessage
import com.retroplay.feature.consoles.BiosBadge
import com.retroplay.feature.consoles.ConsolesViewModel
import com.retroplay.feature.consoles.biosBadge
import com.retroplay.feature.play.LaunchGamesEffect
import com.retroplay.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileConsoles(nav: MobileNav, viewModel: ConsolesViewModel = hiltViewModel()) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val bios by viewModel.bios.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.consoles_title)) }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.padding(padding).fillMaxSize().testTag("screen_consoles"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(viewModel.systems, key = { it.id.value }) { system ->
                val count = counts[system.id] ?: 0
                val badge = biosBadge(system, bios)
                Card(
                    onClick = { nav.console(system.id.value) },
                    modifier = Modifier.testTag("system_${system.id.value}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ConsoleArt(system, Modifier.fillMaxWidth().aspectRatio(3f / 2f))
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(system.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Caption(
                            if (count == 0) stringResource(R.string.console_no_games) else pluralStringResource(R.plurals.console_games, count, count),
                            modifier = Modifier.testTag("count_${system.id.value}"),
                        )
                        if (badge == BiosBadge.MISSING_REQUIRED) {
                            Caption(stringResource(R.string.console_needs_bios), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** Página de um console: núcleo, filtros, grade de jogos e "Escolher arquivo". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileConsolePage(nav: MobileNav, viewModel: ConsoleViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val core by viewModel.selectedCore.collectAsStateWithLifecycle()
    val coreState by viewModel.coreState.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    LaunchGamesEffect(viewModel.launcher)
    LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    val system = viewModel.system
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(system.displayName) },
                navigationIcon = { BackButton(nav) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    try { picker.launch(arrayOf("*/*")) } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.no_picker, Toast.LENGTH_LONG).show()
                    }
                },
                icon = { Icon(painterResource(UiR.drawable.ic_nav_transfer), null) },
                text = { Text(stringResource(R.string.mobile_pick_file)) },
                modifier = Modifier.testTag("pick_file"),
            )
        },
    ) { padding ->
        val list = games.orEmpty()
        GameGrid(
            games = list,
            onOpen = { nav.game(it.id) },
            modifier = Modifier.padding(padding).testTag("screen_console_${system.id.value}"),
        ) {
            fullLine("header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConsoleArt(system, Modifier.fillMaxWidth().aspectRatio(3f / 1.4f).clip(RoundedCornerShape(16.dp)))
                    CoreStatus(core.displayName, coreState, viewModel::downloadCore)
                    if (scan is ScanState.Scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameFilter.entries.forEach { f ->
                            FilterChip(
                                selected = f == filter,
                                onClick = { viewModel.setFilter(f) },
                                label = { Text(stringResource(filterLabel(f))) },
                                modifier = Modifier.testTag("filter_${f.name}"),
                            )
                        }
                    }
                }
            }
            if (games != null && list.isEmpty()) {
                fullLine("empty") {
                    EmptyState(
                        icon = UiR.drawable.ic_nav_consoles,
                        title = stringResource(
                            when (filter) {
                                GameFilter.FAVORITES -> R.string.console_no_favorites
                                GameFilter.POPULAR -> R.string.console_no_popular
                                else -> R.string.mobile_console_empty
                            },
                        ),
                        body = if (filter == GameFilter.AZ) stringResource(R.string.mobile_console_empty_body, system.displayName) else null,
                        tag = "console_empty",
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreStatus(coreName: String, state: CoreInstallState, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("core_status"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.console_core_label, coreName), style = MaterialTheme.typography.labelLarge)
            val text = when (state) {
                CoreInstallState.NotInstalled -> stringResource(R.string.core_state_not_installed)
                CoreInstallState.Queued -> stringResource(R.string.core_queued)
                is CoreInstallState.Downloading -> stringResource(R.string.details_core_downloading, ((state.fraction ?: 0f) * 100).toInt())
                is CoreInstallState.Installed -> stringResource(R.string.mobile_core_ready)
                is CoreInstallState.Failed -> stringResource(coreErrorMessage(state.error))
            }
            Caption(text, color = if (state is CoreInstallState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (state is CoreInstallState.Downloading) {
                val f = state.fraction
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        if (state == CoreInstallState.NotInstalled || state is CoreInstallState.Failed) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onDownload, modifier = Modifier.testTag("core_download")) {
                Text(stringResource(if (state is CoreInstallState.Failed) R.string.core_retry else R.string.core_download))
            }
        }
    }
}

fun filterLabel(f: GameFilter) = when (f) {
    GameFilter.AZ -> R.string.filter_az
    GameFilter.POPULAR -> R.string.filter_popular
    GameFilter.RECENT -> R.string.filter_recent
    GameFilter.MOST_PLAYED -> R.string.filter_most_played
    GameFilter.FAVORITES -> R.string.filter_favorites
}

@Composable
fun BackButton(nav: MobileNav) {
    IconButton(onClick = { nav.back() }, modifier = Modifier.testTag("back")) {
        Text("←", style = MaterialTheme.typography.titleLarge)
    }
}

