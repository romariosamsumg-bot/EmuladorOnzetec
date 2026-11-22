package com.retroplay.domain.settings

import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelTest {

    private val snes = SystemRegistry.Ids.SNES
    private val md = SystemRegistry.Ids.MEGA_DRIVE
    private val smw = GameKey.of(snes, "Super Mario World (USA).sfc")

    @Test
    fun `jogo sobrescreve console que sobrescreve global`() {
        val s = SettingsSnapshot(
            mapOf("g|video.shader" to "crt", "s|snes|video.shader" to "lcd", "j|$smw|video.shader" to "sharp"),
        )
        assertEquals(Resolved("sharp", Scope.GAME), s.resolve(AppSettings.SHADER, snes, smw))
        assertEquals(Resolved("lcd", Scope.SYSTEM), s.resolve(AppSettings.SHADER, snes, "snes/outro"))
        assertEquals(Resolved("crt", Scope.GLOBAL), s.resolve(AppSettings.SHADER, md))
        assertEquals(Resolved("none", null), SettingsSnapshot().resolve(AppSettings.SHADER, snes, smw))
        assertTrue(SettingsSnapshot().resolve(AppSettings.SHADER).isDefault)
    }

    @Test
    fun `configuracao global ignora console e jogo`() {
        val s = SettingsSnapshot(mapOf("s|snes|ui.theme" to "oled", "g|ui.theme" to "dark"))
        assertEquals("dark", s.value(AppSettings.THEME, snes, smw))
    }

    @Test
    fun `valores invalidos caem no proximo nivel`() {
        val s = SettingsSnapshot(mapOf("s|snes|video.shader" to "inexistente", "g|video.shader" to "crt", "g|emu.rewind" to "talvez"))
        assertEquals("crt", s.value(AppSettings.SHADER, snes))
        assertEquals(false, s.value(AppSettings.REWIND, snes))
    }

    @Test
    fun `chave do jogo e independente da extensao`() {
        assertEquals("snes/Super Mario World (USA)", smw)
        assertEquals(smw, GameKey.of(snes, "/x/Super Mario World (USA).zip"))
        assertEquals("snes/a_b", GameKey.of(snes, "a:b.smc"))
    }

    @Test
    fun `chaves por nivel para opcoes de nucleo`() {
        val s = SettingsSnapshot(mapOf("s|snes|coreopt.snes9x2010.a" to "1", "s|snes|coreopt.snes9x2010.b" to "2", "g|coreopt.snes9x2010.a" to "0"))
        assertEquals(mapOf("coreopt.snes9x2010.a" to "1", "coreopt.snes9x2010.b" to "2"), s.keysIn(ScopeRef.system(snes), "coreopt.snes9x2010."))
    }

    @Test
    fun `opcao legada de nucleo`() {
        val o = CoreOption.parse("snes9x_2010_aspect", "Preferred Aspect Ratio; 4:3|uncorrected|auto", null)
        assertEquals("Preferred Aspect Ratio", o.description)
        assertEquals(listOf("4:3", "uncorrected", "auto"), o.values)
        assertEquals("4:3", o.current)
        assertEquals("auto", CoreOption.parse("k", "D; a|b|auto", "auto").current)
        assertNull(SettingsSnapshot().get(AppSettings.SHADER, ScopeRef.Global))
    }
}
