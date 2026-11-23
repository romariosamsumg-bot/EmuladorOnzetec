package com.retroplay.feature.game.input

import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_BUTTON_THUMBL
import android.view.KeyEvent.KEYCODE_BUTTON_THUMBR
import com.retroplay.feature.game.input.MenuCombo.Result.CONSUME
import com.retroplay.feature.game.input.MenuCombo.Result.NONE
import com.retroplay.feature.game.input.MenuCombo.Result.OPEN_MENU
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuComboTest {

    @Test
    fun start_e_select_em_qualquer_ordem_abrem_o_menu_uma_vez() {
        val c = MenuCombo.forSetting("start_select")
        // Start primeiro (antes só funcionava com o Select primeiro).
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_START, true))
        assertEquals(OPEN_MENU, c.onKey(KEYCODE_BUTTON_SELECT, true))
        // Soltar não vaza para o jogo nem abre de novo.
        assertEquals(CONSUME, c.onKey(KEYCODE_BUTTON_START, false))
        assertEquals(CONSUME, c.onKey(KEYCODE_BUTTON_SELECT, false))
        // Select primeiro também.
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_SELECT, true))
        assertEquals(OPEN_MENU, c.onKey(KEYCODE_BUTTON_START, true))
        c.onKey(KEYCODE_BUTTON_SELECT, false); c.onKey(KEYCODE_BUTTON_START, false)
        // Start sozinho continua sendo do jogo.
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_START, true))
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_START, false))
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_A, true))
    }

    @Test
    fun select_enviado_como_voltar_tambem_conta() {
        val c = MenuCombo.forSetting("start_select")
        assertEquals(NONE, c.onKey(KEYCODE_BUTTON_START, true))
        assertEquals(OPEN_MENU, c.onKey(KEYCODE_BACK, true))
    }

    @Test
    fun outras_combinacoes_e_desligado() {
        val l3r3 = MenuCombo.forSetting("l3_r3")
        assertEquals(NONE, l3r3.onKey(KEYCODE_BUTTON_THUMBR, true))
        assertEquals(OPEN_MENU, l3r3.onKey(KEYCODE_BUTTON_THUMBL, true))
        val three = MenuCombo.forSetting("l1_r1_start")
        assertEquals(NONE, three.onKey(KEYCODE_BUTTON_L1, true))
        assertEquals(NONE, three.onKey(KEYCODE_BUTTON_START, true))
        assertEquals(OPEN_MENU, three.onKey(KEYCODE_BUTTON_R1, true))
        val off = MenuCombo.forSetting("off")
        off.onKey(KEYCODE_BUTTON_START, true)
        assertEquals(NONE, off.onKey(KEYCODE_BUTTON_SELECT, true))
    }
}
