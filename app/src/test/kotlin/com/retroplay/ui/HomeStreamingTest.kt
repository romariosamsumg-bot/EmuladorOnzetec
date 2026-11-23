package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.library.CoverType
import com.retroplay.domain.library.DeleteResult
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.fakes.FakeLibraryRepository.Companion.game
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeStreamingTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var library: FakeLibraryRepository

    @Inject
    lateinit var saves: com.retroplay.data.saves.SaveStateStore

    @Inject
    lateinit var cores: com.retroplay.fakes.FakeCoreRepository

    private val snes = SystemRegistry.Ids.SNES
    private val md = SystemRegistry.Ids.MEGA_DRIVE

    @Before
    fun setUp() {
        hiltRule.inject()
        library.games.value = listOf(
            game(1, snes, "Super Mario World", lastPlayedAt = 5_000, playCount = 4, thumbs = 7),
            game(2, snes, "Donkey Kong Country", genre = "Platform"),
            game(3, md, "Sonic The Hedgehog", "sonic.md", favorite = true),
            game(4, md, "Streets of Rage 2", "sor2.md", genre = "Fighting"),
            game(5, snes, "Yoshi's Island", genre = "Platform"),
            game(6, md, "Ristar", "ristar.md", genre = "Platform"),
        )
    }

    private fun press(key: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test
    fun destaque_e_fileiras() {
        waitFor("hero_play")
        node("hero_play").assertIsFocused()
        node("hero_title").assertTextEquals("Super Mario World") // último jogado
        screenshot("30_home_hero")
        // Fileiras visíveis na primeira tela (as de baixo são compostas ao rolar).
        node("row_continue").assertExists()
    }

    @Test
    fun fileiras_por_dpad_nome_no_foco_e_foco_restaurado_ao_voltar() {
        waitFor("hero_play")
        press(Key.DirectionDown)
        // 1ª fileira: Continuar jogando → Super Mario World
        node("card_continue/1").assertIsFocused()
        press(Key.DirectionDown) // Favoritos → Sonic
        node("card_favorites/3").assertIsFocused()
        press(Key.DirectionDown) // Mais populares: Super Mario World (1º do SNES)
        node("card_popular/1").assertIsFocused()
        press(Key.DirectionDown) // Recentes: o mais novo primeiro (id 6)
        node("card_recent/6").assertIsFocused()
        press(Key.DirectionRight)
        node("card_recent/5").assertIsFocused()
        // O nome aparece numa etiqueta logo abaixo do card focado (200 ms); o do anterior some.
        node("card_recent/5_title").assertExists()
        node("card_recent/6_title").assertDoesNotExist()
        screenshot("31_home_fileiras")
        press(Key.DirectionCenter)
        waitFor("screen_details")
        node("details_title").assertTextEquals("Yoshi's Island")
        node("details_play").assertIsFocused()
        back()
        waitFor("card_recent/5")
        // Foco volta ao mesmo card de onde o usuário saiu.
        node("card_recent/5").assertIsFocused()
    }

    @Test
    fun favoritar_trocar_capa_e_apagar_nos_detalhes() {
        waitFor("hero_play")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        waitFor("details_play")
        screenshot("32_detalhes")
        compose.onNodeWithTag("details_played").assertTextContains("tempo total", substring = true)
        press(Key.DirectionRight)
        node("details_favorite").assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(library.games.value.first { it.id == 1L }.isFavorite)
        compose.onNodeWithTag("details_favorite").assertTextContains("Nos favoritos", substring = true)

        // Opções do jogo → capa "Tela de título"
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        waitFor("options_dialog")
        // Foco inicial em "Configurações deste jogo"; ↓ desce para as capas.
        node("options_settings").assertIsFocused()
        press(Key.DirectionDown)
        node("cover_AUTO").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        node("cover_TITLE").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(CoverType.TITLE, library.games.value.first { it.id == 1L }.coverOverride)

        // Apagar: foco inicial em Cancelar (seguro); confirma com → + OK
        node("details_options").assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        waitFor("delete_dialog")
        node("delete_cancel").assertIsFocused()
        screenshot("33_confirmar_apagar")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitForIdle()
        assertEquals(listOf(1L), library.deleted)
        waitFor("screen_home")
    }

    @Test
    fun apagar_sem_permissao_mantem_o_jogo() {
        library.deleteResult = DeleteResult.NO_PERMISSION
        waitFor("hero_play")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        waitFor("details_play")
        repeat(3) { press(Key.DirectionRight) }
        node("details_delete").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitForIdle()
        node("screen_details").assertExists()
        assertTrue(library.games.value.any { it.id == 1L })
    }

    @Test
    fun busca_pelo_teclado_em_grade() {
        // Menu → Buscar
        waitFor("hero_play")
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        node("nav_search").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("key_a")
        node("key_a").assertIsFocused()
        // Digita "so" só com o D-pad: s = linha 3, coluna 1; o = linha 3, coluna 3 (grade de 6 colunas)
        repeat(3) { press(Key.DirectionDown) }
        node("key_s").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionRight); press(Key.DirectionRight)
        node("key_o").assertIsFocused()
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(300)
        node("search_query").assertTextEquals("so")
        waitFor("result_3")
        node("result_2").assertDoesNotExist()
        screenshot("34_busca")
        // → até os resultados e abre o Sonic
        repeat(6) { if (runCatching { node("result_3").assertIsFocused() }.isFailure) press(Key.DirectionRight) }
        node("result_3").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("details_title")
        node("details_title").assertTextEquals("Sonic The Hedgehog")
    }

    @Test
    fun continuar_do_save_abre_o_jogo_no_save() {
        saves.write(snes, "Super Mario World.sfc", 0, byteArrayOf(1, 2, 3))
        cores.complete()
        waitFor("hero_play")
        press(Key.DirectionDown)
        node("card_continue/1").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("details_continue")
        node("details_continue").assertIsFocused()
        compose.onNodeWithTag("details_save").assertTextContains("save automático", substring = true)
        screenshot("35_detalhes_continuar")
        press(Key.DirectionCenter)
        var intent: android.content.Intent? = null
        repeat(100) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            intent = org.robolectric.Shadows.shadowOf(compose.activity).peekNextStartedActivity()
            if (intent != null) return@repeat
            Thread.sleep(20)
        }
        assertEquals(0, intent!!.getIntExtra(com.retroplay.feature.game.GameLauncher.EXTRA_LOAD_SLOT, -1))
        assertEquals(1L, intent!!.getLongExtra(com.retroplay.feature.game.GameLauncher.EXTRA_GAME_ID, -1))
        saves.delete(snes, "Super Mario World.sfc", 0)
    }

    @Test
    fun favoritos_em_grade() {
        waitFor("hero_play")
        press(Key.DirectionLeft)
        repeat(3) { press(Key.DirectionDown) }
        node("nav_favorites").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("fav_3")
        node("fav_3").assertIsFocused()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
