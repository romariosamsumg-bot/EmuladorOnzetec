package com.retroplay.fakes

import android.content.Context
import android.net.Uri
import com.retroplay.di.MediaModule
import com.retroplay.feature.media.MediaItem
import com.retroplay.feature.media.MediaLibrary
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Mídia em memória: um print e um highlight (quando [items] não é trocado). */
class FakeMediaLibrary(context: Context) : MediaLibrary(context) {
    var items = DEFAULT

    override fun list(): List<MediaItem> = items

    override fun delete(item: MediaItem): Boolean {
        items = items - item
        return true
    }

    companion object {
        val DEFAULT = listOf(
            MediaItem(Uri.parse("content://media/external/video/media/2"), "Super Mario World_1700000002000.mp4", true, 1_700_000_002_000, 1_000_000, 10_000),
            MediaItem(Uri.parse("content://media/external/images/media/1"), "Sonic The Hedgehog_1700000001000.png", false, 1_700_000_001_000, 200_000),
        )
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [MediaModule::class])
object FakeMediaModule {
    @Provides
    @Singleton
    fun media(@ApplicationContext context: Context): MediaLibrary = FakeMediaLibrary(context)
}
