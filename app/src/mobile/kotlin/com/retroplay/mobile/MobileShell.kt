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
import androidx.compose.runtime.setValue
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
        goHomeRequests.collect { MobileNav(navController).home() }
    }
    // Android 13+: pede (uma vez) a permissão de notificação, usada para avisar de versão nova.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val updatesVm: com.retroplay.feature.update.UpdateViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
        val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { updatesVm.markNotificationAsked() }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !updatesVm.notificationAsked()) {
                kotlinx.coroutines.delay(1500)
                runCatching { askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    var confirmExit by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Voltar na Início (sem telas abertas por cima) pergunta antes de fechar o app.
    val atHome = entry?.destination?.hasRoute(HomeRoute::class) == true && navController.previousBackStackEntry == null
    androidx.activity.compose.BackHandler(enabled = atHome) { confirmExit = true }
    if (confirmExit) {
        val activity = androidx.activity.compose.LocalActivity.current
        MobileExitDialog(onConfirm = { confirmExit = false; activity?.finish() }, onDismiss = { confirmExit = false })
    }
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
                    onClick = { tabFeel(); nav.tab(dest) },
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
                        onClick = { tabFeel(); nav.tab(dest) },
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
            composable<com.retroplay.navigation.MediaRoute> { MobileMedia(nav) }
            composable<SettingsRoute> { MobileSettings(nav, profile, onSwitchProfile, onExit = { confirmExit = true }) }
            composable<GameSettingsRoute> { MobileSettings(nav, profile, onSwitchProfile) }
        }
    }
    }
    }
}

/** Trocar de aba: "tic" curto e vibração leve. */
private fun tabFeel() {
    com.retroplay.ui.feel.UiFeel.play(com.retroplay.ui.feel.Sfx.MOVE)
    com.retroplay.ui.feel.UiFeel.haptic(com.retroplay.ui.feel.Haptic.TICK)
}

/** Rótulos curtos que cabem na barra do celular ("Ajustes" em vez de "Configurações"). */
private fun tabLabel(dest: TopLevelDestination): Int =
    if (dest == TopLevelDestination.SETTINGS) R.string.mobile_tab_settings else dest.labelRes

/** Atalhos de navegação usados pelas telas do celular. */
class MobileNav(private val controller: NavHostController) {
    fun tab(dest: TopLevelDestination) {
        if (dest == TopLevelDestination.HOME) return home()
        controller.navigate(dest.route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Início do começo (topo da lista), tirando as telas abertas por cima. */
    fun home() = controller.navigate(HomeRoute) {
        popUpTo(controller.graph.findStartDestination().id) { inclusive = true }
    }

    fun game(id: Long) = controller.navigate(GameDetailsRoute(id))
    fun console(systemId: String) = controller.navigate(ConsoleDetailRoute(systemId))
    fun library() = controller.navigate(TransferRoute) { launchSingleTop = true }
    fun media() = controller.navigate(com.retroplay.navigation.MediaRoute) { launchSingleTop = true }
    fun gameSettings(id: Long) = controller.navigate(GameSettingsRoute(id))
    fun back() = controller.popBackStack()
}


/** "Sair do Onzetec Games?" no celular. */
@Composable
fun MobileExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit_title)) },
        text = { Text(stringResource(R.string.exit_body)) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm, modifier = Modifier.testTag("exit_confirm")) { Text(stringResource(R.string.exit_confirm)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.testTag("exit_cancel")) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.testTag("exit_dialog"),
    )
}
