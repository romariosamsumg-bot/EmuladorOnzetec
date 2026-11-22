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
            .addMigrations(com.retroplay.data.library.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun datLookup(@ApplicationContext context: Context): DatLookup = DatDatabase(context)

    @Provides
    @Singleton
    fun fileAccess(@ApplicationContext context: Context): FileAccess = FileAccess(context.contentResolver)

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
    ): RoomLibraryRepository = RoomLibraryRepository(db, WorkManager.getInstance(context), gamesFolder, files)
}

/** Separado para que os testes de UI troquem por um repositório falso. */
@Module
@InstallIn(SingletonComponent::class)
object LibraryRepositoryModule {
    @Provides
    @Singleton
    fun libraryRepository(impl: RoomLibraryRepository): LibraryRepository = impl
}
