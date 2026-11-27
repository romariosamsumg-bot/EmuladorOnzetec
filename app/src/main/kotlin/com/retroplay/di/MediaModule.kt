package com.retroplay.di

import android.content.Context
import com.retroplay.feature.media.MediaLibrary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides
    @Singleton
    fun mediaLibrary(@ApplicationContext context: Context): MediaLibrary = MediaLibrary(context)
}
