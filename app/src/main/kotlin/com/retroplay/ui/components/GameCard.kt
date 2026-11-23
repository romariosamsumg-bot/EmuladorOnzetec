package com.retroplay.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * Card de jogo na proporção das capas do console (SNES deitado; Mega Drive, Master System e
 * Arcade em pé), então a capa aparece inteira. Nas fileiras todos os cards têm a mesma altura
 * ([fillWidth] = false); nas grades o card ocupa a largura da célula.
 * Ao receber foco: cresce ~1.1x, ganha borda e mostra o nome numa etiqueta destacada.
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
    fillWidth: Boolean = false,
    /** Grade com consoles misturados: a capa cabe numa caixa de célula × 1,3 (altura igual). */
    mixedGrid: Boolean = false,
    testTag: String? = null,
) {
    if (mixedGrid) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val boxH = maxWidth * 1.3f
            val w = if (maxWidth / boxH > system.coverAspect) boxH * system.coverAspect else maxWidth
            Box(Modifier.fillMaxWidth().height(boxH + 58.dp), contentAlignment = Alignment.BottomCenter) {
                GameCard(system, game, title, onClick, Modifier.width(w), cardModifier, width, fillWidth = true, mixedGrid = false, testTag = testTag)
            }
        }
        return
    }
    val animations = LocalRetroUiPrefs.current.animations
    val scale = LocalRetroUiPrefs.current.cardScale
    var focused by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        showTitle = false
        if (focused) {
            if (animations != AnimationLevel.DESLIGADAS) delay(200)
            showTitle = true
        }
    }
    val ratio = system.coverAspect
    // Altura comum das fileiras: a de um card em pé de [width] de largura (3:4) × 0,9.
    val rowHeight = width * scale * 1.2f
    val outer = if (fillWidth) modifier.fillMaxWidth() else modifier.width(rowHeight * ratio)
    Column(outer, horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = cardModifier
                .let { if (fillWidth) it.fillMaxWidth().aspectRatio(ratio) else it.size(rowHeight * ratio, rowHeight) }
                .onFocusChanged { focused = it.isFocused }
                .let { if (testTag != null) it.testTag(testTag) else it },
            shape = CardDefaults.shape(CardShape),
            scale = CardDefaults.scale(focusedScale = if (animations == AnimationLevel.DESLIGADAS) 1f else 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CardShape),
            ),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            GameCover(system, game, title, Modifier.fillMaxSize())
        }
        // Espaço fixo (o card crescido não cobre a etiqueta) e etiqueta só com foco.
        Box(Modifier.fillMaxWidth().height(58.dp).padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            val alpha by animateFloatAsState(
                targetValue = if (showTitle) 1f else 0f,
                animationSpec = tween(if (animations == AnimationLevel.COMPLETAS) 150 else 0),
                label = "title",
            )
            if (showTitle || alpha > 0f) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    // Transparência aplicada no desenho: o fade não recompõe o texto a cada quadro.
                    modifier = Modifier
                        .widthIn(min = 96.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .let { if (testTag != null) it.testTag("${testTag}_title") else it },
                )
            }
        }
    }
}
