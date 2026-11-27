package com.retroplay.mobile

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.unit.dp
import com.retroplay.R
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retroplay.domain.profile.Profile
import com.retroplay.navigation.ConsoleDetailRoute
import com.retroplay.navigation.ConsolesRoute
import com.retroplay.navigation.FavoritesRoute
import com.retroplay.navigation.GameDetailsRoute
import com.retroplay.navigation.GameSettingsRoute
import com.retroplay.navigation.HomeRoute
import com.retroplay.navigation.SearchRoute
import com.retroplay.navigation.SettingsRoute
import com.retroplay.navigation.TopLevelDestination
import com.retroplay.navigation.TransferRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Abas da barra inferior (Transferir vira "Adicionar jogos", aberto pela Início e pelos Ajustes). */
private val TABS = listOf(
    TopLevelDestination.HOME,
    TopLevelDestination.SEARCH,
    TopLevelDestination.CONSOLES,
    TopLevelDestination.FAVORITES,
    TopLevelDestination.SETTINGS,
)

/**
 * Estrutura do app de celular: barra de navegação inferior com cinco abas; cada tela tem a
 * própria barra superior. Voltar segue a pilha normal do Android.
 */
@Composable
fun MobileShell(
    navController: NavHostController = rememberNavController(),
    goHomeRequests: Flow<Unit> = emptyFlow(),
    profile: Profile? = null,
    onSwitchProfile: (() -> Unit)? = null,
) {
    LaunchedEffect(navController, goHomeRequests) {
        goHomeRequests.collect {
            navController.navigate(HomeRoute) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val nav = MobileNav(navController)
    fun selected(dest: TopLevelDestination) = entry?.destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    // Deitado (tela larga e baixa): barra de navegação na lateral, para sobrar altura ao conteúdo.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val rail = maxWidth > maxHeight && maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
    if (rail) {
        NavigationRail(Modifier.fillMaxHeight().testTag("bottom_bar"), windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)) {
            Spacer(Modifier.weight(1f))
            TABS.forEach { dest ->
                NavigationRailItem(
                    selected = selected(dest),
                    onClick = { nav.tab(dest) },
                    icon = { Icon(painterResource(dest.iconRes), contentDescription = null) },
                    label = { Text(stringResource(tabLabel(dest)), maxLines = 1) },
                    modifier = Modifier.testTag("tab_${dest.name.lowercase()}"),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
    Scaffold(
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("mobile_shell"),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!rail) NavigationBar(Modifier.testTag("bottom_bar")) {
                TABS.forEach { dest ->
                    NavigationBarItem(
                        selected = selected(dest),
                        onClick = { nav.tab(dest) },
                        icon = { Icon(painterResource(dest.iconRes), contentDescription = null) },
                        label = { Text(stringResource(tabLabel(dest)), maxLines = 1) },
                        modifier = Modifier.testTag("tab_${dest.name.lowercase()}"),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable<HomeRoute> { MobileHome(nav, profile, onSwitchProfile) }
            composable<SearchRoute> { MobileSearch(nav) }
            composable<ConsolesRoute> { MobileConsoles(nav) }
            composable<ConsoleDetailRoute> { MobileConsolePage(nav) }
            composable<GameDetailsRoute> { MobileDetails(nav) }
            composable<FavoritesRoute> { MobileFavorites(nav) }
            composable<TransferRoute> { MobileLibrary(nav) }
            composable<SettingsRoute> { MobileSettings(nav, profile, onSwitchProfile) }
            composable<GameSettingsRoute> { MobileSettings(nav, profile, onSwitchProfile) }
        }
    }
    }
    }
}

/** Rótulos curtos que cabem na barra do celular ("Ajustes" em vez de "Configurações"). */
private fun tabLabel(dest: TopLevelDestination): Int =
    if (dest == TopLevelDestination.SETTINGS) R.string.mobile_tab_settings else dest.labelRes

/** Atalhos de navegação usados pelas telas do celular. */
class MobileNav(private val controller: NavHostController) {
    fun tab(dest: TopLevelDestination) = controller.navigate(dest.route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun game(id: Long) = controller.navigate(GameDetailsRoute(id))
    fun console(systemId: String) = controller.navigate(ConsoleDetailRoute(systemId))
    fun library() = controller.navigate(TransferRoute) { launchSingleTop = true }
    fun gameSettings(id: Long) = controller.navigate(GameSettingsRoute(id))
    fun back() = controller.popBackStack()
}

