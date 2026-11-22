package com.retroplay.fakes

import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.core.InstalledCore
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.data.di.CoreRepositoryModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

/** Repositório de núcleos controlado pelo teste. */
class FakeCoreRepository : CoreRepository {
    val state = MutableStateFlow<CoreInstallState>(CoreInstallState.NotInstalled)
    val installRequests = mutableListOf<String>()

    override fun observe(core: CoreDescriptor): Flow<CoreInstallState> = state

    override fun install(core: CoreDescriptor) {
        installRequests += core.id
        state.value = CoreInstallState.Downloading(0, 1_600_000)
    }

    override suspend fun installed(core: CoreDescriptor): InstalledCore? =
        (state.value as? CoreInstallState.Installed)?.core

    override suspend fun remove(core: CoreDescriptor) {
        state.value = CoreInstallState.NotInstalled
    }

    fun complete(coreId: String = "snes9x2010") {
        state.value = CoreInstallState.Installed(
            InstalledCore(coreId, "armeabi-v7a", "/tmp/$coreId.so", "988262322", "abc", 1_643_020, 0),
        )
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CoreRepositoryModule::class])
object FakeCoreModule {
    @Provides
    @Singleton
    fun fake(): FakeCoreRepository = FakeCoreRepository()

    @Provides
    @Singleton
    fun repo(fake: FakeCoreRepository): CoreRepository = fake
}
