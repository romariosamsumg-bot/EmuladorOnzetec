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
        /** Fliperama (MAME/FBNeo), inclusive Neo Geo. Antes da versão 1.2 era "neogeo". */
        val ARCADE = SystemId("arcade")
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
        folderAliases = setOf("snes", "sfc", "supernintendo", "superfamicom", "supernes", "supernintendoentertainmentsystem"),
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
        folderAliases = setOf("megadrive", "genesis", "md", "segagenesis", "segamegadrive", "megadrivegenesis"),
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
        folderAliases = setOf("mastersystem", "sms", "segamastersystem", "markiii", "mark3"),
    )

    val ARCADE = GameSystem(
        id = Ids.ARCADE,
        displayName = "Arcade",
        shortName = "ARC",
        releaseYear = 1978,
        extensions = setOf("zip", "7z"),
        cores = listOf(
            // Romsets do MAME 0.37 (b5 a b11): leve, ideal para TV de 2 GB e celular.
            CoreDescriptor("mame2000", "MAME 2000 (0.37)", "Para romsets do MAME 0.37. Leve, ideal para TVs."),
            CoreDescriptor("fbneo", "FinalBurn Neo", "Para romsets do FBNeo. Ótimo em Neo Geo, CPS1, CPS2 e CPS3."),
            CoreDescriptor("mame2003_plus", "MAME 2003-Plus", "Para romsets do MAME 0.78. Mais jogos, mais pesado."),
        ),
        // Só os jogos de Neo Geo exigem a BIOS (o app confere jogo a jogo).
        bios = listOf(
            BiosRequirement("neogeo.zip", "BIOS do Neo Geo (para os jogos de Neo Geo)", required = false),
        ),
        identification = IdentificationStrategy.ARCADE_ROMSET,
        // Botões 1–6 do fliperama, na ordem que MAME e FBNeo esperam (B, A, Y, X, L, R).
        controllerLayout = ControllerLayout(
            listOf(
                ConsoleButton("A", B), ConsoleButton("B", A),
                ConsoleButton("C", Y), ConsoleButton("D", X),
                ConsoleButton("E", L), ConsoleButton("F", R),
                ConsoleButton("Ficha", SELECT, highlighted = true),
                ConsoleButton("Start", START),
            ),
        ),
        accentColor = 0xFFE2A93B,
        iconKey = "arcade_stick",
        libretroDatabaseName = "MAME",
        folderAliases = setOf("arcade", "arcades", "fliperama", "mame", "mame2000", "mame037", "mame037b11", "fbneo", "fba", "finalburn",
            "neogeo", "neo", "mvs", "aes", "cps1", "cps2", "cps3"),
    )

    override val all: List<GameSystem> = listOf(SNES, MEGA_DRIVE, MASTER_SYSTEM, ARCADE)
}
