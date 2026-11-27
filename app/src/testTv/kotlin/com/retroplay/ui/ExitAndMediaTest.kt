package com.retroplay.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** TV: Voltar na Início pergunta antes de sair; Início volta ao topo; tela Mídia. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExitAndMediaTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Leva o foco ao menu lateral (insiste: o 1º Voltar pode chegar antes da Início montar). */
    private fun toDrawer() {
        compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        repeat(5) { if (!focused("nav_home") && !focused("nav_media")) back() }
    }

    @Test
    fun voltar_na_inicio_pergunta_antes_de_sair() {
        library.games.value = listOf(FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"))
        waitFor("hero_play")
        toDrawer()
        node("nav_home").assertIsFocused()
        back()
        waitFor("exit_dialog")
        // Foco em Cancelar: não fecha sem querer.
        node("exit_cancel").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag("exit_dialog")).fetchSemanticsNodes().isEmpty() }
        assertFalse(compose.activity.isFinishing)
        // De novo, agora confirmando.
        toDrawer()
        back()
        waitFor("exit_dialog")
        press(Key.DirectionRight)
        node("exit_confirm").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(compose.activity.isFinishing)
    }

    @Test
    fun inicio_volta_ao_topo_com_foco_no_jogar() {
        library.games.value = (1L..12L).map { FakeLibraryRepository.game(it, SystemRegistry.Ids.SNES, "Jogo $it") }
        waitFor("hero_play")
        // Desce pelas fileiras.
        press(Key.DirectionDown, 3)
        assertFalse(focused("hero_play"))
        toDrawer()
        press(Key.DirectionCenter) // Início
        compose.waitUntil(5_000) { focused("hero_play") }
        node("hero_play").assertIsFocused()
    }

    @Test
    fun midia_lista_prints_e_highlights_e_filtra() {
        waitFor("screen_home")
        toDrawer()
        repeat(8) { if (!focused("nav_media")) press(Key.DirectionDown) }
        node("nav_media").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("media_grid")
        node("media_0").assertExists()
        node("media_1").assertExists()
        // Filtros por D-pad: Tudo → Prints.
        compose.waitUntil(5_000) { focused("media_filter_ALL") }
        press(Key.DirectionRight)
        node("media_filter_PRINTS").assertIsFocused()
        press(Key.DirectionCenter)
        node("media_1").assertDoesNotExist()
    }
}
