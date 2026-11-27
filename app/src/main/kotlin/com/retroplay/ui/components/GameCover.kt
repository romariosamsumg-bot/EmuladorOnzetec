package com.retroplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.retroplay.core.ui.components.SystemIcons
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.GameSystem

/**
 * Capa do jogo. Por baixo fica sempre um card gerado (cor + ícone do console + nome);
 * se a capa do libretro-thumbnails carregar, ela cobre o card. Sem capa → só o card gerado.
 * A Coil reduz a imagem ao tamanho do componente (nunca decodifica a imagem inteira).
 */
@Composable
fun GameCover(
    system: GameSystem,
    game: Game?,
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Recebe a proporção (largura/altura) da capa carregada. */
    onAspect: ((Float) -> Unit)? = null,
) {
    val url = game?.let { CoverUrls.url(system, it) }
    val context = LocalPlatformContext.current
    val loader = SingletonImageLoader.get(context)
    // Tamanho fixo de decodificação: a chave do cache não muda com o tamanho do card (que muda com
    // o foco, o formato da capa e a tela), então voltar a uma tela mostra a capa na hora, da memória.
    val request = remember(url) {
        url?.let { ImageRequest.Builder(context).data(it).size(COVER_DECODE_PX).memoryCacheKey(it).placeholderMemoryCacheKey(it).build() }
    }
    // Já está na memória? Então nem desenha o card provisório (evita o "pisca" a cada visita).
    var loaded by remember(url) { mutableStateOf(url != null && loader.memoryCache?.get(MemoryCache.Key(url)) != null) }
    Box(modifier.background(Color(0xFF0E0E14))) {
        if (!loaded) GeneratedCover(system, title, compact, Modifier.fillMaxSize())
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                // Inteira, sem cortar: o card já tem a proporção do console; sobras ficam escuras.
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    loaded = true
                    val size = state.painter.intrinsicSize
                    if (onAspect != null && size.width > 0f && size.height > 0f) onAspect(size.width / size.height)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun GeneratedCover(system: GameSystem, title: String, compact: Boolean, modifier: Modifier = Modifier) {
    val accent = Color(system.accentColor)
    Box(
        modifier = modifier.background(Brush.verticalGradient(listOf(accent, Color(0xFF15151C)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(SystemIcons.forKey(system.iconKey)),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(if (compact) 28.dp else 56.dp).alpha(0.35f).align(Alignment.TopCenter).padding(top = 8.dp),
        )
        if (!compact) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/** Lado maior (px) em que as capas são decodificadas: nítido até no card grande focado. */
private const val COVER_DECODE_PX = 480
