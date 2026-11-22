package com.retroplay.feature.game.input

import android.view.KeyEvent

/** Ações de atalho do gamepad durante o jogo. */
enum class Hotkey { MENU, FAST_FORWARD, REWIND_START, REWIND_STOP, QUICK_SAVE, QUICK_LOAD }

/**
 * Atalhos com Select como modificador (padrão; remapeáveis na Fase 7):
 *  Select+Start = menu · Select+R1 = acelerar (alternar) · Select+L1 (segurar) = rewind ·
 *  Select+R2 = salvar rápido · Select+L2 = carregar rápido.
 */
class HotkeyDetector(
    private val modifier: Int = KeyEvent.KEYCODE_BUTTON_SELECT,
    private val bindings: Map<Int, Hotkey> = DEFAULT,
) {
    private val held = HashSet<Int>()
    private var rewinding = false

    /** @return o atalho disparado por este evento (ou null). */
    fun onKey(keyCode: Int, down: Boolean): Hotkey? {
        if (down) held += keyCode else held -= keyCode
        if (!down && keyCode == REWIND_KEY && rewinding || !down && keyCode == modifier && rewinding) {
            rewinding = false
            return Hotkey.REWIND_STOP
        }
        if (!down || modifier !in held || keyCode == modifier) return null
        val action = bindings[keyCode] ?: return null
        if (action == Hotkey.REWIND_START) rewinding = true
        return action
    }

    fun reset() {
        held.clear()
        rewinding = false
    }

    companion object {
        private const val REWIND_KEY = KeyEvent.KEYCODE_BUTTON_L1
        val DEFAULT = mapOf(
            KeyEvent.KEYCODE_BUTTON_START to Hotkey.MENU,
            KeyEvent.KEYCODE_BUTTON_R1 to Hotkey.FAST_FORWARD,
            REWIND_KEY to Hotkey.REWIND_START,
            KeyEvent.KEYCODE_BUTTON_R2 to Hotkey.QUICK_SAVE,
            KeyEvent.KEYCODE_BUTTON_L2 to Hotkey.QUICK_LOAD,
        )
    }
}
