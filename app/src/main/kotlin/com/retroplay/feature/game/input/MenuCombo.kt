package com.retroplay.feature.game.input

import android.view.KeyEvent

/**
 * Atalho do controle que abre o menu do jogo (o "Voltar" do controle remoto), configurável.
 * Padrão: Start + Select juntos, em qualquer ordem. Funciona com qualquer controle Bluetooth
 * ou USB: o botão Select que alguns controles enviam como "Voltar" também conta.
 */
class MenuCombo(private val keys: Set<Int>) {

    enum class Result { NONE, OPEN_MENU, CONSUME }

    private val held = HashSet<Int>()
    private var fired = false

    fun onKey(keyCode: Int, down: Boolean): Result {
        if (keys.isEmpty()) return Result.NONE
        val key = normalize(keyCode)
        if (key !in keys) return Result.NONE
        if (down) {
            held += key
            if (!fired && keys.all { it in held }) {
                fired = true
                return Result.OPEN_MENU
            }
            return if (fired) Result.CONSUME else Result.NONE
        }
        held -= key
        val wasFired = fired
        if (held.isEmpty()) fired = false
        // Depois do atalho, soltar os botões não chega ao jogo (ele já recebeu o "soltar" à força).
        return if (wasFired) Result.CONSUME else Result.NONE
    }

    fun reset() {
        held.clear()
        fired = false
    }

    companion object {
        const val START_SELECT = "start_select"
        const val L3_R3 = "l3_r3"
        const val L1_R1_START = "l1_r1_start"
        const val OFF = "off"
        val OPTIONS = listOf(START_SELECT, L3_R3, L1_R1_START, OFF)

        fun forSetting(value: String): MenuCombo = MenuCombo(
            when (value) {
                L3_R3 -> setOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)
                L1_R1_START -> setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START)
                OFF -> emptySet()
                else -> setOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)
            },
        )

        /**
         * Botão do RetroPad que a tecla aciona depois do remapeamento (ex.: um controle genérico
         * com o Start em BUTTON_10 remapeado vira BUTTON_START), para os atalhos seguirem o remapeamento.
         */
        fun logicalKey(router: InputRouter, keyCode: Int): Int =
            (router.route(keyCode, true) as? InputAction.Forward)?.retroKeyCode ?: normalize(keyCode)

        /** Alguns controles enviam o Select como "Voltar"; o botão Menu (≡) como Start. */
        fun normalize(keyCode: Int) = when (keyCode) {
            KeyEvent.KEYCODE_BACK -> KeyEvent.KEYCODE_BUTTON_SELECT
            KeyEvent.KEYCODE_MENU -> KeyEvent.KEYCODE_BUTTON_START
            else -> keyCode
        }
    }
}
