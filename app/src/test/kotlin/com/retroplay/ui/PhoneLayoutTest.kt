package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
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

/** Celular 20:9 deitado: a interface de TV cabe inteira (escala) e funciona por toque. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "w873dp-h393dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneLayoutTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        library.games.value = listOf(
            game(1, SystemRegistry.Ids.SNES, "Super Mario World", lastPlayedAt = 5_000, playCount = 2),
            game(2, SystemRegistry.Ids.MEGA_DRIVE, "Sonic The Hedgehog", "sonic.md", favorite = true),
            game(3, SystemRegistry.Ids.ARCADE, "Metal Slug", "mslug.zip"),
            game(4, SystemRegistry.Ids.ARCADE, "Street Fighter II", "sf2.zip"),
        )
    }

    private fun tap(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    @Test
    fun telas_cabem_e_respondem_ao_toque() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("hero", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // A interface usa toda a altura do celular, sem cortar: a raiz tem 393 dp na vertical.
        val root = compose.onRoot().fetchSemanticsNode().size
        assertTrue("tela deitada", root.width > root.height)
        // O botão Jogar do destaque fica inteiro dentro da tela.
        val play = compose.onNodeWithTag("hero_play", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(play.bottom <= root.height && play.right <= root.width)
        shot("01_home")
        tap("card_continue/1")
        compose.onNodeWithTag("screen_details", useUnmergedTree = true).fetchSemanticsNode()
        shot("02_details")
        tap("nav_consoles")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("bios_badge_arcade", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        shot("04_consoles")
        tap("nav_settings")
        tap("cat_CONTROLS")
        shot("03_settings")
        assertEquals(1, compose.onAllNodesWithTag("set_controls.touch_pad").fetchSemanticsNodes().size)
    }

    private fun shot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/phone").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
