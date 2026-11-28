package com.retroplay.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver

/**
 * Tema Material 3 do celular com as mesmas cores do tema da TV (fundo escuro, cor do perfil).
 * Fica dentro do RetroPlayTheme: os componentes compartilhados (capas, avatares) continuam iguais.
 */
@Composable
fun MobileTheme(content: @Composable () -> Unit) {
    val tv = androidx.tv.material3.MaterialTheme.colorScheme
    val primary = tv.primary
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = tv.onPrimary,
        primaryContainer = primary.copy(alpha = 0.28f).compositeOver(tv.surface),
        onPrimaryContainer = tv.onSurface,
        secondary = primary,
        onSecondary = tv.onPrimary,
        secondaryContainer = primary.copy(alpha = 0.22f).compositeOver(tv.surface),
        onSecondaryContainer = tv.onSurface,
        tertiary = primary,
        background = tv.background,
        onBackground = tv.onBackground,
        surface = tv.background,
        onSurface = tv.onSurface,
        surfaceVariant = tv.surfaceVariant,
        onSurfaceVariant = tv.onSurfaceVariant,
        surfaceContainerLowest = tv.background,
        surfaceContainerLow = tv.surface,
        surfaceContainer = tv.surface,
        surfaceContainerHigh = tv.surfaceVariant,
        surfaceContainerHighest = tv.surfaceVariant,
        outline = tv.border,
        outlineVariant = tv.surfaceVariant,
        error = tv.error,
    )
    MaterialTheme(colorScheme = scheme) {
        // Texto claro também nas telas sem Surface (ex.: "Quem vai jogar?"): sem isto o Material 3
        // usa preto por padrão, que some no fundo escuro.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground,
            content = content,
        )
    }
}
