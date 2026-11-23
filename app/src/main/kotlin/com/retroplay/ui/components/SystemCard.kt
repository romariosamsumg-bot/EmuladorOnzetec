package com.retroplay.ui.components

import androidx.compose.foundation.BorderStroke
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

/** Card de um console, com zoom, borda luminosa e brilho ao receber foco. */
@Composable
fun SystemCard(
    system: GameSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    biosBadge: com.retroplay.feature.consoles.BiosBadge = com.retroplay.feature.consoles.BiosBadge.NONE,
) {
    val accent = Color(system.accentColor)
    Card(
        onClick = onClick,
        modifier = modifier.width(220.dp).testTag("system_${system.id.value}"),
        shape = CardDefaults.shape(CardShape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CardShape),
        ),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(SystemIcons.forKey(system.iconKey)),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(system.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    system.releaseYear.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (label, color) = when (biosBadge) {
                    com.retroplay.feature.consoles.BiosBadge.NONE -> null to Color.Unspecified
                    com.retroplay.feature.consoles.BiosBadge.OK -> stringResource(R.string.console_bios_ok) to Color(0xFF5BD68A)
                    com.retroplay.feature.consoles.BiosBadge.INCOMPLETE -> stringResource(R.string.console_bios_incomplete) to MaterialTheme.colorScheme.error
                    com.retroplay.feature.consoles.BiosBadge.MISSING_REQUIRED -> stringResource(R.string.console_needs_bios) to MaterialTheme.colorScheme.primary
                    com.retroplay.feature.consoles.BiosBadge.MISSING_OPTIONAL -> stringResource(R.string.console_bios_missing_neo) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                if (label != null) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, modifier = Modifier.testTag("bios_badge_${system.id.value}"))
                }
            }
        }
    }
}
