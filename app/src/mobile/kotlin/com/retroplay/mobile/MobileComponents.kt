package com.retroplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.GameCover
import com.retroplay.ui.feel.UiFeel
import com.retroplay.ui.feel.pressBounce

/** Proporção única das capas nas grades e fileiras do celular (3:4); a capa inteira cabe dentro. */
const val TILE_RATIO = 3f / 4f

/** Capa + nome do jogo (toque abre os detalhes). */
@Composable
fun GameTile(game: Game, system: GameSystem, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp? = null) {
    // Toque: afunda e volta com mola, com som e vibração de seleção.
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        (if (width != null) modifier.width(width) else modifier)
            .pressBounce(interaction)
            .clip(RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current) { UiFeel.select(); onClick() }
            .testTag("game_${game.id}"),
    ) {
        GameCover(
            system = system,
            game = game,
            title = game.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(TILE_RATIO).clip(RoundedCornerShape(10.dp)),
        )
        Text(
            game.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

/** Grade de jogos que se adapta à largura (3 colunas num celular em pé, mais deitado/tablet). */
@Composable
fun GameGrid(
    games: List<Game>,
    catalog: SystemCatalog = SystemRegistry,
    onOpen: (Game) -> Unit,
    modifier: Modifier = Modifier,
    header: (LazyGridScope.() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize().testTag("game_grid"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        header?.invoke(this)
        items(games, key = { it.id }) { game ->
            val system = game.systemId?.let { catalog.byId(it) } ?: return@items
            GameTile(game, system, onClick = { onOpen(game) })
        }
    }
}

/** Item da grade que ocupa a linha toda. */
fun LazyGridScope.fullLine(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** Mensagem de tela vazia com ícone, texto e um botão opcional. */
@Composable
fun EmptyState(icon: Int, title: String, body: String?, action: String? = null, onAction: () -> Unit = {}, tag: String = "empty") {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, modifier = Modifier.testTag("${tag}_action")) { Text(action) }
        }
    }
}

/** Fundo em degradê na cor do console (atrás de capas e banners). */
fun Modifier.systemGradient(system: GameSystem, background: Color): Modifier =
    background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(system.accentColor).copy(alpha = 0.55f), background)))

/** Texto secundário curto. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

