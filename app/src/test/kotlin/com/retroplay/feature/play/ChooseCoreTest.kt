package com.retroplay.feature.play

import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ordem: escolha para o jogo → núcleo do romset → escolha para o console → padrão. */
class ChooseCoreTest {
    private val arcade = SystemRegistry.ARCADE
    private fun snap(vararg kv: Pair<String, String>) = SettingsSnapshot(kv.toMap())

    @Test
    fun ordem_de_escolha_do_nucleo() {
        assertEquals("mame2000", chooseCore(arcade, snap(), "arcade/sf2", null).id)
        assertEquals("fbneo", chooseCore(arcade, snap(), "arcade/mslug3", "fbneo").id)
        // A escolha para o console não passa por cima do que o romset exige...
        assertEquals("fbneo", chooseCore(arcade, snap("s|arcade|emu.core" to "mame2003_plus"), "arcade/mslug3", "fbneo").id)
        // ...mas vale para romsets sem indicação.
        assertEquals("mame2003_plus", chooseCore(arcade, snap("s|arcade|emu.core" to "mame2003_plus"), "arcade/x", null).id)
        // A escolha feita para o jogo sempre vence.
        assertEquals("mame2000", chooseCore(arcade, snap("j|arcade/mslug3|emu.core" to "mame2000"), "arcade/mslug3", "fbneo").id)
        // Indicação desconhecida é ignorada.
        assertEquals("mame2000", chooseCore(arcade, null, "arcade/a", "mame9999").id)
        assertEquals("snes9x2010", chooseCore(SystemRegistry.SNES, snap(), "snes/mario", null).id)
    }
}
