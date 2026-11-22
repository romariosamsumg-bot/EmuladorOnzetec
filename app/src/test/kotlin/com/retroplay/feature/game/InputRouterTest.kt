package com.retroplay.feature.game

import android.view.KeyEvent
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.feature.game.input.InputAction
import com.retroplay.feature.game.input.InputRouter
import com.retroplay.feature.game.input.PortAssigner
import com.retroplay.feature.game.input.RetroPadKeys
import com.retroplay.feature.game.input.StickToDpad
import org.junit.Assert.assertEquals
import org.junit.Test

class InputRouterTest {

    private val router = InputRouter()
    private fun fwd(b: RetroPadButton) = InputAction.Forward(RetroPadKeys.keyCodeFor(b))

    @Test
    fun `gamepad usa mapeamento posicional`() {
        // Botão de baixo (A do Xbox / ✕ do PlayStation) = B do SNES.
        assertEquals(fwd(RetroPadButton.B), router.route(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(fwd(RetroPadButton.A), router.route(KeyEvent.KEYCODE_BUTTON_B, true))
        assertEquals(fwd(RetroPadButton.Y), router.route(KeyEvent.KEYCODE_BUTTON_X, true))
        assertEquals(fwd(RetroPadButton.X), router.route(KeyEvent.KEYCODE_BUTTON_Y, true))
        assertEquals(fwd(RetroPadButton.L), router.route(KeyEvent.KEYCODE_BUTTON_L1, true))
        assertEquals(fwd(RetroPadButton.START), router.route(KeyEvent.KEYCODE_BUTTON_START, true))
        assertEquals(fwd(RetroPadButton.SELECT), router.route(KeyEvent.KEYCODE_BUTTON_SELECT, true))
        assertEquals(fwd(RetroPadButton.UP), router.route(KeyEvent.KEYCODE_DPAD_UP, true))
    }

    @Test
    fun `retropad chega ao libretrodroid com as teclas que ele traduz`() {
        // O LibretroDroid converte KEYCODE_BUTTON_B → RETRO_DEVICE_ID_JOYPAD_B.
        assertEquals(InputAction.Forward(KeyEvent.KEYCODE_BUTTON_B), router.route(KeyEvent.KEYCODE_BUTTON_A, true))
    }

    @Test
    fun `controle remoto funciona como controle simples`() {
        assertEquals(fwd(RetroPadButton.LEFT), router.route(KeyEvent.KEYCODE_DPAD_LEFT, false))
        assertEquals(fwd(RetroPadButton.B), router.route(KeyEvent.KEYCODE_DPAD_CENTER, false))
        assertEquals(fwd(RetroPadButton.START), router.route(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false))
    }

    @Test
    fun `voltar e guide abrem o menu`() {
        assertEquals(InputAction.Menu, router.route(KeyEvent.KEYCODE_BACK, false))
        assertEquals(InputAction.Menu, router.route(KeyEvent.KEYCODE_BACK, true))
        assertEquals(InputAction.Menu, router.route(KeyEvent.KEYCODE_BUTTON_MODE, true))
    }

    @Test
    fun `volume e outras teclas ficam com o sistema`() {
        assertEquals(InputAction.Ignore, router.route(KeyEvent.KEYCODE_VOLUME_UP, false))
        assertEquals(InputAction.Ignore, router.route(KeyEvent.KEYCODE_HOME, false))
    }

    @Test
    fun `diagonais passam direto`() {
        assertEquals(InputAction.Forward(KeyEvent.KEYCODE_DPAD_UP_LEFT), router.route(KeyEvent.KEYCODE_DPAD_UP_LEFT, true))
    }

    @Test
    fun `mapa customizado substitui o padrao`() {
        val custom = InputRouter(gamepadMap = mapOf(KeyEvent.KEYCODE_BUTTON_A to RetroPadButton.START))
        assertEquals(fwd(RetroPadButton.START), custom.route(KeyEvent.KEYCODE_BUTTON_A, true))
        assertEquals(InputAction.Ignore, custom.route(KeyEvent.KEYCODE_BUTTON_B, true))
    }
}

class PortAssignerTest {
    @Test
    fun `controles recebem portas na ordem de uso`() {
        val p = PortAssigner()
        assertEquals(0, p.portFor(deviceId = 7, isGamepad = true))
        assertEquals(1, p.portFor(deviceId = 3, isGamepad = true))
        assertEquals(0, p.portFor(deviceId = 7, isGamepad = true))
        assertEquals(2, p.portFor(deviceId = 9, isGamepad = true))
    }

    @Test
    fun `controle remoto e sempre o jogador 1`() {
        val p = PortAssigner()
        p.portFor(5, true)
        assertEquals(0, p.portFor(1, isGamepad = false))
    }

    @Test
    fun `porta liberada e reaproveitada e limite de 4`() {
        val p = PortAssigner()
        (1..4).forEach { p.portFor(it, true) }
        assertEquals(3, p.portFor(99, true))
        p.release(2)
        assertEquals(1, p.portFor(50, true))
    }
}

class StickToDpadTest {
    @Test
    fun `hat tem prioridade e analogico usa limiar`() {
        assertEquals(-1f, StickToDpad.combine(-1f, 0.9f))
        assertEquals(1f, StickToDpad.combine(0f, 0.7f))
        assertEquals(-1f, StickToDpad.combine(0f, -0.6f))
        assertEquals(0f, StickToDpad.combine(0f, 0.3f))
    }
}
