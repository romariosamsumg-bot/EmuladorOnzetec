package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.fakes.FakeCoreRepository
import com.retroplay.feature.game.GameActivity
import com.retroplay.feature.game.GameLauncher
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.system.SystemRegistry

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsoleScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var fake: FakeCoreRepository

    @Inject
    lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    /** A lista é carregada fora da thread principal: espera o item existir (tempo real). */
    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    /** Espera uma condição processando a fila da thread principal (coroutines, Room, IO). */
    private fun eventually(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("condição não satisfeita")
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    /** Menu → Consoles → card SNES → OK: abre a página do console só com o D-pad. */
    private fun openConsoles() {
        // Espera a Início carregar e pôr o foco no conteúdo (senão o ← pode se perder).
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        // A Início pode trocar de conteúdo quando a biblioteca chega: insiste no ← até o menu.
        repeat(5) {
            if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("nav_home") and androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                press(Key.DirectionLeft)
            }
        }
        node("nav_home").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        node("nav_consoles").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("system_snes")
        node("system_snes").assertIsFocused()
    }

    /** Página do SNES com um jogo na biblioteca; o foco começa no 1º jogo. */
    private fun openSnesPage() {
        library.games.value = listOf(FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"))
        openConsoles()
        press(Key.DirectionCenter)
        node("screen_console_snes").assertExists()
        waitFor("game_1")
        node("game_1").assertIsFocused()
    }

    /** Abre os detalhes do jogo focado e aperta "Jogar". */
    private fun playFocused() {
        press(Key.DirectionCenter)
        waitFor("details_play")
        node("details_play").assertIsFocused()
        press(Key.DirectionCenter)
    }

    @Test
    fun jogar_sem_nucleo_baixa_e_abre_o_jogo_ao_terminar() {
        openSnesPage()
        node("core_status").assertTextContains("Ainda não baixado", substring = true)
        screenshot("10_console_sem_nucleo")

        playFocused()
        eventually { fake.installRequests.isNotEmpty() }
        compose.waitForIdle()
        assertEquals(listOf("snes9x2010"), fake.installRequests)
        fake.state.value = CoreInstallState.Downloading(800_000, 1_600_000)
        compose.waitForIdle()
        screenshot("11_console_baixando")
        assertNull(shadowOf(compose.activity).nextStartedActivity)

        fake.complete()
        compose.waitForIdle()
        eventually { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertNotNull("o jogo deveria abrir ao terminar o download", intent)
        assertEquals(GameActivity::class.java.name, intent.component!!.className)
        assertEquals("snes", intent.getStringExtra(GameLauncher.EXTRA_SYSTEM))
        assertEquals("snes9x2010", intent.getStringExtra(GameLauncher.EXTRA_CORE))
        assertEquals("/storage/emulated/0/Jogos/Super Mario World.sfc", intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH))
        screenshot("12_console_pronto")
    }

    @Test
    fun com_nucleo_instalado_abre_na_hora() {
        fake.complete()
        openSnesPage()
        playFocused()
        eventually { shadowOf(compose.activity).peekNextStartedActivity() != null }
        assertTrue(fake.installRequests.isEmpty())
        assertEquals(GameActivity::class.java.name, shadowOf(compose.activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun botao_baixar_nucleo_e_alcancavel_pelo_dpad() {
        openSnesPage()
        // ↑ passa pelos filtros e chega ao botão do cabeçalho.
        repeat(3) { if (runCatching { node("core_download").assertIsFocused() }.isFailure) press(Key.DirectionUp) }
        node("core_download").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf("snes9x2010"), fake.installRequests)
    }

    @Test
    fun erro_de_rede_mostra_mensagem_e_tentar_de_novo() {
        fake.state.value = CoreInstallState.Failed(CoreError.NO_NETWORK)
        openSnesPage()
        node("core_status").assertTextContains("Sem internet", substring = true)
        compose.onNodeWithTag("core_download").assertTextContains("Tentar de novo", substring = true)
        screenshot("13_console_erro")
    }

    @Test
    fun voltar_na_pagina_do_console_retorna_a_lista_de_consoles() {
        openSnesPage()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        node("screen_consoles").assertExists()
        // Foco volta ao card do SNES.
        waitFor("system_snes")
    }

    @Test
    fun jogo_da_biblioteca_abre_detalhes_e_joga() {
        library.games.value = listOf(
            FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"),
            FakeLibraryRepository.game(2, SystemRegistry.Ids.MEGA_DRIVE, "Sonic The Hedgehog", "sonic.md"),
        )
        fake.complete()
        openConsoles()
        // Cada console mostra quantos jogos tem.
        compose.onNodeWithTag("count_snes", useUnmergedTree = true).assertTextContains("1 jogo", substring = true)
        compose.onNodeWithTag("count_arcade", useUnmergedTree = true).assertTextContains("nenhum jogo", substring = true)
        press(Key.DirectionCenter)
        waitFor("game_1")
        node("game_1").assertIsFocused()
        node("game_2").assertDoesNotExist() // é de outro console
        // O nome aparece abaixo do card depois de 600 ms de foco.
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        node("game_1_title").assertExists()
        screenshot("14_console_grade")
        press(Key.DirectionCenter)
        waitFor("details_play")
        node("details_play").assertIsFocused()
        press(Key.DirectionCenter)
        eventually { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals("/storage/emulated/0/Jogos/Super Mario World.sfc", intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH))
        assertEquals(1L, intent.getLongExtra(GameLauncher.EXTRA_GAME_ID, -1))
    }

    @Test
    fun filtro_favoritos_e_romset_incompleto() {
        library.games.value = listOf(
            FakeLibraryRepository.game(3, SystemRegistry.Ids.ARCADE, "Metal Slug", "mslug.zip", romsetStatus = RomsetStatus.INCOMPLETE, missing = 3),
            FakeLibraryRepository.game(4, SystemRegistry.Ids.ARCADE, "Garou", "garou.zip", favorite = true),
        )
        openConsoles()
        // Neo Geo fica na 2ª linha da grade de consoles, abaixo do SNES.
        press(Key.DirectionDown)
        node("system_arcade").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("game_4")
        // A–Z: Garou primeiro.
        node("game_4").assertIsFocused()
        press(Key.DirectionRight)
        node("game_3").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("details_romset")
        compose.onNodeWithTag("details_romset").assertTextContains("faltam 3 arquivos", substring = true)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("game_3")
        // Voltou para o mesmo card.
        node("game_3").assertIsFocused()
        press(Key.DirectionUp)
        repeat(4) { if (runCatching { node("filter_FAVORITES").assertIsFocused() }.isFailure) press(Key.DirectionRight) }
        node("filter_FAVORITES").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitForIdle()
        node("game_3").assertDoesNotExist()
        node("game_4").assertExists()
    }

    @Test
    fun console_sem_jogos_mostra_aviso_e_foca_os_filtros() {
        openConsoles()
        press(Key.DirectionRight)
        node("system_megadrive").assertIsFocused()
        press(Key.DirectionCenter)
        node("screen_console_megadrive").assertExists()
        waitFor("console_empty")
        // Sem jogos: o foco vai para os filtros (nunca fica perdido).
        node("filter_AZ").assertIsFocused()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
