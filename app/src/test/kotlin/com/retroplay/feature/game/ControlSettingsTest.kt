package com.retroplay.feature.game

import android.view.KeyEvent
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.feature.game.input.GamepadProfile
import com.retroplay.feature.game.input.MappingCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ControlSettingsTest {

    @Test
    fun `codifica e decodifica mapeamento ignorando lixo`() {
        val map = mapOf(KeyEvent.KEYCODE_BUTTON_X to RetroPadButton.B, KeyEvent.KEYCODE_BUTTON_A to RetroPadButton.Y)
        assertEquals(map, MappingCodec.decode(MappingCodec.encode(map)))
        assertEquals(emptyMap<Int, RetroPadButton>(), MappingCodec.decode("x=y,12=NADA,"))
    }

    @Test
    fun `remapear troca a tecla do botao e remove a antiga`() {
        val m = MappingCodec.gamepadMap(mapOf(KeyEvent.KEYCODE_BUTTON_X to RetroPadButton.B), nintendoLayout = false)
        assertEquals(RetroPadButton.B, m[KeyEvent.KEYCODE_BUTTON_X])
        // O A físico, que era o B do RetroPad, deixa de ser.
        assertFalse(m.containsKey(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(RetroPadButton.START, m[KeyEvent.KEYCODE_BUTTON_START])
    }

    @Test
    fun `layout nintendo troca A com B e X com Y`() {
        val m = MappingCodec.gamepadMap(emptyMap(), nintendoLayout = true)
        assertEquals(RetroPadButton.A, m[KeyEvent.KEYCODE_BUTTON_A])
        assertEquals(RetroPadButton.Y, m[KeyEvent.KEYCODE_BUTTON_Y])
    }

    @Test
    fun `turbo portas e perfis`() {
        assertEquals(setOf(RetroPadButton.B, RetroPadButton.Y), MappingCodec.decodeTurbo("Y,B,lixo"))
        assertEquals("B,Y", MappingCodec.encodeTurbo(setOf(RetroPadButton.Y, RetroPadButton.B)))
        val ports = mapOf("abc=1" to 1, "def" to 3)
        assertEquals(ports, MappingCodec.decodePorts(MappingCodec.encodePorts(ports)))
        assertEquals(emptyMap<String, Int>(), MappingCodec.decodePorts("x=9;semporta"))
        assertEquals(GamepadProfile.XBOX, GamepadProfile.fromVendor(0x045E))
        assertEquals(GamepadProfile.GENERIC, GamepadProfile.fromVendor(1))
    }
}
