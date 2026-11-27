package com.retroplay.data.di

import android.content.Context
import com.retroplay.data.info.WikipediaGameInfo
import com.retroplay.domain.library.GameInfoSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

/** Resumos dos jogos (Wikipédia no idioma do app, depois em inglês), com cache em disco. */
@Module
@InstallIn(SingletonComponent::class)
object InfoModule {
    @Provides
    @Singleton
    fun gameInfo(@ApplicationContext context: Context, client: OkHttpClient): GameInfoSource =
        WikipediaGameInfo(client, File(context.cacheDir, "info"), languages = {
            val lang = context.resources.configuration.locales[0].language
            listOf(if (lang in setOf("pt", "en", "es")) lang else "pt", "en")
        })
}
