package com.retroplay.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.R
import com.retroplay.feature.favorites.FavoritesViewModel
import com.retroplay.feature.search.SearchViewModel
import com.retroplay.navigation.TopLevelDestination
import com.retroplay.core.ui.R as UiR

/** Busca com o teclado do celular; resultados em grade enquanto digita. */
@Composable
fun MobileSearch(nav: MobileNav, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("screen_search")) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::set,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("search_field"),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_nav_search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear, modifier = Modifier.testTag("search_clear")) { Text("✕") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        Box(Modifier.weight(1f)) {
            when {
                !state.hasLibrary -> EmptyState(
                    UiR.drawable.ic_nav_search, stringResource(R.string.search_empty_library), null,
                    action = stringResource(R.string.mobile_add_games), onAction = nav::library, tag = "search_empty",
                )
                query.isBlank() -> EmptyState(UiR.drawable.ic_nav_search, stringResource(R.string.search_start), null, tag = "search_start")
                state.query == query && state.results.isEmpty() ->
                    EmptyState(UiR.drawable.ic_nav_search, stringResource(R.string.search_no_results, query), null, tag = "search_none")
                else -> GameGrid(state.results, onOpen = { keyboard?.hide(); nav.game(it.id) }, modifier = Modifier.testTag("search_results"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFavorites(nav: MobileNav, viewModel: FavoritesViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorites_title)) }) },
    ) { padding ->
        val list = favorites
        Box(Modifier.padding(padding).fillMaxSize().testTag("screen_favorites")) {
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    UiR.drawable.ic_nav_favorites, stringResource(R.string.favorites_title), stringResource(R.string.favorites_body),
                    action = stringResource(R.string.favorites_browse), onAction = { nav.tab(TopLevelDestination.CONSOLES) }, tag = "favorites_empty",
                )
                else -> GameGrid(list, onOpen = { nav.game(it.id) })
            }
        }
    }
}
