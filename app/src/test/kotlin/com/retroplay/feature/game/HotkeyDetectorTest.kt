package com.retroplay.feature.game

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.retroplay.feature.game.input.Hotkey
import com.retroplay.feature.game.input.HotkeyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotkeyDetectorTest {

    @Test
    fun `select mais start abre o menu em qualquer ordem`() {
        val d = HotkeyDetector()
        assertNull(d.onKey(KEYCODE_BUTTON_SELECT, true))
        assertEquals(Hotkey.MENU, d.onKey(KEYCODE_BUTTON_START, true))
        d.reset()
        // Start sozinho é botão do jogo.
        assertNull(d.onKey(KEYCODE_BUTTON_START, true))
        assertNull(d.onKey(KEYCODE_BUTTON_START, false))
    }

    @Test
    fun `rewind enquanto segura e para ao soltar`() {
        val d = HotkeyDetector()
        d.onKey(KEYCODE_BUTTON_SELECT, true)
        assertEquals(Hotkey.REWIND_START, d.onKey(KEYCODE_BUTTON_L1, true))
        assertEquals(Hotkey.REWIND_STOP, d.onKey(KEYCODE_BUTTON_L1, false))
        assertEquals(Hotkey.REWIND_START, d.onKey(KEYCODE_BUTTON_L1, true))
        assertEquals(Hotkey.REWIND_STOP, d.onKey(KEYCODE_BUTTON_SELECT, false))
    }

    @Test
    fun `acelerar e botoes sem atalho`() {
        val d = HotkeyDetector()
        d.onKey(KEYCODE_BUTTON_SELECT, true)
        assertEquals(Hotkey.FAST_FORWARD, d.onKey(KEYCODE_BUTTON_R1, true))
        assertNull(d.onKey(KEYCODE_BUTTON_A, true))
    }
}
