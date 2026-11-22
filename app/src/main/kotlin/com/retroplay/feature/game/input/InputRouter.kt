package com.retroplay.feature.game.input

import android.view.KeyEvent
import com.retroplay.domain.system.RetroPadButton

/** O que fazer com uma tecla recebida durante o jogo. */
sealed interface InputAction {
    /** Repassa ao núcleo como o botão do RetroPad correspondente. */
    data class Forward(val retroKeyCode: Int) : InputAction

    /** Abrir o menu / sair do jogo (Fase 2: sai salvando a SRAM). */
    data object Menu : InputAction

    /** Não é do jogo (ex.: volume) — deixa o sistema tratar. */
    data object Ignore : InputAction
}

/**
 * Converte teclas físicas (gamepad ou controle remoto da TV) em botões do RetroPad.
 * Os mapas são injetáveis para o remapeamento da Fase 7.
 */
class InputRouter(
    private val gamepadMap: Map<Int, RetroPadButton> = DEFAULT_GAMEPAD,
    private val remoteMap: Map<Int, RetroPadButton> = DEFAULT_REMOTE,
) {

    fun route(keyCode: Int, fromGamepad: Boolean): InputAction {
        if (keyCode in MENU_KEYS) return InputAction.Menu
        if (keyCode in DIAGONALS) return InputAction.Forward(keyCode)
        val button = (if (fromGamepad) gamepadMap[keyCode] else remoteMap[keyCode] ?: gamepadMap[keyCode])
            ?: return InputAction.Ignore
        return InputAction.Forward(RetroPadKeys.keyCodeFor(button))
    }

    companion object {
        /** Voltar (controle remoto ou botão "back" de alguns gamepads) e Guide/Home do gamepad. */
        val MENU_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_MODE)

        private val DIAGONALS = setOf(
            KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        )

        private val DPAD = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to RetroPadButton.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to RetroPadButton.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to RetroPadButton.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to RetroPadButton.RIGHT,
        )

        /**
         * Mapeamento posicional: o botão de BAIXO do gamepad (A no Xbox, ✕ no PlayStation)
         * vira o botão de baixo do RetroPad (B), como no console original.
         */
        val DEFAULT_GAMEPAD: Map<Int, RetroPadButton> = DPAD + mapOf(
            KeyEvent.KEYCODE_BUTTON_A to RetroPadButton.B,
            KeyEvent.KEYCODE_BUTTON_B to RetroPadButton.A,
            KeyEvent.KEYCODE_BUTTON_X to RetroPadButton.Y,
            KeyEvent.KEYCODE_BUTTON_Y to RetroPadButton.X,
            KeyEvent.KEYCODE_BUTTON_L1 to RetroPadButton.L,
            KeyEvent.KEYCODE_BUTTON_R1 to RetroPadButton.R,
            KeyEvent.KEYCODE_BUTTON_L2 to RetroPadButton.L2,
            KeyEvent.KEYCODE_BUTTON_R2 to RetroPadButton.R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL to RetroPadButton.L3,
            KeyEvent.KEYCODE_BUTTON_THUMBR to RetroPadButton.R3,
            KeyEvent.KEYCODE_BUTTON_START to RetroPadButton.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to RetroPadButton.SELECT,
        )

        /** Controle remoto como controle de jogo (fallback para jogos simples). */
        val DEFAULT_REMOTE: Map<Int, RetroPadButton> = DPAD + mapOf(
            KeyEvent.KEYCODE_DPAD_CENTER to RetroPadButton.B,
            KeyEvent.KEYCODE_ENTER to RetroPadButton.B,
            KeyEvent.KEYCODE_NUMPAD_ENTER to RetroPadButton.B,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to RetroPadButton.START,
            KeyEvent.KEYCODE_MENU to RetroPadButton.START,
        )
    }
}

/** Teclas Android que o LibretroDroid traduz para cada botão do RetroPad. */
object RetroPadKeys {
    fun keyCodeFor(button: RetroPadButton): Int = when (button) {
        RetroPadButton.B -> KeyEvent.KEYCODE_BUTTON_B
        RetroPadButton.A -> KeyEvent.KEYCODE_BUTTON_A
        RetroPadButton.Y -> KeyEvent.KEYCODE_BUTTON_Y
        RetroPadButton.X -> KeyEvent.KEYCODE_BUTTON_X
        RetroPadButton.L -> KeyEvent.KEYCODE_BUTTON_L1
        RetroPadButton.R -> KeyEvent.KEYCODE_BUTTON_R1
        RetroPadButton.L2 -> KeyEvent.KEYCODE_BUTTON_L2
        RetroPadButton.R2 -> KeyEvent.KEYCODE_BUTTON_R2
        RetroPadButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
        RetroPadButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
        RetroPadButton.START -> KeyEvent.KEYCODE_BUTTON_START
        RetroPadButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
        RetroPadButton.UP -> KeyEvent.KEYCODE_DPAD_UP
        RetroPadButton.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        RetroPadButton.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        RetroPadButton.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    }
}
