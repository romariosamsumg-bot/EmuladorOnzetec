package com.retroplay.feature.game

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.feature.game.session.AspectChoice
import com.retroplay.feature.game.session.GameMenu
import com.retroplay.feature.game.session.GameMenuActions
import com.retroplay.feature.game.session.GameMenuState
import com.retroplay.feature.game.session.MenuPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameMenuTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val calls = mutableListOf<String>()

    /** Ações gravadas; a navegação de páginas altera o estado como a GameActivity faz. */
    private inner class Actions(private val update: (GameMenuState.() -> GameMenuState) -> Unit) : GameMenuActions {
        override fun resume() { calls += "resume" }
        override fun openPage(page: MenuPage) { calls += "page:$page"; update { copy(page = page) } }
        override fun save(slot: Int) { calls += "save:$slot" }
        override fun load(slot: Int) { calls += "load:$slot" }
        override fun reset() { calls += "reset" }
        override fun insertCoin() { calls += "coin" }
        override fun cycleSpeed() { calls += "speed" }
        override fun toggleRewind() { calls += "rewind" }
        override fun rewindSeconds() { calls += "rewind5" }
        override fun cycleShader() { calls += "shader" }
        override fun cycleAspect() { calls += "aspect" }
        override fun toggleFps() { calls += "fps" }
        override fun screenshot() { calls += "shot" }
        override fun exit() { calls += "exit" }
    }

    private fun show(system: GameSystem, slots: List<SaveSlot> = emptyList(), aspect: AspectChoice? = AspectChoice.ORIGINAL) {
        compose.setContent {
            var state by remember { mutableStateOf(GameMenuState("Super Mario World", system, slots = slots, aspect = aspect)) }
            RetroPlayTheme { GameMenu(state, Actions { f -> state = state.f() }) }
        }
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun thumb(color: Int): String {
        val f = File(tmp.root, "t$color.png")
        val b = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    @Test
    fun menu_principal_por_dpad_e_voltar_continua() {
        show(SystemRegistry.SNES)
        node("menu_resume").assertIsFocused()
        node("menu_load").assertIsNotEnabled() // sem saves
        press(Key.DirectionDown)
        node("menu_save").assertIsFocused()
        // "Carregar" desabilitado é pulado pelo D-pad.
        press(Key.DirectionDown)
        node("menu_rewind").assertIsFocused()
        press(Key.DirectionCenter)
        node("menu_coin").assertDoesNotExist() // ficha só no Neo Geo
        back()
        assertEquals(listOf("rewind", "resume"), calls)
    }

    @Test
    fun salvar_no_espaco_1() {
        show(SystemRegistry.SNES)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        node("slots_save").assertExists()
        node("slot_0").assertDoesNotExist() // o automático não é escolhido à mão
        node("slot_1").assertIsFocused()
        press(Key.DirectionRight)
        node("slot_2").assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        assertEquals(listOf("page:SAVE", "save:1"), calls)
        back()
        node("menu_resume").assertExists()
    }

    @Test
    fun carregar_so_espacos_com_save() {
        val slots = listOf(SaveSlot(0, 1_000, thumb(0xFF2266DD.toInt()), 10), SaveSlot(3, 2_000, thumb(0xFF22AA44.toInt()), 10))
        show(SystemRegistry.SNES, slots)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        node("menu_load").assertIsFocused()
        press(Key.DirectionCenter)
        node("slot_0").assertIsFocused()
        node("slot_5").assertIsNotEnabled()
        compose.onNodeWithTag("slot_0").assertTextContains("Automático", substring = true)
        screenshot("50_menu_carregar")
        press(Key.DirectionRight) // espaços 1 e 2 vazios são pulados
        node("slot_3").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("load:3", calls.last())
    }

    @Test
    fun neo_geo_tem_ficha_e_proporcao_indisponivel() {
        show(SystemRegistry.ARCADE, aspect = null)
        node("menu_coin").assertExists()
        node("menu_aspect").assertIsNotEnabled()
        screenshot("51_menu_neogeo")
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
