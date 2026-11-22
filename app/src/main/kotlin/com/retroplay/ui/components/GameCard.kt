package com.retroplay.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import com.retroplay.ui.tv.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.core.ui.theme.AnimationLevel
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.GameSystem
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(10.dp)

/**
 * Card de jogo em proporção de capa. Ao receber foco: cresce ~1.1x, ganha borda luminosa e
 * sombra; depois de 600 ms mostra o nome abaixo. Animações curtas (150 ms) ou nenhuma,
 * conforme a preferência de animações.
 */
@Composable
fun GameCard(
    system: GameSystem,
    game: Game?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    width: Dp = 150.dp,
    testTag: String? = null,
) {
    val animations = LocalRetroUiPrefs.current.animations
    var focused by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        showTitle = false
        if (focused) {
            if (animations != AnimationLevel.DESLIGADAS) delay(600)
            showTitle = true
        }
    }
    Column(modifier.width(width * LocalRetroUiPrefs.current.cardScale)) {
        Card(
            onClick = onClick,
            modifier = cardModifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onFocusChanged { focused = it.isFocused }
                .let { if (testTag != null) it.testTag(testTag) else it },
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = if (animations == AnimationLevel.DESLIGADAS) 1f else 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CardShape),
            ),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = MaterialTheme.colorScheme.primary, elevation = 12.dp)),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            GameCover(system, game, title, Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxWidth().height(44.dp).padding(top = 10.dp)) {
            val alpha by animateFloatAsState(
                targetValue = if (showTitle) 1f else 0f,
                animationSpec = tween(if (animations == AnimationLevel.COMPLETAS) 150 else 0),
                label = "title",
            )
            if (alpha > 0f) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(alpha).let { if (testTag != null) it.testTag("${testTag}_title") else it },
                )
            }
        }
    }
}
