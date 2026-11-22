package com.retroplay.feature.game

import android.content.Context
import android.content.Intent
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.domain.system.GameSystem

/** Abre um jogo na [GameActivity] (processo ":game"). Só passa strings entre processos. */
object GameLauncher {
    const val EXTRA_SYSTEM = "system"
    const val EXTRA_CORE = "core"
    const val EXTRA_ROM_PATH = "rom"
    const val EXTRA_TITLE = "title"
    const val EXTRA_FILE_NAME = "file_name"

    /** @param romUri caminho de arquivo ou URI content:// (pendrive/SAF). */
    fun intent(context: Context, system: GameSystem, core: CoreDescriptor, romUri: String, fileName: String, title: String): Intent =
        Intent(context, GameActivity::class.java)
            .putExtra(EXTRA_SYSTEM, system.id.value)
            .putExtra(EXTRA_CORE, core.id)
            .putExtra(EXTRA_ROM_PATH, romUri)
            .putExtra(EXTRA_FILE_NAME, fileName)
            .putExtra(EXTRA_TITLE, title)
}
