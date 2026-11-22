package com.retroplay.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.retroplay.R
import kotlinx.serialization.Serializable
import com.retroplay.core.ui.R as UiR

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object ConsolesRoute
@Serializable data object FavoritesRoute
@Serializable data object TransferRoute
@Serializable data object SettingsRoute

/** Destinos do menu lateral, na ordem em que aparecem. */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    /** Tag estável para testes de UI. */
    val testTag: String,
) {
    HOME(HomeRoute, R.string.nav_home, UiR.drawable.ic_nav_home, "nav_home"),
    SEARCH(SearchRoute, R.string.nav_search, UiR.drawable.ic_nav_search, "nav_search"),
    CONSOLES(ConsolesRoute, R.string.nav_consoles, UiR.drawable.ic_nav_consoles, "nav_consoles"),
    FAVORITES(FavoritesRoute, R.string.nav_favorites, UiR.drawable.ic_nav_favorites, "nav_favorites"),
    TRANSFER(TransferRoute, R.string.nav_transfer, UiR.drawable.ic_nav_transfer, "nav_transfer"),
    SETTINGS(SettingsRoute, R.string.nav_settings, UiR.drawable.ic_nav_settings, "nav_settings"),
}
