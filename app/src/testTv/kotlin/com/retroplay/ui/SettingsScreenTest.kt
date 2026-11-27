package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.system.SystemRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

/** Configurações pelo D-pad: categorias, nível por console com indicador, PIN dos pais. */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var profiles: com.retroplay.domain.profile.ProfileRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    /** Árvore mesclada: inclui os textos dos filhos (valor e indicador). */
    private fun merged(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag)

    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    /** O DataStore grava em outra thread: espera em tempo real, deixando a UI processar. */
    private fun waitSetting(check: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!check()) {
            check(System.currentTimeMillis() < end) { "configuração não gravada a tempo" }
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    private fun openSettings() {
        // A Início pode trocar de conteúdo ao montar: insiste no ← até o menu receber o foco.
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty() }
        repeat(5) {
            if (compose.onAllNodes(hasTestTag("nav_home") and androidx.compose.ui.test.isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                press(Key.DirectionLeft)
            }
        }
        node("nav_home").assertIsFocused()
        press(Key.DirectionDown, 5)
        node("nav_settings").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("cat_VIDEO")
        node("cat_VIDEO").assertIsFocused()
    }

    @Test
    fun filtro_personalizado_so_no_super_nintendo() {
        openSettings()
        screenshot("01_video_global")
        // → entra no painel: primeiro o seletor de nível (Global), → Super Nintendo.
        press(Key.DirectionRight)
        node("scope_global").assertIsFocused()
        press(Key.DirectionRight)
        node("scope_snes").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        node("set_video.shader").assertIsFocused()
        merged("set_video.shader").assertTextContains("Herdado do global")
        press(Key.DirectionCenter)
        waitFor("choice_dialog")
        node("choice_inherit").assertIsFocused()
        press(Key.DirectionDown, 2)
        node("choice_crt").assertIsFocused()
        press(Key.DirectionCenter)
        waitSetting { runBlocking { settings.current().get(AppSettings.SHADER, ScopeRef.system(SystemRegistry.Ids.SNES)) } == "crt" }
        compose.waitForIdle()
        merged("set_video.shader").assertTextContains("Personalizado aqui")
        merged("set_video.shader").assertTextContains("CRT")
        screenshot("02_video_snes_override")

        val snap = runBlocking { settings.current() }
        assertEquals("crt", snap.value(AppSettings.SHADER, SystemRegistry.Ids.SNES))
        assertEquals(Scope.SYSTEM, snap.resolve(AppSettings.SHADER, SystemRegistry.Ids.SNES).source)
        assertEquals("none", snap.value(AppSettings.SHADER, SystemRegistry.Ids.MEGA_DRIVE))
        assertEquals("none", snap.value(AppSettings.SHADER))
    }

    @Test
    fun categorias_mostram_as_opcoes_ao_receber_foco() {
        openSettings()
        val expected = listOf(
            "cat_AUDIO" to "set_audio.enabled",
            "cat_CONTROLS" to "remap",
            "cat_EMULATION" to "set_emu.ff_speed",
            "cat_BIOS" to "bios_status",
            "cat_LIBRARY" to "lib_scan",
            "cat_CORES" to "set_cores.channel",
            "cat_INTERFACE" to "set_ui.language",
            "cat_NETWORK" to "set_net.port",
            "cat_STORAGE" to "storage_clear",
            "cat_PARENTAL" to "parental_pin",
            "cat_ABOUT" to "about_version",
        )
        for ((cat, row) in expected) {
            press(Key.DirectionDown)
            node(cat).assertIsFocused()
            waitFor(row)
        }
        screenshot("03_about")
    }

    @Test
    fun pin_dos_pais_protege_as_configuracoes() {
        openSettings()
        press(Key.DirectionDown, 10)
        node("cat_PARENTAL").assertIsFocused()
        press(Key.DirectionRight)
        node("parental_pin").assertIsFocused()
        // Sem PIN, "Proteger as Configurações" não liga.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(false, runBlocking { settings.current() }.value(AppSettings.PARENTAL_SETTINGS))
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        waitFor("pin_dialog")
        node("pin_key_1").assertIsFocused()
        // 1 2 3 4 pelo teclado numérico.
        press(Key.DirectionCenter)
        merged("pin_dots").assertTextContains("●  ○  ○  ○")
        press(Key.DirectionRight); press(Key.DirectionCenter)
        press(Key.DirectionRight); press(Key.DirectionCenter)
        merged("pin_dots").assertTextContains("●  ●  ●  ○")
        press(Key.DirectionDown); press(Key.DirectionLeft, 2)
        node("pin_key_4").assertIsFocused()
        press(Key.DirectionCenter)
        waitSetting { runBlocking { settings.current() }.value(AppSettings.PARENTAL_PIN) == "1234" }
        compose.waitForIdle()
        // Espera a tela refletir o PIN gravado antes de seguir.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("parental_pin") and hasText("Definido", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // O diálogo do PIN fechou: só a janela principal recebe as teclas.
        compose.waitUntil(5_000) { compose.onAllNodes(isRoot()).fetchSemanticsNodes().size == 1 }
        node("parental_pin").assertIsFocused()
        screenshot("04_parental")
    }

    @Test
    fun com_pin_definido_liga_as_protecoes() {
        runBlocking { settings.set(AppSettings.PARENTAL_PIN, ScopeRef.Global, "1234") }
        openSettings()
        press(Key.DirectionDown, 10)
        press(Key.DirectionRight)
        node("parental_pin").assertIsFocused()
        press(Key.DirectionDown, 2)
        node("set_parental.delete").assertIsFocused()
        press(Key.DirectionCenter)
        waitSetting { runBlocking { settings.current() }.value(AppSettings.PARENTAL_DELETE) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("set_parental.delete") and hasText("Ligado", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun configuracoes_bloqueadas_pedem_o_pin() {
        runBlocking {
            settings.set(AppSettings.PARENTAL_PIN, ScopeRef.Global, "4321")
            settings.set(AppSettings.PARENTAL_SETTINGS, ScopeRef.Global, true)
        }
        press(Key.DirectionLeft)
        press(Key.DirectionDown, 5)
        press(Key.DirectionCenter)
        waitFor("pin_dialog")
        node("screen_settings_locked").assertExists()
        // Digita 4 3 2 1: 4 fica abaixo do 1.
        press(Key.DirectionDown); press(Key.DirectionCenter) // 4
        press(Key.DirectionUp); press(Key.DirectionRight, 2); press(Key.DirectionCenter) // 3
        press(Key.DirectionLeft); press(Key.DirectionCenter) // 2
        press(Key.DirectionLeft); press(Key.DirectionCenter) // 1
        waitFor("cat_VIDEO")
        node("cat_VIDEO").assertExists()
    }

    @Test
    fun modo_aes_e_free_play_so_no_neo_geo() {
        openSettings()
        press(Key.DirectionDown, 3)
        node("cat_EMULATION").assertIsFocused()
        press(Key.DirectionRight)
        node("scope_global").assertIsFocused()
        press(Key.DirectionRight, 4)
        node("scope_arcade").assertIsFocused()
        press(Key.DirectionCenter)
        // Desce até as opções do console.
        repeat(20) {
            if (compose.onAllNodes(hasTestTag("set_console.neogeo_mode") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty()) return@repeat
            press(Key.DirectionDown)
        }
        node("set_console.neogeo_mode").assertIsFocused()
        merged("set_console.neogeo_mode").assertTextContains("MVS (fliperama)")
        press(Key.DirectionCenter)
        waitFor("choice_dialog")
        press(Key.DirectionDown, 2)
        node("choice_aes").assertIsFocused()
        press(Key.DirectionCenter)
        waitSetting { runBlocking { settings.current() }.get(com.retroplay.domain.settings.ConsoleSettings.NEOGEO_MODE, ScopeRef.system(SystemRegistry.Ids.ARCADE)) == "aes" }
        press(Key.DirectionDown)
        node("set_console.neogeo_freeplay").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("choice_dialog")
        press(Key.DirectionDown)
        node("choice_true").assertIsFocused()
        press(Key.DirectionCenter)
        waitSetting { runBlocking { settings.current() }.value(com.retroplay.domain.settings.ConsoleSettings.NEOGEO_FREEPLAY, SystemRegistry.Ids.ARCADE) }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("set_console.neogeo_mode") and hasText("AES (console)", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("05_neogeo_options")
        // O Mega Drive não foi afetado.
        assertEquals(false, runBlocking { settings.current() }.value(com.retroplay.domain.settings.ConsoleSettings.NEOGEO_FREEPLAY, SystemRegistry.Ids.MEGA_DRIVE))
    }

    @Test
    fun criar_perfil_pelo_dialogo_e_trocar() {
        openSettings()
        press(Key.DirectionDown, 7)
        node("cat_INTERFACE").assertIsFocused()
        press(Key.DirectionRight)
        node("ui_profiles").assertIsFocused()
        merged("ui_profiles").assertTextContains("Jogador 1")
        press(Key.DirectionCenter)
        waitFor("profiles_dialog")
        node("profile_1").assertIsFocused()
        press(Key.DirectionDown)
        node("profile_new").assertIsFocused()
        press(Key.DirectionCenter)
        // Editor de perfil: o foco começa no nome.
        waitFor("screen_profile_editor")
        node("profile_name").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("text_dialog_input")
        // Perfil novo: a caixa do nome começa vazia; digita ANA.
        node("tkey_A").assertIsFocused()
        press(Key.DirectionCenter) // A
        press(Key.DirectionDown); press(Key.DirectionRight, 4); press(Key.DirectionCenter) // N
        press(Key.DirectionUp); press(Key.DirectionLeft, 4); press(Key.DirectionCenter) // A
        repeat(8) { if (compose.onAllNodes(hasTestTag("text_input_save") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionDown) }
        node("text_input_save").assertIsFocused()
        press(Key.DirectionCenter)
        // De volta ao editor: escolhe o avatar "gato" (2ª fileira, 1º) e a cor verde, e salva.
        compose.onNodeWithTag("profile_preview", useUnmergedTree = true).assertExists()
        node("profile_name").assertIsFocused()
        press(Key.DirectionDown)
        node("avatar_controle").assertIsFocused()
        press(Key.DirectionDown)
        node("avatar_gato").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        node("color_LARANJA").assertIsFocused()
        press(Key.DirectionRight, 4)
        node("color_VERDE").assertIsFocused()
        press(Key.DirectionCenter)
        screenshot("06_profile_editor")
        press(Key.DirectionDown)
        repeat(3) { if (compose.onAllNodes(hasTestTag("profile_save") and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) press(Key.DirectionLeft) }
        node("profile_save").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { runBlocking { profiles.profiles.first() }.size == 2 }
        val ana = runBlocking { profiles.profiles.first() }.last()
        assertEquals("Ana", ana.name)
        assertEquals("gato", ana.avatar)
        assertEquals("VERDE", ana.accent)
        waitSetting { runBlocking { profiles.activeId() } == ana.id }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("profile_${ana.id}") and androidx.compose.ui.test.isSelected().not()).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("07_profiles")
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/settings").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
