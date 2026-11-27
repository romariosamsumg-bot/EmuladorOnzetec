package com.retroplay.feature.media

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.retroplay.R
import com.retroplay.ui.components.CenteredDialog
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.ui.tv.Button
import com.retroplay.ui.tv.Card
import com.retroplay.ui.tv.OutlinedButton
import com.retroplay.core.ui.R as UiR

/** Mídia (TV): prints e highlights do jogo, em grade; OK abre em tela cheia. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaScreen(viewModel: MediaViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var open by remember { mutableStateOf<MediaItem?>(null) }
    val list = items ?: return Box(Modifier.fillMaxSize().testTag("screen_media"))
    val first = remember { FocusRequester() }
    val chipFocus = rememberInitialFocus(ready = true)
    Column(Modifier.fillMaxSize().padding(top = 32.dp).testTag("screen_media")) {
        Text(stringResource(R.string.media_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 48.dp))
        Row(Modifier.padding(horizontal = 48.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MediaFilter.entries.forEachIndexed { i, f ->
                FilterChip(
                    selected = f == filter,
                    onClick = { viewModel.setFilter(f) },
                    modifier = (if (i == 0) Modifier.focusRequester(chipFocus) else Modifier).testTag("media_filter_${f.name}"),
                ) { Text(stringResource(filterLabel(f))) }
            }
        }
        if (list.isEmpty()) {
            PlaceholderScreen(
                title = stringResource(R.string.media_empty_title),
                body = stringResource(R.string.media_empty_body),
                iconRes = UiR.drawable.ic_nav_media,
                testTag = "media_empty",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.testTag("media_grid"),
            ) {
                itemsIndexed(list, key = { _, m -> m.uri.toString() }) { i, item ->
                    Column {
                        Card(
                            onClick = { open = item },
                            modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth().aspectRatio(16f / 9f).testTag("media_${i}"),
                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = CardDefaults.scale(focusedScale = 1.06f),
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF101016))) {
                                MediaThumbnail(item, Modifier.fillMaxSize())
                                if (item.isVideo) VideoBadge(item, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                            }
                        }
                        Text(item.gameName, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            DateUtils.getRelativeTimeSpanString(item.dateMs).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    open?.let { item ->
        MediaViewer(
            item = item,
            onClose = { open = null },
            onDelete = {
                viewModel.delete(item) { ok ->
                    Toast.makeText(context, if (ok) R.string.media_deleted else R.string.media_delete_failed, Toast.LENGTH_SHORT).show()
                }
                open = null
            },
        )
    }
}

@Composable
private fun VideoBadge(item: MediaItem, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text("▶ " + (formatClip(item.durationMs) ?: stringResource(R.string.media_highlights)), color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Tela cheia: a foto ou o vídeo (em repetição), com Fechar e Apagar. Voltar fecha. */
@Composable
private fun MediaViewer(item: MediaItem, onClose: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    CenteredDialog(onDismissRequest = onClose) {
        val close = rememberInitialFocus()
        Column(
            Modifier.fillMaxSize().background(Color.Black).padding(24.dp).testTag("media_viewer"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (item.isVideo) MediaVideo(item, Modifier.fillMaxSize())
                else AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Text(item.gameName, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onClose, modifier = Modifier.focusRequester(close).testTag("media_close")) { Text(stringResource(R.string.media_close)) }
                if (!confirm) {
                    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.testTag("media_delete")) { Text(stringResource(R.string.delete)) }
                } else {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.testTag("media_delete_confirm")) { Text(stringResource(R.string.media_delete_confirm)) }
                }
            }
        }
    }
}

fun filterLabel(f: MediaFilter) = when (f) {
    MediaFilter.ALL -> R.string.media_all
    MediaFilter.PRINTS -> R.string.media_prints
    MediaFilter.HIGHLIGHTS -> R.string.media_highlights
}
