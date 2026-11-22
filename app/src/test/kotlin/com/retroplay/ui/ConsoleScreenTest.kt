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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.data.di.CoreRepositoryModule
import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.fakes.FakeCoreRepository
import com.retroplay.feature.game.GameActivity
import com.retroplay.feature.game.GameLauncher
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
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

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(CoreRepositoryModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConsoleScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val fake = FakeCoreRepository()

    @BindValue
    @JvmField
    val repo: CoreRepository = fake

    @Before
    fun setUp() = hiltRule.inject()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    /** Início → ↓ (card SNES) → OK: abre a página do console só com o D-pad. */
    private fun openSnesPage() {
        press(Key.DirectionDown)
        node("system_snes").assertIsFocused()
        press(Key.DirectionCenter)
        node("screen_console_snes").assertExists()
        node("game_testrom").assertIsFocused()
    }

    @Test
    fun jogar_sem_nucleo_baixa_e_abre_o_jogo_ao_terminar() {
        openSnesPage()
        node("core_status").assertTextContains("Ainda não baixado", substring = true)
        screenshot("10_console_sem_nucleo")

        press(Key.DirectionCenter)
        assertEquals(listOf("snes9x2010"), fake.installRequests)
        node("core_status").assertTextContains("Baixando", substring = true)
        fake.state.value = CoreInstallState.Downloading(800_000, 1_600_000)
        compose.waitForIdle()
        screenshot("11_console_baixando")
        assertNull(shadowOf(compose.activity).nextStartedActivity)

        fake.complete()
        compose.waitForIdle()
        node("core_status").assertTextContains("Pronto", substring = true)
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertNotNull("o jogo deveria abrir ao terminar o download", intent)
        assertEquals(GameActivity::class.java.name, intent.component!!.className)
        assertEquals("snes", intent.getStringExtra(GameLauncher.EXTRA_SYSTEM))
        assertEquals("snes9x2010", intent.getStringExtra(GameLauncher.EXTRA_CORE))
        val rom = File(intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH)!!)
        assertTrue("ROM de teste copiada dos assets", rom.isFile && rom.length() == 131072L)
        screenshot("12_console_pronto")
    }

    @Test
    fun com_nucleo_instalado_abre_na_hora() {
        fake.complete()
        openSnesPage()
        press(Key.DirectionCenter)
        assertTrue(fake.installRequests.isEmpty())
        assertEquals(GameActivity::class.java.name, shadowOf(compose.activity).nextStartedActivity.component!!.className)
    }

    @Test
    fun botao_baixar_nucleo_e_alcancavel_pelo_dpad() {
        openSnesPage()
        press(Key.DirectionLeft)
        node("core_download").assertIsFocused()
        // → volta para a lista de jogos.
        press(Key.DirectionRight)
        node("game_testrom").assertIsFocused()
        press(Key.DirectionLeft)
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
    fun voltar_na_pagina_do_console_retorna_ao_inicio() {
        openSnesPage()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        node("screen_home").assertExists()
    }

    @Test
    fun outros_consoles_nao_mostram_a_rom_de_teste() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        node("system_megadrive").assertIsFocused()
        press(Key.DirectionCenter)
        node("screen_console_megadrive").assertExists()
        node("game_testrom").assertDoesNotExist()
        node("pick_file").assertIsFocused()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
