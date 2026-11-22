package com.retroplay.data.scan

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.retroplay.data.library.GamesFolderManager
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/**
 * "Copiar para a TV": copia os jogos de uma pasta (ex.: pendrive) para Jogos/<console>
 * (ou para a pasta do app sem permissão), arquivo por arquivo, checando o espaço livre.
 * Ao terminar sem erros, a pasta de origem sai da biblioteca e uma varredura é feita.
 */
@HiltWorker
class CopyFolderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: LibraryDatabase,
    private val files: FileAccess,
    private val gamesFolder: GamesFolderManager,
    private val catalog: SystemCatalog,
    private val library: LibraryRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folderId = inputData.getLong(KEY_FOLDER, -1)
        val games = db.games().byFolder(folderId).filter { it.system != null }
        val useGamesDir = gamesFolder.hasAllFilesAccess() && gamesFolder.ensureCreated()
        var copied = 0
        for ((index, game) in games.withIndex()) {
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf(KEY_DONE to index, KEY_TOTAL to games.size, KEY_CURRENT to game.fileName))
            val system = catalog.byId(SystemId(game.system!!)) ?: continue
            val dir = if (useGamesDir) File(gamesFolder.gamesDir, system.displayName) else File(gamesFolder.appDir, system.id.value)
            dir.mkdirs()
            val dest = File(dir, game.fileName)
            if (dest.isFile && dest.length() == game.sizeBytes) { copied++; continue }
            if (dir.usableSpace < game.sizeBytes + MARGIN) {
                return Result.failure(workDataOf(KEY_ERROR to ERROR_NO_SPACE, KEY_DONE to copied))
            }
            val tmp = File(dir, ".${game.fileName}.part")
            try {
                files.openStream(game.uri).use { input -> tmp.outputStream().use { input.copyTo(it, 256 * 1024) } }
                if (!tmp.renameTo(dest)) { dest.delete(); tmp.renameTo(dest) }
                copied++
            } catch (e: IOException) {
                tmp.delete()
                return Result.failure(workDataOf(KEY_ERROR to ERROR_IO, KEY_DONE to copied))
            }
        }
        library.removeFolder(folderId)
        library.scanNow()
        return Result.success(workDataOf(KEY_DONE to copied))
    }

    companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        const val KEY_ERROR = "error"
        const val ERROR_NO_SPACE = "no_space"
        const val ERROR_IO = "io"
        const val UNIQUE_NAME = "copy-folder"
        private const val MARGIN = 50L * 1024 * 1024
    }
}
