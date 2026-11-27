package com.retroplay.feature.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class MediaFilter { ALL, PRINTS, HIGHLIGHTS }

@HiltViewModel
class MediaViewModel @Inject constructor(private val media: MediaLibrary) : ViewModel() {
    private val all = MutableStateFlow<List<MediaItem>?>(null)
    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()
    private val _items = MutableStateFlow<List<MediaItem>?>(null)

    /** null enquanto carrega. */
    val items: StateFlow<List<MediaItem>?> = _items.asStateFlow()

    /** Relê a lista (ao abrir a tela e ao voltar do jogo). */
    fun refresh() {
        viewModelScope.launch {
            all.value = withContext(Dispatchers.IO) { media.list() }
            apply()
        }
    }

    fun setFilter(f: MediaFilter) {
        _filter.value = f
        apply()
    }

    fun delete(item: MediaItem, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { media.delete(item) }
            if (ok) all.value = all.value?.minus(item)
            apply()
            done(ok)
        }
    }

    private fun apply() {
        _items.value = all.value?.filter {
            when (_filter.value) {
                MediaFilter.ALL -> true
                MediaFilter.PRINTS -> !it.isVideo
                MediaFilter.HIGHLIGHTS -> it.isVideo
            }
        }
    }
}

/** Miniatura: o próprio print, ou um quadro do highlight. */
@Composable
fun MediaThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    if (!item.isVideo) {
        AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        return
    }
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                    context.contentResolver.loadThumbnail(item.uri, Size(480, 270), null)
                } else {
                    MediaMetadataRetriever().run {
                        try { setDataSource(context, item.uri); getFrameAtTime(0) } finally { release() }
                    }
                }
            }.getOrNull()
        }
    }
    frame?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier) }
}

/** Reprodutor do highlight (repete sem parar). */
@Composable
fun MediaVideo(item: MediaItem, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                setOnPreparedListener { it.isLooping = true; start() }
                setVideoURI(item.uri)
            }
        },
        onRelease = { it.stopPlayback() },
        modifier = modifier,
    )
}

/** "0:10" */
fun formatClip(ms: Long?): String? = ms?.let { val s = (it + 500) / 1000; "%d:%02d".format(s / 60, s % 60) }
