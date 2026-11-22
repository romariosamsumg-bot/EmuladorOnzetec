package com.retroplay

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class RetroPlayApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttp: Lazy<OkHttpClient>

    // WorkManager é inicializado sob demanda (inicializador padrão removido no manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    /** Capas: cache em disco limitado a 200 MB e memória pequena (a TV tem só 2 GB). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = buildImageLoader(context, cacheDir) { okHttp.get() }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Libera as capas da memória quando o sistema pede (ex.: ao entrar num jogo).
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }

    companion object {
        const val COVER_CACHE_BYTES = 200L * 1024 * 1024

        fun buildImageLoader(context: PlatformContext, cacheDir: java.io.File, okHttp: () -> OkHttpClient): ImageLoader =
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp() })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("covers"))
                        .maxSizeBytes(COVER_CACHE_BYTES)
                        .build()
                }
                .crossfade(150)
                .build()
    }
}
