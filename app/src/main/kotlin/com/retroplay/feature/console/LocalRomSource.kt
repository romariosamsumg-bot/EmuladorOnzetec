package com.retroplay.feature.console

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Um jogo disponível para abrir (Fase 2; a biblioteca Room substitui isto na Fase 3). */
data class LocalGame(val title: String, val path: String, val isTestRom: Boolean = false)

/**
 * Fontes provisórias de jogos:
 * - ROM de teste embutida (só SNES);
 * - arquivos em `Android/data/<pacote>/files/roms/<sistema>/` (copiados via adb, USB ou "Escolher arquivo").
 */
@Singleton
class LocalRomSource @Inject constructor(@ApplicationContext private val context: Context) {

    fun romsDir(system: GameSystem): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "roms/${system.id.value}").apply { mkdirs() }

    fun list(system: GameSystem): List<LocalGame> {
        val files = romsDir(system).listFiles().orEmpty()
            .filter { it.isFile && SystemDetector.extensionOf(it.name) in system.extensions }
            .sortedBy { it.name.lowercase() }
            .map { LocalGame(title = it.nameWithoutExtension, path = it.absolutePath) }
        val test = if (system.id.value == "snes") listOf(testRom()) else emptyList()
        return test + files
    }

    private fun testRom(): LocalGame {
        val out = File(context.filesDir, "testrom/$TEST_ROM")
        if (!out.isFile) {
            out.parentFile?.mkdirs()
            context.assets.open("testrom/$TEST_ROM").use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return LocalGame(title = "", path = out.absolutePath, isTestRom = true)
    }

    /** Copia um arquivo escolhido pelo seletor do sistema para a pasta de ROMs do console. */
    fun import(system: GameSystem, uri: Uri): ImportResult {
        val name = displayName(uri) ?: return ImportResult.Failed
        if (SystemDetector.extensionOf(name) !in system.extensions) return ImportResult.WrongType
        val safe = name.substringAfterLast('/').substringAfterLast('\\')
        val dest = File(romsDir(system), safe)
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

    companion object {
        const val TEST_ROM = "retroplay-teste.sfc"
    }
}
