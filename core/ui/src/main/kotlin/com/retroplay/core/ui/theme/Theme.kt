package com.retroplay.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Nível de animações escolhido pelo usuário. */
enum class AnimationLevel { COMPLETAS, REDUZIDAS, DESLIGADAS }

@Immutable
data class RetroUiPrefs(
    val accent: AccentColor = AccentColor.VIOLETA,
    val oledBlack: Boolean = false,
    val animations: AnimationLevel = AnimationLevel.COMPLETAS,
)

val LocalRetroUiPrefs = staticCompositionLocalOf { RetroUiPrefs() }

@Composable
fun RetroPlayTheme(
    prefs: RetroUiPrefs = RetroUiPrefs(),
    content: @Composable () -> Unit,
) {
    val background = if (prefs.oledBlack) RetroPalette.BackgroundOled else RetroPalette.Background
    val surface = if (prefs.oledBlack) RetroPalette.SurfaceOled else RetroPalette.Surface
    val colors = darkColorScheme(
        primary = prefs.accent.color,
        onPrimary = prefs.accent.onColor,
        secondary = prefs.accent.color,
        onSecondary = prefs.accent.onColor,
        background = background,
        onBackground = RetroPalette.OnSurface,
        surface = surface,
        onSurface = RetroPalette.OnSurface,
        surfaceVariant = RetroPalette.SurfaceVariant,
        onSurfaceVariant = RetroPalette.OnSurfaceVariant,
        border = RetroPalette.Border,
        error = RetroPalette.Error,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalRetroUiPrefs provides prefs) {
        MaterialTheme(colorScheme = colors, typography = RetroTypography, content = content)
    }
}
