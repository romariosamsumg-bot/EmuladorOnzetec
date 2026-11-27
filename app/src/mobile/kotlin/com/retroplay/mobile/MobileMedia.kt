package com.retroplay.mobile

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.retroplay.R
import com.retroplay.feature.media.MediaFilter
import com.retroplay.feature.media.MediaItem
import com.retroplay.feature.media.MediaThumbnail
import com.retroplay.feature.media.MediaVideo
import com.retroplay.feature.media.MediaViewModel
import com.retroplay.feature.media.filterLabel
import com.retroplay.feature.media.formatClip
import com.retroplay.core.ui.R as UiR

/** Mídia no celular: prints e highlights em grade; toque abre em tela cheia (compartilhar/apagar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMedia(nav: MobileNav, viewModel: MediaViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var open by remember { mutableStateOf<MediaItem?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.media_title)) }, navigationIcon = { BackButton(nav) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().testTag("screen_media")) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaFilter.entries.forEach { f ->
                    FilterChip(selected = f == filter, onClick = { viewModel.setFilter(f) }, label = { Text(stringResource(filterLabel(f))) }, modifier = Modifier.testTag("media_filter_${f.name}"))
                }
            }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(UiR.drawable.ic_nav_media, stringResource(R.string.media_empty_title), stringResource(R.string.media_empty_body), tag = "media_empty")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("media_grid"),
                ) {
                    itemsIndexed(list, key = { _, m -> m.uri.toString() }) { i, item ->
                        Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = item }.testTag("media_$i")) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF101016))) {
                                MediaThumbnail(item, Modifier.fillMaxSize())
                                if (item.isVideo) {
                                    Text(
                                        "▶ " + (formatClip(item.durationMs) ?: ""),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(item.gameName, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                            Caption(DateUtils.getRelativeTimeSpanString(item.dateMs).toString())
                        }
                    }
                }
            }
        }
    }
    open?.let { item ->
        var confirm by remember(item) { mutableStateOf(false) }
        Dialog(onDismissRequest = { open = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().testTag("media_viewer")) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (item.isVideo) MediaVideo(item, Modifier.fillMaxSize())
                    else AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { open = null }, modifier = Modifier.testTag("media_close")) { Text(stringResource(R.string.media_close), color = Color.White) }
                    if (item.uri.scheme == "content") {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType(if (item.isVideo) "video/mp4" else "image/png")
                                .putExtra(Intent.EXTRA_STREAM, item.uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        }, modifier = Modifier.testTag("media_share")) { Text(stringResource(R.string.media_share), color = Color.White) }
                    }
                    TextButton(onClick = { confirm = true }, modifier = Modifier.testTag("media_delete")) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.media_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        viewModel.delete(item) { ok -> Toast.makeText(context, if (ok) R.string.media_deleted else R.string.media_delete_failed, Toast.LENGTH_SHORT).show() }
                        open = null
                    }, modifier = Modifier.testTag("media_delete_confirm")) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
