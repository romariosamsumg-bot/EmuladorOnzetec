package com.retroplay.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.retroplay.R
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.core.ui.R as UiR

/** Fase 4: teclado em grade, busca por voz e resultados em tempo real. */
@Composable
fun SearchScreen() = PlaceholderScreen(
    title = stringResource(R.string.search_title),
    body = stringResource(R.string.search_body),
    iconRes = UiR.drawable.ic_nav_search,
    testTag = "screen_search",
)
