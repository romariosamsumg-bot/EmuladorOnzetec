package com.retroplay.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.ui.tv.Button
import com.retroplay.ui.tv.FilterChip
import com.retroplay.ui.tv.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.ui.components.requestFocusWhenReady

/** Categorias que fazem sentido no nível de um jogo. */
internal val GAME_CATEGORIES = listOf(SettingsCategory.VIDEO, SettingsCategory.AUDIO, SettingsCategory.CONTROLS, SettingsCategory.EMULATION)

@Composable
fun SettingsEntry(
    onOpenTransfer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val resources = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.formattedMessages.collect { (res, args) -> Toast.makeText(context, resources.getString(res, *args.toTypedArray()), Toast.LENGTH_LONG).show() }
    }
    val snap = snapshot ?: run { Box(Modifier.fillMaxSize().testTag("screen_settings")); return }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val pin = snap.value(AppSettings.PARENTAL_PIN)
    if (!unlocked && pin.isNotEmpty() && snap.value(AppSettings.PARENTAL_SETTINGS)) {
        LockedSettings(pin) { unlocked = true }
        return
    }
    SettingsScreen(viewModel, snap, onOpenTransfer)
}

@Composable
internal fun LockedSettings(pin: String, onUnlock: () -> Unit) {
    var asking by remember { mutableStateOf(true) }
    val button = rememberInitialFocus(!asking)
    Column(
        Modifier.fillMaxSize().padding(48.dp).testTag("screen_settings_locked"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.parental_locked), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { asking = true }, modifier = Modifier.focusRequester(button).testTag("unlock")) { Text(stringResource(R.string.unlock)) }
    }
    if (asking) PinDialog(stringResource(R.string.pin_enter), onSubmit = { (it == pin).also { ok -> if (ok) onUnlock() } }, onDismiss = { asking = false })
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, snap: SettingsSnapshot, onOpenTransfer: () -> Unit) {
    val category by viewModel.category.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val game by viewModel.game.collectAsStateWithLifecycle()
    val categories = if (game != null) GAME_CATEGORIES else SettingsCategory.entries
    val requesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    // → numa categoria entra sempre no topo do painel (não no item mais próximo na vertical).
    val paneEntry = remember { FocusRequester() }
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    // Foco inicial na categoria atual (a lista é curta, mas a composição é preguiçosa).
    LaunchedEffect(game == null) {
        requesters.getValue(if (category in categories) category else categories.first())
            .requestFocusWhenReady()
    }

    Row(Modifier.fillMaxSize().padding(start = 32.dp, top = 32.dp, end = 40.dp).testTag("screen_settings")) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(
                game?.let { stringResource(R.string.game_settings) } ?: stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            )
            LazyColumn(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(categories, key = { _, c -> c.name }) { _, c ->
                    ListItem(
                        selected = c == category,
                        onClick = { runCatching { paneEntry.requestFocus(FocusDirection.Enter) } },
                        modifier = Modifier
                            .focusRequester(requesters.getValue(c))
                            .focusProperties { right = paneEntry }
                            .onFocusChanged { if (it.isFocused && c != category) viewModel.selectCategory(c) }
                            .testTag("cat_${c.name}"),
                        colors = ListItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        headlineContent = { Text(stringResource(c.title)) },
                    )
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f).fillMaxHeight().focusRequester(paneEntry).focusGroup()) {
            Text(stringResource(category.title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            if (category.scoped) ScopeSelector(viewModel, scope, game)
            val pane = SettingsPane(
                snap = snap,
                scope = scope,
                system = viewModel.scopeSystem(),
                gameKey = viewModel.scopeGameKey(),
                vm = viewModel,
                openDialog = { dialog = it },
                closeDialog = { dialog = null },
                onOpenTransfer = onOpenTransfer,
            )
            val listState = rememberLazyListState()
            LaunchedEffect(category, scope) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().testTag("settings_list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp),
            ) {
                with(pane) { content(category) }
            }
        }
    }
    dialog?.invoke()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ScopeSelector(viewModel: SettingsViewModel, scope: ScopeRef, game: GameContext?) {
    if (game != null) {
        Text(
            stringResource(R.string.scope_game, game.title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp).testTag("scope_game"),
        )
        return
    }
    val options = listOf(ScopeRef.Global) + viewModel.catalog.all.map { ScopeRef.system(it.id) }
    LazyRow(Modifier.focusGroup().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options, key = { _, s -> s.prefix }) { _, s ->
            FilterChip(
                selected = s == scope,
                onClick = { viewModel.selectScope(s) },
                modifier = Modifier.testTag("scope_${s.id ?: "global"}"),
            ) {
                Text(
                    if (s.scope == Scope.GLOBAL) stringResource(R.string.scope_global)
                    else viewModel.catalog.all.first { it.id.value == s.id }.shortName,
                )
            }
        }
    }
    Text(
        stringResource(R.string.scope_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Spacer(Modifier.height(4.dp))
}
