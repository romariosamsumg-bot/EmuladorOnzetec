package com.retroplay.mobile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.profile.Profile
import com.retroplay.fakes.FakeProfiles
import com.retroplay.fakes.TestStartup
import com.retroplay.feature.profiles.StartupOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** "Quem vai jogar?" no celular: texto legível (claro no fundo escuro) e escolha por toque. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "b+pt+BR-w411dp-h891dp-port-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobileStartupTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    private fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/mobile").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** O texto do nó tem pixels claros (o bug deixava o texto preto sobre o fundo escuro). */
    private fun assertLightText(tag: String) {
        val bmp = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        var brightest = 0.0
        for (x in 0 until bmp.width step 2) for (y in 0 until bmp.height step 2) {
            val c = bmp.getPixel(x, y)
            val l = (0.2126 * ((c shr 16) and 0xFF) + 0.7152 * ((c shr 8) and 0xFF) + 0.0722 * (c and 0xFF)) / 255
            if (l > brightest) brightest = l
        }
        assertTrue("texto de $tag escuro demais (luminância máx. $brightest)", brightest > 0.7)
    }

    @Test
    fun quem_vai_jogar_legivel_e_por_toque() {
        waitFor("screen_profiles")
        compose.mainClock.advanceTimeBy(800)
        screenshot("20_quem_vai_jogar")
        assertLightText("profiles_title")
        assertLightText("profile_1")
        compose.onNodeWithTag("profile_manage").performClick()
        compose.waitForIdle()
        screenshot("21_gerenciar_perfis")
        compose.onNodeWithTag("profile_manage").performClick()
        compose.onNodeWithTag("profile_add").performClick()
        waitFor("screen_profile_editor")
        compose.mainClock.advanceTimeBy(600)
        screenshot("22_novo_perfil")
        assertLightText("screen_profile_editor")
        compose.onNodeWithTag("profile_name").performTextInput("Ana")
    }

    companion object {
        @JvmStatic @BeforeClass
        fun before() {
            TestStartup.options = StartupOptions(intro = false, alwaysAsk = true, welcome = false)
            FakeProfiles.initial = listOf(
                Profile(1, "Hahaha", 0, avatar = "controle", accent = "LARANJA"),
                Profile(2, "Ana", 0, avatar = "gato", accent = "CIANO"),
            )
        }

        @JvmStatic @AfterClass
        fun after() {
            TestStartup.options = StartupOptions(intro = false, alwaysAsk = false, welcome = false)
            FakeProfiles.initial = FakeProfiles.DEFAULT
        }
    }
}
