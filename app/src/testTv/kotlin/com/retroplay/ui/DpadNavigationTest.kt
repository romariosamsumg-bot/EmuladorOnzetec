package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Navegação 100% por D-pad: sem nenhum toque, o usuário percorre todas as telas
 * do menu lateral e o foco nunca se perde.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DpadNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /** Equivale ao botão Voltar do controle remoto. */
    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** Leva o foco ao menu (item Início), insistindo enquanto a Início termina de montar. */
    private fun toMenu() {
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty() }
        repeat(5) {
            if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("nav_home") and androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                press(Key.DirectionLeft)
            }
        }
        node("nav_home").assertIsFocused()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun foco_inicial_fica_no_conteudo_da_home() {
        node("screen_home").assertExists()
        node("home_transfer").assertIsFocused()
        screenshot("01_home")
    }

    @Test
    fun d_pad_percorre_todas_as_telas_do_menu() {
        node("home_transfer").assertIsFocused()

        // Esquerda → menu lateral (item Início selecionado)
        toMenu()
        node("nav_home").assertIsFocused()
        screenshot("02_drawer_open")

        val screens = listOf(
            "nav_search" to "screen_search",
            "nav_consoles" to "screen_consoles",
            "nav_favorites" to "screen_favorites",
            "nav_transfer" to "screen_transfer",
            "nav_settings" to "screen_settings",
        )
        for ((navTag, screenTag) in screens) {
            press(Key.DirectionDown)
            node(navTag).assertIsFocused()
            press(Key.DirectionCenter)
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            node(screenTag).assertExists()
            // A tela nova recebe o foco automaticamente (nada de "foco perdido").
            assertContentHasFocus(screenTag)
            screenshot("03_$screenTag")
            // Volta ao menu pelo D-pad esquerdo, mantendo o item atual focado.
            press(Key.DirectionLeft)
            node(navTag).assertIsFocused()
        }
    }

    @Test
    fun voltar_no_conteudo_vai_ao_menu_e_depois_ao_inicio() {
        // Vai para Consoles pelo D-pad.
        toMenu()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        node("nav_consoles").assertIsFocused()
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(500)
        node("system_snes").assertIsFocused()

        // Direita move entre os cards de console.
        press(Key.DirectionRight)
        node("system_megadrive").assertIsFocused()
        screenshot("04_consoles_focus")

        // Voltar com foco no conteúdo → menu (item Consoles).
        pressBack()
        compose.waitForIdle()
        node("nav_consoles").assertIsFocused()

        // Voltar no menu fora do Início → Início.
        pressBack()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        node("screen_home").assertExists()
        assertContentHasFocus("screen_home")
    }

    @Test
    fun home_permite_descer_para_a_fileira_de_consoles() {
        press(Key.DirectionDown)
        node("system_snes").assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        node("system_arcade").assertIsFocused()
        press(Key.DirectionUp)
        node("home_transfer").assertIsFocused()
    }

    private fun assertContentHasFocus(screenTag: String) {
        val focused = compose.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Nenhum nó focado em $screenTag", focused.isNotEmpty())
        node("${screenTag}_primary").let { primary ->
            when (screenTag) {
                "screen_home" -> node("home_transfer").assertIsFocused()
                "screen_consoles" -> node("system_snes").assertIsFocused()
                // Sem permissão de arquivos (padrão do repositório falso): foco no botão de permitir.
                "screen_transfer" -> node("grant_access").assertIsFocused()
                "screen_search" -> node("key_a").assertIsFocused()
                // Configurações: foco na primeira categoria (Vídeo).
                "screen_settings" -> node("cat_VIDEO").assertIsFocused()
                else -> primary.assertIsFocused()
            }
        }
    }

    /** Salva screenshot em app/build/screenshots quando -Dretroplay.screenshots=true. */
    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
