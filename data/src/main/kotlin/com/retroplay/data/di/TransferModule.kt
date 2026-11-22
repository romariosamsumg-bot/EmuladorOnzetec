package com.retroplay.data.di

import android.content.Context
import com.retroplay.data.library.GamesFolderManager
import com.retroplay.data.scan.FileAccess
import com.retroplay.data.transfer.PhoneTransferServer
import com.retroplay.data.transfer.PhoneTransferService
import com.retroplay.data.transfer.TransferDestination
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.transfer.PhoneTransfer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    @Provides
    @Singleton
    fun transferDestination(
        gamesFolder: GamesFolderManager,
        identifier: RomIdentifier,
        files: FileAccess,
        catalog: SystemCatalog,
    ): TransferDestination = TransferDestination(
        // Pasta Jogos quando há permissão; senão a pasta privada do app (sempre monitorada).
        root = {
            if (gamesFolder.hasAllFilesAccess() && gamesFolder.ensureCreated()) gamesFolder.gamesDir else gamesFolder.appDir
        },
        folderFor = { id ->
            if (gamesFolder.hasAllFilesAccess()) catalog.byId(id)?.displayName ?: id.value else id.value
        },
        biosFolder = GamesFolderManager.BIOS_FOLDER,
        identifier = identifier,
        files = files,
        catalog = catalog,
    )
}

/** Separado para os testes de UI trocarem por uma versão falsa. */
@Module
@InstallIn(SingletonComponent::class)
object PhoneTransferModule {
    @Provides
    @Singleton
    fun phoneTransfer(
        @ApplicationContext context: Context,
        catalog: SystemCatalog,
        destination: TransferDestination,
        library: LibraryRepository,
    ): PhoneTransfer = PhoneTransferService(
        serverFactory = { port, pin, onFile ->
            PhoneTransferServer(port, pin, catalog, destination, page = { context.assets.open("web/index.html") }, onFileReceived = onFile)
        },
        onLibraryChanged = { library.scanNow() },
    )
}
