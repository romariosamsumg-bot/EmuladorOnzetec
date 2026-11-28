package com.retroplay.ui.shell

import com.retroplay.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.retroplay.ui.tv.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.retroplay.feature.console.ConsoleEntry
import com.retroplay.feature.consoles.ConsolesEntry
import com.retroplay.feature.favorites.FavoritesScreen
import com.retroplay.feature.home.HomeEntry
import com.retroplay.feature.search.SearchScreen
import com.retroplay.feature.settings.SettingsEntry
import com.retroplay.navigation.GameSettingsRoute
import com.retroplay.feature.transfer.TransferScreen
import com.retroplay.navigation.ConsoleDetailRoute
import com.retroplay.navigation.ConsolesRoute
import com.retroplay.navigation.GameDetailsRoute
import com.retroplay.feature.details.GameDetailsEntry
import com.retroplay.navigation.FavoritesRoute
import com.retroplay.navigation.HomeRoute
import com.retroplay.navigation.SearchRoute
import com.retroplay.navigation.SettingsRoute
import com.retroplay.navigation.TopLevelDestination
import com.retroplay.navigation.TransferRoute

/** Altura de cada item do menu lateral (mais compacto que o padrão do Compose for TV). */
private val ItemHeight = 44.dp

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
fun RetroPlayShell(
    navController: NavHostController = rememberNavController(),
    goHomeRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    /** Perfil ativo (avatar e nome no menu lateral). */
    profile: com.retroplay.domain.profile.Profile? = null,
    /** "Trocar perfil" no menu lateral: volta para "Quem vai jogar?". */
    onSwitchProfile: (() -> Unit)? = null,
) {
    // Volta à Início limpando as telas abertas por cima (ex.: detalhes do jogo).
    androidx.compose.runtime.LaunchedEffect(navController, goHomeRequests) {
        goHomeRequests.collect { navController.navigateHomeFresh() }
    }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
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
                    // Rola se não couber (TVs com tela menor); o item focado sempre aparece.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .selectableGroup()
                    .onFocusChanged { drawerHasFocus = it.hasFocus }
                    // Ao entrar no menu (D-pad ← ou Voltar), o foco vai sempre para o item da tela atual.
                    .focusProperties { onEnter = { itemRequesters.getValue(current).requestFocus() } }
                    .focusGroup()
                    .testTag("drawer"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Marca: o mesmo símbolo nos dois estados (não "pula" ao abrir); com o menu aberto, o
                // nome e "Feito por Romário Marcelino" aparecem ao lado, sem apertar o logo.
                Row(
                    Modifier.height(56.dp).padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.logo_mark),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.width(48.dp).testTag("drawer_mark"),
                        )
                    }
                    if (open) {
                        Column(Modifier.width(196.dp).padding(start = 6.dp).testTag("drawer_logo")) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(R.string.made_by),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.testTag("drawer_credit"),
                            )
                        }
                    }
                }
                // Perfil ativo: avatar (menu recolhido) ou avatar + nome; abre "Quem vai jogar?".
                if (profile != null && onSwitchProfile != null) {
                    NavigationDrawerItem(
                        selected = false,
                        onClick = onSwitchProfile,
                        leadingContent = { com.retroplay.feature.profiles.ProfileAvatar(profile, 24.dp) },
                        modifier = Modifier.height(ItemHeight).testTag("nav_profile"),
                    ) {
                        Text(profile.name, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                TopLevelDestination.entries.forEach { dest ->
                    NavigationDrawerItem(
                        selected = dest == current,
                        onClick = {
                            when {
                                // Início sempre abre do começo: topo da tela, foco no "Jogar" do destaque.
                                dest == TopLevelDestination.HOME -> navController.navigateHomeFresh()
                                dest == current && isTopLevelScreen -> contentRequester.requestFocus()
                                else -> navController.navigateTopLevel(dest)
                            }
                        },
                        leadingContent = { Icon(painterResource(dest.iconRes), contentDescription = null) },
                        modifier = Modifier
                            .height(ItemHeight)
                            .focusRequester(itemRequesters.getValue(dest))
                            .testTag(dest.testTag),
                    ) {
                        Text(stringResource(dest.labelRes))
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Sair: pede confirmação antes de fechar o app.
                NavigationDrawerItem(
                    selected = false,
                    onClick = { confirmExit = true },
                    leadingContent = { Icon(painterResource(com.retroplay.core.ui.R.drawable.ic_nav_exit), contentDescription = null) },
                    modifier = Modifier.height(ItemHeight).testTag("nav_exit"),
                ) {
                    Text(stringResource(R.string.nav_exit))
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = CollapsedDrawerWidth)
                .focusRequester(contentRequester)
                // Grupo simples: NÃO usar focusRestorer aqui — ele devolveria o foco a um item
                // da tela anterior (que está saindo da composição) e o foco se perderia.
                .focusGroup()
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
                        onOpenGame = { navController.navigate(GameDetailsRoute(it.id)) },
                    )
                }
                composable<SearchRoute> { SearchScreen(onOpenGame = { navController.navigate(GameDetailsRoute(it.id)) }) }
                composable<ConsolesRoute> { ConsolesEntry(onOpenConsole = { navController.navigate(ConsoleDetailRoute(it.id.value)) }) }
                composable<ConsoleDetailRoute> { ConsoleEntry(onOpenGame = { navController.navigate(GameDetailsRoute(it.id)) }) }
                composable<GameDetailsRoute> {
                    GameDetailsEntry(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigate(GameSettingsRoute(it)) },
                    )
                }
                composable<FavoritesRoute> {
                    FavoritesScreen(
                        onOpenGame = { navController.navigate(GameDetailsRoute(it.id)) },
                        onBrowse = { navController.navigateTopLevel(TopLevelDestination.CONSOLES) },
                    )
                }
                composable<TransferRoute> { TransferScreen() }
                composable<com.retroplay.navigation.MediaRoute> { com.retroplay.feature.media.MediaScreen() }
                composable<SettingsRoute> { SettingsEntry(onOpenTransfer = { navController.navigateTopLevel(TopLevelDestination.TRANSFER) }) }
                composable<GameSettingsRoute> { SettingsEntry(onOpenTransfer = { navController.navigateTopLevel(TopLevelDestination.TRANSFER) }) }
            }
            // Declarado DEPOIS do NavHost para ter prioridade sobre o "voltar" padrão dele.
            // Em telas internas o NavHost cuida do Voltar (volta para a tela anterior).
            BackHandler(enabled = isTopLevelScreen) {
                when {
                    !drawerHasFocus -> itemRequesters.getValue(current).requestFocus()
                    current != TopLevelDestination.HOME -> navController.navigateHomeFresh()
                    // No menu, já na Início: pergunta antes de fechar o app.
                    else -> confirmExit = true
                }
            }
        }
    }
    if (confirmExit) {
        val activity = androidx.activity.compose.LocalActivity.current
        com.retroplay.ui.components.ExitDialog(onConfirm = { confirmExit = false; activity?.finish() }, onDismiss = { confirmExit = false })
    }
}

/** Início do começo: tira as telas abertas e recria a Início (topo, foco no destaque). */
private fun NavHostController.navigateHomeFresh() {
    navigate(HomeRoute) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = false
    }
}

private fun NavHostController.navigateTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
