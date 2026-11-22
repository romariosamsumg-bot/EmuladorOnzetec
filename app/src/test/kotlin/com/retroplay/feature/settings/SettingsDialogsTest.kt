package com.retroplay.feature.settings

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.settings.HomeRowGroups
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsDialogsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun assistente_grava_botoes_do_gamepad_e_ok_do_remoto_pula() {
        var result: Map<Int, RetroPadButton>? = null
        val buttons = SystemRegistry.SNES.controllerLayout.buttons.filter { it.retroPad !in setOf(RetroPadButton.UP, RetroPadButton.DOWN, RetroPadButton.LEFT, RetroPadButton.RIGHT) }
        compose.setContent {
            RetroPlayTheme { RemapWizardDialog("Super Nintendo", buttons, emptyMap(), onDone = { result = it }, onDismiss = {}) }
        }
        compose.waitForIdle()
        node("remap_button").assertTextContains(buttons[0].label)
        // 1º botão ← X do gamepad; 2º pulado com OK do remoto; o resto com Y (a última tecla vence).
        press(Key.ButtonX)
        node("remap_button").assertTextContains(buttons[1].label)
        press(Key.DirectionCenter)
        repeat(buttons.size - 2) { press(Key.ButtonY) }
        val map = result!!
        assertEquals(buttons[0].retroPad, map[KeyEvent.KEYCODE_BUTTON_X])
        assertEquals(buttons.last().retroPad, map[KeyEvent.KEYCODE_BUTTON_Y])
        assertEquals(2, map.size)
    }

    @Test
    fun teclado_de_pin_pelo_direcional() {
        var submitted: String? = null
        compose.setContent { RetroPlayTheme { PinDialog("PIN", onSubmit = { submitted = it; it == "1234" }, onDismiss = {}) } }
        compose.waitForIdle()
        node("pin_key_1").assertIsFocused()
        press(Key.DirectionCenter) // 1
        press(Key.DirectionRight); press(Key.DirectionCenter) // 2
        press(Key.DirectionRight); press(Key.DirectionCenter) // 3
        press(Key.DirectionDown); press(Key.DirectionLeft); press(Key.DirectionLeft)
        node("pin_key_4").assertIsFocused()
        press(Key.DirectionCenter) // 4 → envia
        assertEquals("1234", submitted)
    }

    @Test
    fun pin_errado_limpa_e_avisa() {
        compose.setContent { RetroPlayTheme { PinDialog("PIN", onSubmit = { false }, onDismiss = {}) } }
        compose.waitForIdle()
        repeat(4) { press(Key.DirectionCenter) }
        node("pin_dots").assertTextContains("○  ○  ○  ○")
    }

    @Test
    fun escolha_com_valor_herdado() {
        var picked: String? = "x"
        compose.setContent {
            RetroPlayTheme {
                ChoiceDialog("Filtro", listOf("none" to "Nenhum", "crt" to "CRT"), selected = null, inheritedLabel = "Nenhum", onPick = { picked = it }, onDismiss = {})
            }
        }
        compose.waitForIdle()
        node("choice_inherit").assertIsFocused()
        press(Key.DirectionDown); press(Key.DirectionDown)
        node("choice_crt").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("crt", picked)
    }

    @Test
    fun regras_puras() {
        assertEquals(RemapKey.Skip, classifyRemapKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemapKey.Assign(KeyEvent.KEYCODE_BUTTON_A), classifyRemapKey(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(RemapKey.Ignore, classifyRemapKey(KeyEvent.KEYCODE_DPAD_UP))
        // Uma tecla leva a um só botão, e um botão tem uma só tecla.
        val m = assignKey(assignKey(emptyMap(), 96, RetroPadButton.A), 97, RetroPadButton.A)
        assertEquals(mapOf(97 to RetroPadButton.A), m)
        assertEquals(mapOf(97 to RetroPadButton.B), assignKey(m, 97, RetroPadButton.B))

        assertEquals(HomeRowGroups.DEFAULT_ORDER, parseRowOrder(""))
        assertEquals("genres", parseRowOrder("genres,xyz")[0])
        assertEquals(listOf("b", "a", "c"), moveItem(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "b", "c"), moveItem(listOf("a", "b", "c"), 0, -1))
        assertEquals(setOf("recent"), parseHidden(" recent ,"))

        assertEquals(OverrideBadge.NONE, overrideBadge(Scope.GLOBAL, Scope.GLOBAL, true))
        assertEquals(OverrideBadge.HERE, overrideBadge(Scope.SYSTEM, Scope.SYSTEM, true))
        assertEquals(OverrideBadge.FROM_SYSTEM, overrideBadge(Scope.GAME, Scope.SYSTEM, true))
        assertEquals(OverrideBadge.FROM_GLOBAL, overrideBadge(Scope.GAME, null, true))
        assertEquals("/storage/emulated/0/Jogos/a.sfc", readablePath("/storage/emulated/0/Jogos/a.sfc"))
        assertTrue(readablePath("content://x/tree/primary%3AJogos%2FBIOS/document/primary%3AJogos%2FBIOS%2Fneogeo.zip")== "Jogos/BIOS/neogeo.zip")
    }

    @Test
    fun quem_vai_jogar_comeca_no_perfil_ativo() {
        val list = listOf(com.retroplay.domain.profile.Profile(1, "Pai", 0), com.retroplay.domain.profile.Profile(2, "Ana", 0), com.retroplay.domain.profile.Profile(3, "Leo", 0))
        var picked: Long? = null
        compose.setContent { RetroPlayTheme { com.retroplay.feature.profiles.ProfilePickerScreen(list, activeId = 2) { picked = it.id } } }
        compose.waitForIdle()
        node("profile_2").assertIsFocused()
        press(Key.DirectionRight)
        node("profile_3").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(3L, picked)
    }

    @Test
    fun teclado_em_grade_digita_pelo_direcional() {
        var saved: String? = null
        compose.setContent { RetroPlayTheme { TextInputDialog("Nome", "", onSave = { saved = it }, onDismiss = {}) } }
        compose.waitForIdle()
        node("tkey_A").assertIsFocused()
        press(Key.DirectionCenter) // A
        press(Key.DirectionDown); press(Key.DirectionRight, 4) // N (linha 2, coluna 5)
        node("tkey_N").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp); press(Key.DirectionLeft, 4)
        press(Key.DirectionCenter) // A
        node("text_input_value").assertTextContains("Ana▏")
        repeat(8) { if (compose.onAllNodes(hasTestTag("text_input_save") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionDown) }
        node("text_input_save").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("Ana", saved)
        assertEquals("Ana Lu", applyTextKey(applyTextKey(applyTextKey("Ana", " ", 20), "L", 20), "U", 20))
        assertEquals("Ab", applyTextKey("Ab", "C", 2))
        assertEquals("A", applyTextKey("A ", "⌫", 20))
    }

    @Test
    fun teclado_completo_para_senha() {
        var saved: String? = null
        compose.setContent { RetroPlayTheme { TextInputDialog("Senha", "", onSave = { saved = it }, onDismiss = {}, raw = true, secret = true) } }
        compose.waitForIdle()
        node("tkey_a").assertIsFocused()
        press(Key.DirectionCenter) // a
        // ⇧ fica na última linha; liga, digita B (segunda tecla da primeira linha), desliga.
        repeat(6) { if (compose.onAllNodes(hasTestTag("tkey_shift") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionDown) }
        node("tkey_shift").assertIsFocused()
        press(Key.DirectionCenter)
        repeat(6) { press(Key.DirectionUp) }
        repeat(12) { press(Key.DirectionLeft) }
        node("tkey_a").assertIsFocused()
        press(Key.DirectionRight)
        node("tkey_b").assertIsFocused()
        press(Key.DirectionCenter)
        node("text_input_value").assertTextContains("•B▏")
        repeat(8) { if (compose.onAllNodes(hasTestTag("text_input_save") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        assertEquals("aB", saved)
        assertEquals("x.y", applyRawKey(applyRawKey("x", ".", false, 10), "y", false, 10))
    }
}
