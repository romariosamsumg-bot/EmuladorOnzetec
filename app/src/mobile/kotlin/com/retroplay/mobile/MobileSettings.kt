package com.retroplay.mobile

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.R
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.settings.AppSettings
import com.retroplay.feature.profiles.ProfileAvatar
import com.retroplay.feature.settings.GAME_CATEGORIES
import com.retroplay.feature.settings.LocalSettingValueWidth
import com.retroplay.feature.settings.LockedSettings
import com.retroplay.feature.settings.ScopeSelector
import com.retroplay.feature.settings.SettingsCategory
import com.retroplay.feature.settings.SettingsPane
import com.retroplay.feature.settings.SettingsViewModel

/**
 * Ajustes no celular: lista de categorias → página da categoria (as mesmas opções da TV, com toque).
 * Também é por aqui que se troca de perfil e se adicionam jogos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettings(nav: MobileNav, profile: Profile?, onSwitchProfile: (() -> Unit)?, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val game by viewModel.game.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(viewModel) {
        viewModel.formattedMessages.collect { (res, args) -> Toast.makeText(context, resources.getString(res, *args.toTypedArray()), Toast.LENGTH_LONG).show() }
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val snap = snapshot ?: run { Box(Modifier.fillMaxSize().testTag("screen_settings")); return }
    val pin = snap.value(AppSettings.PARENTAL_PIN)
    if (!unlocked && pin.isNotEmpty() && snap.value(AppSettings.PARENTAL_SETTINGS)) {
        LockedSettings(pin) { unlocked = true }
        return
    }
    val categories = if (game != null) GAME_CATEGORIES else SettingsCategory.entries
    val category = open?.let { name -> categories.firstOrNull { it.name == name } }
    BackHandler(enabled = category != null) { open = null }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        category?.let { stringResource(it.title) }
                            ?: game?.let { stringResource(R.string.game_settings) }
                            ?: stringResource(R.string.settings_title),
                    )
                },
                navigationIcon = {
                    if (category != null) {
                        androidx.compose.material3.IconButton(onClick = { open = null }, modifier = Modifier.testTag("settings_back")) {
                            Text("←", style = MaterialTheme.typography.titleLarge)
                        }
                    } else if (game != null) {
                        BackButton(nav)
                    }
                },
            )
        },
    ) { padding ->
        if (category == null) {
            LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("screen_settings"), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (game == null) {
                    if (profile != null && onSwitchProfile != null) {
                        item(key = "profile") {
                            ListItem(
                                headlineContent = { Text(profile.name) },
                                supportingContent = { Text(stringResource(R.string.mobile_switch_profile)) },
                                leadingContent = { ProfileAvatar(profile, 40.dp) },
                                modifier = Modifier.testTag("settings_profile").clickableRow(onSwitchProfile),
                            )
                        }
                    }
                    item(key = "add") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.mobile_add_games)) },
                            supportingContent = { Text(stringResource(R.string.mobile_add_games_desc)) },
                            modifier = Modifier.testTag("settings_add_games").clickableRow(nav::library),
                        )
                    }
                    item(key = "div") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                } else {
                    item(key = "game") { Caption(stringResource(R.string.scope_game, game!!.title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                }
                items(categories, key = { it.name }) { c ->
                    ListItem(
                        headlineContent = { Text(stringResource(c.title)) },
                        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                        modifier = Modifier.testTag("cat_${c.name}").clickableRow {
                            viewModel.selectCategory(c)
                            open = c.name
                        },
                    )
                }
            }
        } else {
            val pane = SettingsPane(
                snap = snap,
                scope = scope,
                system = viewModel.scopeSystem(),
                gameKey = viewModel.scopeGameKey(),
                vm = viewModel,
                openDialog = { dialog = it },
                closeDialog = { dialog = null },
                onOpenTransfer = nav::library,
            )
            val listState = rememberLazyListState()
            LaunchedEffect(category, scope) { listState.scrollToItem(0) }
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
                if (category.scoped) ScopeSelector(viewModel, scope, game)
                // As linhas e janelas das opções são as mesmas da TV (funcionam com toque).
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // Em pé, o valor ocupa no máximo ~35% da linha (quebra em 2 linhas) e o nome cabe.
                    val valueWidth = if (maxWidth < 600.dp) maxWidth * 0.36f else 320.dp
                    androidx.compose.runtime.CompositionLocalProvider(LocalSettingValueWidth provides valueWidth) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().testTag("settings_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 48.dp),
                        ) { with(pane) { content(category) } }
                    }
                }
            }
        }
    }
    dialog?.invoke()
}
