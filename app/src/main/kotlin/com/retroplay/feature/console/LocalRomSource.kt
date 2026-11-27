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
    private val catalog: com.retroplay.domain.system.SystemCatalog,
) {

    suspend fun import(system: GameSystem, uri: Uri): ImportResult {
        val name = displayName(uri) ?: return ImportResult.Failed
        if (SystemDetector.extensionOf(name) !in system.extensions) return ImportResult.WrongType
        return copy(uri, name, system.id.value).also { if (it == ImportResult.Ok) library.scanNow() }
    }

    /**
     * Vários arquivos de uma vez, sem escolher o console (app de celular): a extensão decide a
     * subpasta quando só um console a aceita; .zip, .bin etc. vão para "Outros" e a biblioteca
     * identifica pelo conteúdo. Retorna quantos foram copiados.
     */
    suspend fun importMany(uris: List<Uri>): ImportSummary {
        var ok = 0
        var wrong = 0
        var failed: ImportResult? = null
        for (uri in uris) {
            val name = displayName(uri)
            val folder = name?.let { folderFor(it) }
            when {
                name == null -> failed = ImportResult.Failed
                folder == null -> wrong++
                else -> when (val r = copy(uri, name, folder)) {
                    ImportResult.Ok -> ok++
                    else -> failed = r
                }
            }
        }
        if (ok > 0) library.scanNow()
        return ImportSummary(ok, wrong, failed)
    }

    private fun folderFor(name: String): String? = when (val m = SystemDetector(catalog).matchByFileName(name)) {
        is com.retroplay.domain.system.ExtensionMatch.Unique -> m.system.id.value
        is com.retroplay.domain.system.ExtensionMatch.Ambiguous -> OTHER_FOLDER
        com.retroplay.domain.system.ExtensionMatch.Unsupported -> null
    }

    private suspend fun copy(uri: Uri, name: String, folder: String): ImportResult {
        val appDir = File(library.ensureDefaultFolders().appPath, folder).apply { mkdirs() }
        val dest = File(appDir, name.substringAfterLast('/').substringAfterLast('\\'))
        return runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
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

    /** [copied] arquivos copiados, [unsupported] de tipo desconhecido, [error] o último erro (se houve). */
    data class ImportSummary(val copied: Int, val unsupported: Int, val error: ImportResult?)

    private companion object {
        const val OTHER_FOLDER = "outros"
    }
}
