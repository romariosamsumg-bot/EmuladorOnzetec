package com.retroplay.feature.favorites

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.retroplay.R
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.core.ui.R as UiR

/** Fase 4: grade de favoritos vinda da biblioteca. */
@Composable
fun FavoritesScreen() = PlaceholderScreen(
    title = stringResource(R.string.favorites_title),
    body = stringResource(R.string.favorites_body),
    iconRes = UiR.drawable.ic_nav_favorites,
    testTag = "screen_favorites",
)
