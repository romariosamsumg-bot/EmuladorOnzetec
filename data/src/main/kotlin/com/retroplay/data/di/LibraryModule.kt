package com.retroplay.data.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.retroplay.data.dat.DatDatabase
import com.retroplay.data.library.GamesFolderManager
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.data.library.RoomLibraryRepository
import com.retroplay.data.scan.FileAccess
import com.retroplay.data.scan.LibraryScanner
import com.retroplay.domain.library.DatLookup
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {

    @Provides
    @Singleton
    fun libraryDatabase(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db")
            // O jogo roda no processo ":game": mantém os dois processos sincronizados.
            .enableMultiInstanceInvalidation()
            .addMigrations(com.retroplay.data.library.MIGRATION_1_2, com.retroplay.data.library.MIGRATION_2_3, com.retroplay.data.library.MIGRATION_3_4, com.retroplay.data.library.MIGRATION_4_5)
            .addCallback(com.retroplay.data.library.CREATE_DEFAULT_PROFILE)
            .build()

    @Provides
    @Singleton
    fun datLookup(@ApplicationContext context: Context): DatLookup = DatDatabase(context)

    @Provides
    @Singleton
    fun fileAccess(@ApplicationContext context: Context, smb: com.retroplay.data.smb.SmbAccess): FileAccess = FileAccess(context.contentResolver, smb)

    @Provides
    @Singleton
    fun smbCredentials(@ApplicationContext context: Context) = com.retroplay.data.smb.SmbCredentialStore(context)

    @Provides
    @Singleton
    fun smbAccess(store: com.retroplay.data.smb.SmbCredentialStore) = com.retroplay.data.smb.SmbAccess(store::get)

    @Provides
    @Singleton
    fun identifier(catalog: SystemCatalog, dat: DatLookup): RomIdentifier = RomIdentifier(catalog, dat)

    @Provides
    @Singleton
    fun scanner(db: LibraryDatabase, files: FileAccess, identifier: RomIdentifier, catalog: SystemCatalog): LibraryScanner =
        LibraryScanner(db, files, identifier, catalog)

    @Provides
    @Singleton
    fun gamesFolder(@ApplicationContext context: Context, catalog: SystemCatalog): GamesFolderManager =
        GamesFolderManager(context, catalog)

    @Provides
    @Singleton
    fun roomLibraryRepository(
        @ApplicationContext context: Context,
        db: LibraryDatabase,
        gamesFolder: GamesFolderManager,
        files: FileAccess,
        settings: com.retroplay.domain.settings.SettingsRepository,
    ): RoomLibraryRepository = RoomLibraryRepository(
        db, WorkManager.getInstance(context), gamesFolder, files,
        activeProfile = settings.snapshots.map { it.value(com.retroplay.domain.settings.AppSettings.ACTIVE_PROFILE).toLongOrNull() ?: 1L },
    )

}

/** Separado para que os testes de UI troquem por um repositório falso. */
@Module
@InstallIn(SingletonComponent::class)
object LibraryRepositoryModule {
    @Provides
    @Singleton
    fun libraryRepository(impl: RoomLibraryRepository): LibraryRepository = impl
}

/** Separado para que os testes de UI troquem por perfis em memória. */
@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    @Provides
    @Singleton
    fun profileRepository(
        @ApplicationContext context: Context,
        db: LibraryDatabase,
        settings: com.retroplay.domain.settings.SettingsRepository,
    ): com.retroplay.domain.profile.ProfileRepository =
        com.retroplay.data.library.RoomProfileRepository(db, settings, listOf(java.io.File(context.filesDir, "states"), java.io.File(context.filesDir, "saves")))
}
