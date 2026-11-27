package com.retroplay.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.fakes.FakeLibraryRepository.Companion.game
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** Celular: tudo funciona só com toques na tela (sem controle remoto). */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var library: FakeLibraryRepository
    @Inject lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        library.games.value = listOf(
            game(1, SystemRegistry.Ids.SNES, "Super Mario World", lastPlayedAt = 5_000, playCount = 2),
            game(2, SystemRegistry.Ids.MEGA_DRIVE, "Sonic The Hedgehog", "sonic.md"),
        )
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tap(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        node(tag).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    @Test
    fun abrir_jogo_e_favoritar_tocando() {
        tap("card_continue/1")
        node("screen_details").assertExists()
        tap("details_favorite")
        assertTrue(library.games.value.first { it.id == 1L }.isFavorite)
        // O item tocado também recebe o foco (as telas continuam coerentes com o controle remoto).
        node("details_favorite").assertIsFocused()
    }

    @Test
    fun menu_lateral_e_configuracoes_por_toque() {
        tap("nav_settings")
        node("screen_settings").assertExists()
        tap("cat_INTERFACE")
        // Tocar na categoria a seleciona e leva às opções dela.
        node("cat_INTERFACE").assertIsSelected()
        tap("set_ui.theme")
        tap("choice_oled")
        compose.waitUntil(5_000) { runBlocking { settings.current() }.value(AppSettings.THEME) == "oled" }
        compose.onNodeWithTag("set_ui.theme").assertTextContains("Preto OLED", substring = true)
        tap("nav_search")
        tap("key_s")
        tap("key_o")
        compose.onNodeWithTag("search_query", useUnmergedTree = true).assertTextContains("so", substring = true, ignoreCase = true)
    }
}
