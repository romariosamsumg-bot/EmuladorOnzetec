package com.retroplay.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.retroplay.feature.console.ConsoleEntry
import com.retroplay.feature.consoles.ConsolesEntry
import com.retroplay.feature.favorites.FavoritesScreen
import com.retroplay.feature.home.HomeEntry
import com.retroplay.feature.search.SearchScreen
import com.retroplay.feature.settings.SettingsScreen
import com.retroplay.feature.transfer.TransferScreen
import com.retroplay.navigation.ConsoleDetailRoute
import com.retroplay.navigation.ConsolesRoute
import com.retroplay.navigation.FavoritesRoute
import com.retroplay.navigation.HomeRoute
import com.retroplay.navigation.SearchRoute
import com.retroplay.navigation.SettingsRoute
import com.retroplay.navigation.TopLevelDestination
import com.retroplay.navigation.TransferRoute

/** Largura do menu recolhido; o conteúdo começa depois dela. */
private val CollapsedDrawerWidth = 88.dp

/**
 * Estrutura principal: menu lateral recolhível + conteúdo.
 *
 * Regras de foco/Voltar:
 * - Cada tela define seu foco inicial ao abrir.
 * - Voltar com foco no conteúdo → leva o foco ao item selecionado do menu.
 * - Voltar com foco no menu fora do Início → vai para o Início.
 * - Voltar com foco no menu no Início → sai do app.
 */
@Composable
fun RetroPlayShell(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val topLevelMatch = TopLevelDestination.entries.firstOrNull { dest ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }
    // Em telas internas (ex.: página do console) o menu mantém selecionada a seção de origem.
    var lastTopLevel by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    if (topLevelMatch != null && topLevelMatch != lastTopLevel) lastTopLevel = topLevelMatch
    val current = topLevelMatch ?: lastTopLevel
    val isTopLevelScreen = topLevelMatch != null

    val itemRequesters = remember { TopLevelDestination.entries.associateWith { FocusRequester() } }
    val contentRequester = remember { FocusRequester() }
    var drawerHasFocus by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)),
        drawerContent = { drawerValue ->
            val open = drawerValue == DrawerValue.Open
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(if (open) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .selectableGroup()
                    .onFocusChanged { drawerHasFocus = it.hasFocus }
                    // Ao entrar no menu (D-pad ← ou Voltar), o foco vai sempre para o item da tela atual.
                    .focusProperties { onEnter = { itemRequesters.getValue(current).requestFocus() } }
                    .focusGroup()
                    .testTag("drawer"),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                TopLevelDestination.entries.forEach { dest ->
                    NavigationDrawerItem(
                        selected = dest == current,
                        onClick = {
                            if (dest == current && isTopLevelScreen) contentRequester.requestFocus()
                            else navController.navigateTopLevel(dest)
                        },
                        leadingContent = { Icon(painterResource(dest.iconRes), contentDescription = null) },
                        modifier = Modifier
                            .focusRequester(itemRequesters.getValue(dest))
                            .testTag(dest.testTag),
                    ) {
                        Text(stringResource(dest.labelRes))
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = CollapsedDrawerWidth)
                .focusRequester(contentRequester)
                .focusRestorer()
                .testTag("content"),
        ) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
            ) {
                composable<HomeRoute> {
                    HomeEntry(
                        onOpenTransfer = { navController.navigateTopLevel(TopLevelDestination.TRANSFER) },
                        onOpenConsole = { navController.navigate(ConsoleDetailRoute(it.id.value)) },
                    )
                }
                composable<SearchRoute> { SearchScreen() }
                composable<ConsolesRoute> { ConsolesEntry(onOpenConsole = { navController.navigate(ConsoleDetailRoute(it.id.value)) }) }
                composable<ConsoleDetailRoute> { ConsoleEntry() }
                composable<FavoritesRoute> { FavoritesScreen() }
                composable<TransferRoute> { TransferScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
            // Declarado DEPOIS do NavHost para ter prioridade sobre o "voltar" padrão dele.
            // Em telas internas o NavHost cuida do Voltar (volta para a tela anterior).
            BackHandler(enabled = isTopLevelScreen && (!drawerHasFocus || current != TopLevelDestination.HOME)) {
                if (!drawerHasFocus) {
                    itemRequesters.getValue(current).requestFocus()
                } else {
                    navController.navigateTopLevel(TopLevelDestination.HOME)
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
