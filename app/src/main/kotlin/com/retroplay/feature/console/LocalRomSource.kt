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
 * ROM de teste embutida e importação de um arquivo avulso (seletor do sistema) para a
 * pasta privada do app — que é uma das pastas monitoradas pela biblioteca.
 */
@Singleton
class LocalRomSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) {

    fun testRom(): File {
        val out = File(context.filesDir, "testrom/$TEST_ROM")
        if (!out.isFile) {
            out.parentFile?.mkdirs()
            context.assets.open("testrom/$TEST_ROM").use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return out
    }

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

    companion object {
        const val TEST_ROM = "retroplay-teste.sfc"
    }
}
