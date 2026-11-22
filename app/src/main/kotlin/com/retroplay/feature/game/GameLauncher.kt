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

    fun intent(context: Context, system: GameSystem, core: CoreDescriptor, romPath: String, title: String): Intent =
        Intent(context, GameActivity::class.java)
            .putExtra(EXTRA_SYSTEM, system.id.value)
            .putExtra(EXTRA_CORE, core.id)
            .putExtra(EXTRA_ROM_PATH, romPath)
            .putExtra(EXTRA_TITLE, title)
}
