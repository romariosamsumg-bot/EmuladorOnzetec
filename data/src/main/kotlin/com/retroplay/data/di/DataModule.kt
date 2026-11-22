package com.retroplay.data.di

import android.content.Context
import androidx.work.WorkManager
import com.retroplay.core.common.AbiDetector
import com.retroplay.core.common.DefaultDispatcherProvider
import com.retroplay.core.common.DispatcherProvider
import com.retroplay.data.core.CoreInstaller
import com.retroplay.data.core.CoreStorage
import com.retroplay.data.core.WorkManagerCoreRepository
import com.retroplay.data.rom.RomPreparer
import com.retroplay.domain.core.CoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun coreStorage(@ApplicationContext context: Context): CoreStorage =
        CoreStorage(File(context.filesDir, "cores"))

    @Provides
    @Singleton
    fun coreInstaller(client: OkHttpClient, storage: CoreStorage): CoreInstaller = CoreInstaller(client, storage)

    @Provides
    @Singleton
    fun romPreparer(@ApplicationContext context: Context, files: com.retroplay.data.scan.FileAccess): RomPreparer =
        RomPreparer(context.cacheDir, files)
}

/** Separado para que os testes possam trocar por um repositório falso. */
@Module
@InstallIn(SingletonComponent::class)
object CoreRepositoryModule {
    @Provides
    @Singleton
    fun coreRepository(
        @ApplicationContext context: Context,
        storage: CoreStorage,
        dispatchers: DispatcherProvider,
    ): CoreRepository = WorkManagerCoreRepository(
        workManager = WorkManager.getInstance(context),
        storage = storage,
        abi = AbiDetector.detect()?.abiName,
        dispatchers = dispatchers,
    )
}
