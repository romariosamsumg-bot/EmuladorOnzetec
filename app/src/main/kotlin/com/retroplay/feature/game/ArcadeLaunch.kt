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
    data class Ready(
        val bios: BiosStatus?,
        val companions: List<RomCompanion>,
        val warnings: List<ArcadeWarning>,
        /** Jogo de Neo Geo (controle na tela com A, B, C, D). */
        val neoGeo: Boolean = false,
    ) : ArcadeCheck
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
    fun check(system: GameSystem, fileName: String, dat: DatLookup, library: List<Game>, bios: List<BiosStatus>, coreId: String? = null): ArcadeCheck {
        if (system.identification != IdentificationStrategy.ARCADE_ROMSET) return ArcadeCheck.Ready(null, emptyList(), emptyList())
        // A versão do romset do núcleo em uso (MAME 0.37 e FBNeo diferem em pai e BIOS).
        val versions = dat.romsets(system.id, romsetName(fileName))
        val game = versions.firstOrNull { it.first.core == coreId }?.first ?: versions.firstOrNull()?.first
        val needed = game?.bios ?: system.bios.filter { it.required }.firstOrNull()?.fileName?.substringBeforeLast('.')
        val warnings = mutableListOf<ArcadeWarning>()
        val companions = mutableListOf<RomCompanion>()
        var found: BiosStatus? = null
        if (needed != null) {
            // Prefere uma BIOS completa se houver mais de uma.
            found = bios.filter { it.system == system.id && it.setName == needed }.sortedByDescending { it.complete }.firstOrNull()
                ?: return ArcadeCheck.BiosMissing
            if (!found.complete) warnings += ArcadeWarning.BIOS_INCOMPLETE
            // MAME procura a BIOS na pasta do jogo; o FBNeo também (e em system/fbneo).
            if (library.none { it.uri == found.uri }) companions += RomCompanion(found.uri, "$needed.zip")
        }
        val parent = game?.parent
        if (parent != null) {
            val g = library.firstOrNull { romsetName(it.fileName) == parent } ?: return ArcadeCheck.ParentMissing(parent)
            companions.add(0, RomCompanion(g.uri, g.fileName))
        }
        return ArcadeCheck.Ready(found, companions, warnings, neoGeo = needed == "neogeo")
    }

    fun isLowMemory(romsetBytes: Long, availableBytes: Long): Boolean =
        romsetBytes > 0 && romsetBytes * MEMORY_FACTOR + EMULATOR_OVERHEAD > availableBytes
}
