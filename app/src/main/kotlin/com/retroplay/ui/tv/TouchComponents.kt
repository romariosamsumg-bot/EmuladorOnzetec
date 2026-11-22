@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.retroplay.ui.tv

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.CardScale
import androidx.tv.material3.CardShape
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.ListItemColors
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.FilterChip as TvFilterChip
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Surface as TvSurface

/*
 * Os componentes do Compose for TV só respondem ao controle remoto (tecla OK), não ao toque.
 * Estas versões têm a mesma aparência e assinatura e também aceitam toque: o toque foca o item
 * (como mover o direcional até ele) e executa a ação (como apertar OK). Arrastar continua
 * rolando as listas normalmente.
 */

/** Toque = focar + clicar. Usado por todos os componentes abaixo. */
@Composable
fun Modifier.touchClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val latest by rememberUpdatedState(onClick)
    val focus = remember { FocusRequester() }
    return this
        .focusRequester(focus)
        .pointerInput(enabled) {
            if (enabled) detectTapGestures(onTap = {
                runCatching { focus.requestFocus() }
                latest()
            })
        }
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.colors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = TvButton(onClick = onClick, modifier = modifier.touchClick(enabled, onClick), enabled = enabled, colors = colors, contentPadding = contentPadding, content = content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = TvOutlinedButton(onClick = onClick, modifier = modifier.touchClick(enabled, onClick), enabled = enabled, content = content)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: CardShape = CardDefaults.shape(),
    colors: CardColors = CardDefaults.colors(),
    scale: CardScale = CardDefaults.scale(),
    border: CardBorder = CardDefaults.border(),
    glow: CardGlow = CardDefaults.glow(),
    content: @Composable ColumnScope.() -> Unit,
) = TvCard(onClick = onClick, modifier = modifier.touchClick(true, onClick), shape = shape, colors = colors, scale = scale, border = border, glow = glow, content = content)

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    content: @Composable BoxScope.() -> Unit,
) = TvSurface(onClick = onClick, modifier = modifier.touchClick(enabled, onClick), enabled = enabled, shape = shape, colors = colors, scale = scale, border = border, content = content)

@Composable
fun ListItem(
    selected: Boolean,
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
) = TvListItem(
    selected = selected, onClick = onClick, headlineContent = headlineContent, modifier = modifier.touchClick(enabled, onClick),
    enabled = enabled, supportingContent = supportingContent, trailingContent = trailingContent, colors = colors,
)

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = TvFilterChip(selected = selected, onClick = onClick, modifier = modifier.touchClick(true, onClick), content = content)

@Composable
fun NavigationDrawerScope.NavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = TvNavigationDrawerItem(selected = selected, onClick = onClick, leadingContent = leadingContent, modifier = modifier.touchClick(true, onClick), content = content)
