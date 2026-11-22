package com.retroplay.data.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.retroplay.domain.system.SystemCatalog
import java.io.File

/**
 * A pasta "Jogos" na raiz do armazenamento da TV, onde o usuário coloca as ROMs
 * (por pendrive, gerenciador de arquivos, app Downloader ou pela transferência do celular).
 *
 * No Android 11+ só é possível ler arquivos colocados ali por outros apps com a permissão
 * "acesso a todos os arquivos"; sem ela, o usuário escolhe pastas pelo seletor do sistema.
 */
class GamesFolderManager(private val context: Context, private val catalog: SystemCatalog) {

    val gamesDir: File get() = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)

    /** Pasta privada do app (sempre acessível; útil com adb). */
    val appDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "roms").apply { mkdirs() }

    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Cria Jogos/ com uma subpasta por console, BIOS/ e um LEIA-ME. Retorna false se não conseguir. */
    fun ensureCreated(): Boolean {
        val root = gamesDir
        if (!root.isDirectory && !root.mkdirs()) return false
        catalog.all.forEach { File(root, it.displayName).mkdirs() }
        File(root, BIOS_FOLDER).mkdirs()
        val readme = File(root, "LEIA-ME.txt")
        if (!readme.exists()) {
            runCatching { readme.writeText(readmeText()) }
        }
        return root.isDirectory
    }

    private fun readmeText() = buildString {
        appendLine("Retrô Play — pasta de jogos")
        appendLine()
        appendLine("Coloque os jogos na subpasta do console correspondente:")
        catalog.all.forEach { appendLine("  ${it.displayName}/  (${it.extensions.joinToString(" ") { e -> ".$e" }})") }
        appendLine("  $BIOS_FOLDER/  (neogeo.zip para o Neo Geo)")
        appendLine()
        appendLine("Arquivos .zip e .7z funcionam. Romsets de Neo Geo devem ficar compactados como estão.")
        appendLine("Depois de copiar, abra o Retrô Play: os jogos novos aparecem sozinhos.")
        appendLine()
        appendLine("O Retrô Play não inclui jogos nem BIOS. Use apenas cópias que você possui legalmente.")
    }

    companion object {
        const val FOLDER_NAME = "Jogos"
        const val BIOS_FOLDER = "BIOS"
    }
}
