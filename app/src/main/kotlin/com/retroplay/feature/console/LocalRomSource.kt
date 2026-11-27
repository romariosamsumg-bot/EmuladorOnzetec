package com.retroplay.feature.console

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importação de um arquivo avulso (seletor do sistema) para a pasta privada do app — que é uma das pastas monitoradas pela biblioteca.
 */
@Singleton
class LocalRomSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) {

    suspend fun import(system: GameSystem, uri: Uri): ImportResult {
        val name = displayName(uri) ?: return ImportResult.Failed
        if (SystemDetector.extensionOf(name) !in system.extensions) return ImportResult.WrongType
        val appDir = File(library.ensureDefaultFolders().appPath, system.id.value).apply { mkdirs() }
        val dest = File(appDir, name.substringAfterLast('/').substringAfterLast('\\'))
        return runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
            library.scanNow()
            ImportResult.Ok
        }.getOrElse {
            dest.delete()
            if (it.message?.contains("ENOSPC") == true) ImportResult.NoSpace else ImportResult.Failed
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment

    enum class ImportResult { Ok, WrongType, NoSpace, Failed }
}
