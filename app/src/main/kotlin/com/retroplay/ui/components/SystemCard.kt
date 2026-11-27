package com.retroplay.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import com.retroplay.domain.system.SystemRegistry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.retroplay.ui.feel.springFocusScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import com.retroplay.ui.tv.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.components.SystemIcons
import com.retroplay.domain.system.GameSystem

private val CardShape = RoundedCornerShape(12.dp)

/** Capa ilustrada do console (tools/brand/build_console_covers.py); null = ícone genérico. */
@DrawableRes
fun consoleCover(system: GameSystem): Int? = when (system.id) {
    SystemRegistry.Ids.SNES -> R.drawable.cover_snes
    SystemRegistry.Ids.MEGA_DRIVE -> R.drawable.cover_megadrive
    SystemRegistry.Ids.MASTER_SYSTEM -> R.drawable.cover_mastersystem
    SystemRegistry.Ids.ARCADE -> R.drawable.cover_arcade
    SystemRegistry.Ids.PLAYSTATION -> R.drawable.cover_psx
    SystemRegistry.Ids.NES -> R.drawable.cover_nes
    SystemRegistry.Ids.GAME_BOY -> R.drawable.cover_gb
    SystemRegistry.Ids.GAME_BOY_COLOR -> R.drawable.cover_gbc
    SystemRegistry.Ids.GBA -> R.drawable.cover_gba
    SystemRegistry.Ids.ATARI_2600 -> R.drawable.cover_atari2600
    else -> null
}

/** Card de um console, com zoom, borda luminosa e brilho ao receber foco. */
@Composable
fun SystemCard(
    system: GameSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    biosBadge: com.retroplay.feature.consoles.BiosBadge = com.retroplay.feature.consoles.BiosBadge.NONE,
    /** Quantidade de jogos do console na biblioteca (null = não mostra). */
    gameCount: Int? = null,
) {
    val accent = Color(system.accentColor)
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier.width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .springFocusScale(focused)
            .testTag("system_${system.id.value}"),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CardShape),
        ),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val cover = consoleCover(system)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    painter = painterResource(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().testTag("system_cover_${system.id.value}"),
                )
            } else {
                Icon(
                    painter = painterResource(SystemIcons.forKey(system.iconKey)),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(system.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (gameCount == null) system.releaseYear.toString()
                    else system.releaseYear.toString() + " · " + (if (gameCount == 0) stringResource(R.string.console_no_games) else androidx.compose.ui.res.pluralStringResource(R.plurals.console_games, gameCount, gameCount)),
                    modifier = Modifier.testTag("count_${system.id.value}"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (label, color) = when (biosBadge) {
                    com.retroplay.feature.consoles.BiosBadge.NONE -> null to Color.Unspecified
                    com.retroplay.feature.consoles.BiosBadge.OK -> stringResource(R.string.console_bios_ok) to Color(0xFF5BD68A)
                    com.retroplay.feature.consoles.BiosBadge.INCOMPLETE -> stringResource(R.string.console_bios_incomplete) to MaterialTheme.colorScheme.error
                    com.retroplay.feature.consoles.BiosBadge.MISSING_REQUIRED -> stringResource(R.string.console_needs_bios) to MaterialTheme.colorScheme.primary
                    com.retroplay.feature.consoles.BiosBadge.MISSING_OPTIONAL -> stringResource(R.string.console_bios_missing_neo) to MaterialTheme.colorScheme.onSurfaceVariant
                    com.retroplay.feature.consoles.BiosBadge.WRONG_VERSION -> stringResource(R.string.console_bios_wrong_version) to MaterialTheme.colorScheme.error
                    com.retroplay.feature.consoles.BiosBadge.MISSING_RECOMMENDED -> stringResource(R.string.console_bios_recommended) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, modifier = Modifier.testTag("bios_badge_${system.id.value}"))
                }
            }
        }
    }
}
