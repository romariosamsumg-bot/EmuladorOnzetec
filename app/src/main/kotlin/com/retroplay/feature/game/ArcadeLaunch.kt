package com.retroplay.feature.game

import com.retroplay.data.rom.RomCompanion
import com.retroplay.domain.library.BiosStatus
import com.retroplay.domain.library.DatLookup
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.IdentificationStrategy
import com.retroplay.domain.system.GameSystem

/** Resultado da verificação antes de abrir um jogo de arcade (Neo Geo). */
sealed interface ArcadeCheck {
    /** Pode abrir. [warnings] são avisos não bloqueantes (BIOS incompleta, pouca memória). */
    data class Ready(val bios: BiosStatus?, val companions: List<RomCompanion>, val warnings: List<ArcadeWarning>) : ArcadeCheck
    data object BiosMissing : ArcadeCheck
    data class ParentMissing(val parent: String) : ArcadeCheck
}

enum class ArcadeWarning { BIOS_INCOMPLETE, LOW_MEMORY }

object ArcadeLaunch {
    /** Margem: o FBNeo precisa do romset descompactado na RAM, mais o próprio emulador. */
    const val MEMORY_FACTOR = 1.3
    const val EMULATOR_OVERHEAD = 96L * 1024 * 1024

    fun romsetName(fileName: String) = fileName.substringAfterLast('/').substringBeforeLast('.').lowercase()

    /**
     * @param library jogos do mesmo console (para achar o romset "pai" em qualquer pasta).
     * @param bios BIOS encontradas na biblioteca.
     */
    fun check(system: GameSystem, fileName: String, dat: DatLookup, library: List<Game>, bios: List<BiosStatus>): ArcadeCheck {
        if (system.identification != IdentificationStrategy.ARCADE_ROMSET) return ArcadeCheck.Ready(null, emptyList(), emptyList())
        val required = system.bios.filter { it.required }
        val found = bios.filter { it.system == system.id }
            .sortedByDescending { it.complete } // prefere uma BIOS completa se houver mais de uma
            .firstOrNull { b -> required.any { r -> r.fileName.substringBeforeLast('.') == b.setName } }
        if (required.isNotEmpty() && found == null) return ArcadeCheck.BiosMissing
        val warnings = mutableListOf<ArcadeWarning>()
        if (found != null && !found.complete) warnings += ArcadeWarning.BIOS_INCOMPLETE

        val parent = dat.romset(system.id, romsetName(fileName))?.first?.parent
        val companions = if (parent == null) emptyList() else {
            val g = library.firstOrNull { romsetName(it.fileName) == parent } ?: return ArcadeCheck.ParentMissing(parent)
            listOf(RomCompanion(g.uri, g.fileName))
        }
        return ArcadeCheck.Ready(found, companions, warnings)
    }

    fun isLowMemory(romsetBytes: Long, availableBytes: Long): Boolean =
        romsetBytes > 0 && romsetBytes * MEMORY_FACTOR + EMULATOR_OVERHEAD > availableBytes
}
