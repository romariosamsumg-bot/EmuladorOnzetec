package com.retroplay.data.settings

import java.io.File

/** Espaço usado por categoria (Configurações → Armazenamento). */
data class StorageReport(val games: Long, val saves: Long, val covers: Long, val cores: Long, val cache: Long)

object StorageUsage {
    fun sizeOf(f: File?): Long = when {
        f == null || !f.exists() -> 0
        f.isFile -> f.length()
        else -> f.listFiles()?.sumOf { sizeOf(it) } ?: 0
    }

    fun report(filesDir: File, cacheDir: File, gamesBytes: Long): StorageReport = StorageReport(
        games = gamesBytes,
        saves = sizeOf(File(filesDir, "states")) + sizeOf(File(filesDir, "saves")),
        covers = sizeOf(File(cacheDir, "covers")),
        cores = sizeOf(File(filesDir, "cores")),
        cache = sizeOf(File(cacheDir, "rom-run")),
    )
}
