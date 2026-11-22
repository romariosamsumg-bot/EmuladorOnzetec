package com.retroplay.domain.system

import com.retroplay.domain.system.RetroPadButton.A
import com.retroplay.domain.system.RetroPadButton.B
import com.retroplay.domain.system.RetroPadButton.L
import com.retroplay.domain.system.RetroPadButton.R
import com.retroplay.domain.system.RetroPadButton.SELECT
import com.retroplay.domain.system.RetroPadButton.START
import com.retroplay.domain.system.RetroPadButton.X
import com.retroplay.domain.system.RetroPadButton.Y

/** Catálogo de sistemas suportados. Injetado via DI para facilitar testes. */
interface SystemCatalog {
    val all: List<GameSystem>
    fun byId(id: SystemId): GameSystem? = all.firstOrNull { it.id == id }
}

/**
 * Registro dos consoles suportados. Para adicionar um console, crie um [GameSystem]
 * aqui e inclua-o em [all] — nenhuma tela precisa ser alterada.
 */
object SystemRegistry : SystemCatalog {

    object Ids {
        val SNES = SystemId("snes")
        val MEGA_DRIVE = SystemId("megadrive")
        val MASTER_SYSTEM = SystemId("mastersystem")
        val NEO_GEO = SystemId("neogeo")
    }

    private val genesisPlusGx = CoreDescriptor(
        id = "genesis_plus_gx",
        displayName = "Genesis Plus GX",
        description = "Preciso e leve. Recomendado.",
    )
    private val picodrive = CoreDescriptor(
        id = "picodrive",
        displayName = "PicoDrive",
        description = "Muito rápido; menos preciso no áudio.",
    )

    val SNES = GameSystem(
        id = Ids.SNES,
        displayName = "Super Nintendo",
        shortName = "SNES",
        releaseYear = 1990,
        extensions = setOf("sfc", "smc", "fig", "swc", "zip", "7z"),
        cores = listOf(
            CoreDescriptor("snes9x2010", "Snes9x 2010", "Rápido, ideal para TVs. Recomendado."),
            CoreDescriptor("snes9x", "Snes9x", "Mais preciso, porém mais pesado."),
        ),
        controllerLayout = ControllerLayout(
            listOf(
                ConsoleButton("B", B), ConsoleButton("A", A),
                ConsoleButton("Y", Y), ConsoleButton("X", X),
                ConsoleButton("L", L), ConsoleButton("R", R),
                ConsoleButton("Select", SELECT), ConsoleButton("Start", START),
            ),
        ),
        accentColor = 0xFF7E6BD6,
        iconKey = "pad_16bit_rounded",
        libretroDatabaseName = "Nintendo - Super Nintendo Entertainment System",
    )

    val MEGA_DRIVE = GameSystem(
        id = Ids.MEGA_DRIVE,
        displayName = "Mega Drive",
        shortName = "MD",
        releaseYear = 1988,
        extensions = setOf("md", "gen", "smd", "bin", "zip", "7z"),
        cores = listOf(genesisPlusGx, picodrive),
        // Layout de 6 botões do genesis_plus_gx (no modo 3 botões só A, B, C são usados).
        controllerLayout = ControllerLayout(
            listOf(
                ConsoleButton("A", Y), ConsoleButton("B", B), ConsoleButton("C", A),
                ConsoleButton("X", L), ConsoleButton("Y", X), ConsoleButton("Z", R),
                ConsoleButton("Mode", SELECT), ConsoleButton("Start", START),
            ),
        ),
        accentColor = 0xFF2F6FDE,
        iconKey = "pad_16bit_crescent",
        libretroDatabaseName = "Sega - Mega Drive - Genesis",
    )

    val MASTER_SYSTEM = GameSystem(
        id = Ids.MASTER_SYSTEM,
        displayName = "Master System",
        shortName = "SMS",
        releaseYear = 1985,
        extensions = setOf("sms", "zip", "7z"),
        cores = listOf(genesisPlusGx, picodrive),
        controllerLayout = ControllerLayout(
            listOf(
                ConsoleButton("1", B), ConsoleButton("2", A),
                // O botão Pause fica no console; por padrão é mapeado no Start.
                ConsoleButton("Pause", START, highlighted = true),
            ),
        ),
        accentColor = 0xFFD9413B,
        iconKey = "pad_8bit",
        libretroDatabaseName = "Sega - Master System - Mark III",
    )

    val NEO_GEO = GameSystem(
        id = Ids.NEO_GEO,
        displayName = "Neo Geo",
        shortName = "NEO",
        releaseYear = 1990,
        extensions = setOf("zip", "7z"),
        cores = listOf(CoreDescriptor("fbneo", "FinalBurn Neo", "Núcleo de arcade com suporte a MVS/AES.")),
        bios = listOf(
            BiosRequirement("neogeo.zip", "BIOS do Neo Geo (MVS/AES/UniBIOS)", required = true),
        ),
        identification = IdentificationStrategy.ARCADE_ROMSET,
        // Mapeamento clássico do FBNeo para Neo Geo.
        controllerLayout = ControllerLayout(
            listOf(
                ConsoleButton("A", B), ConsoleButton("B", A),
                ConsoleButton("C", Y), ConsoleButton("D", X),
                ConsoleButton("Ficha", SELECT, highlighted = true),
                ConsoleButton("Start", START),
            ),
        ),
        accentColor = 0xFFE2A93B,
        iconKey = "arcade_stick",
        libretroDatabaseName = "SNK - Neo Geo",
    )

    override val all: List<GameSystem> = listOf(SNES, MEGA_DRIVE, MASTER_SYSTEM, NEO_GEO)
}
