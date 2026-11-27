package com.retroplay.mobile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.BuildConfig
import com.retroplay.MainActivity
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeCoreRepository
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.feature.game.GameActivity
import com.retroplay.feature.game.GameLauncher
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

/** App de celular em pé (Pixel-like 411×891 dp): barra inferior, telas em coluna, toque. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "b+pt+BR-w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobileAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var library: FakeLibraryRepository
    @Inject lateinit var cores: FakeCoreRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        waitFor(tag)
        node(tag).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/mobile").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed() {
        library.games.value = listOf(
            FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"),
            FakeLibraryRepository.game(2, SystemRegistry.Ids.MEGA_DRIVE, "Sonic The Hedgehog", "sonic.md"),
            FakeLibraryRepository.game(3, SystemRegistry.Ids.SNES, "Donkey Kong Country", favorite = true),
        )
    }

    @Test
    fun e_o_app_de_celular() {
        assertFalse(BuildConfig.TV_APP)
    }

    @Test
    fun biblioteca_vazia_leva_a_adicionar_jogos() {
        waitFor("home_empty_state")
        node("bottom_bar").assertIsDisplayed()
        screenshot("01_inicio_vazia")
        tap("home_empty_state_action")
        waitFor("screen_library")
        node("import_files").assertIsDisplayed()
        screenshot("02_adicionar_jogos")
    }

    @Test
    fun inicio_detalhes_e_jogar() {
        seed()
        cores.complete()
        waitFor("hero")
        screenshot("03_inicio")
        tap("game_1")
        waitFor("screen_details")
        node("details_title").assertTextContains("Super Mario World")
        screenshot("04_detalhes")
        node("details_play").performClick()
        repeat(200) {
            if (shadowOf(compose.activity).peekNextStartedActivity() != null) return@repeat
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(GameActivity::class.java.name, intent.component!!.className)
        assertEquals("snes", intent.getStringExtra(GameLauncher.EXTRA_SYSTEM))
    }

    @Test
    fun abas_consoles_busca_favoritos_e_ajustes() {
        seed()
        tap("tab_consoles")
        waitFor("system_snes")
        node("count_snes").assertTextContains("2 jogos", substring = true)
        screenshot("05_consoles")
        tap("system_snes")
        waitFor("screen_console_snes")
        waitFor("game_3")
        node("game_2").assertDoesNotExist()
        screenshot("06_console")

        tap("tab_search")
        waitFor("search_field")
        node("search_field").performTextInput("sonic")
        compose.mainClock.advanceTimeBy(500)
        waitFor("game_2")
        node("game_1").assertDoesNotExist()
        screenshot("07_busca")

        tap("tab_favorites")
        waitFor("game_3")
        node("game_1").assertDoesNotExist()

        tap("tab_settings")
        waitFor("cat_VIDEO")
        screenshot("08_ajustes")
        tap("cat_CONTROLS")
        waitFor("settings_list")
        screenshot("09_ajustes_controles")
        tap("settings_back")
        waitFor("cat_VIDEO")
    }

    @Test
    fun opcoes_do_jogo_em_folha_inferior() {
        seed()
        waitFor("game_1")
        tap("game_1")
        tap("details_options")
        waitFor("options_sheet")
        screenshot("10_opcoes")
        // Campo de texto dentro de janela não fica "ocioso" no Robolectric: confere só as opções.
        node("options_rename").assertExists()
        node("options_settings").assertExists()
        node("cover_AUTO").assertExists()
    }

    @Test
    fun deitado_tambem_funciona() {
        seed()
        waitFor("hero")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        org.robolectric.RuntimeEnvironment.setQualifiers("b+pt+BR-w891dp-h411dp-land-xxhdpi")
        compose.waitForIdle()
        waitFor("bottom_bar")
        screenshot("11_inicio_deitado")
    }
}
