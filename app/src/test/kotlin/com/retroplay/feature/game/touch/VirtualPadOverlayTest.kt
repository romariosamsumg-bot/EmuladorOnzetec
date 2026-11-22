package com.retroplay.feature.game.touch

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.TvCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Celular 20:9 deitado (873×393 dp): multitoque de verdade no controle desenhado. */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w873dp-h393dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VirtualPadOverlayTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dois_dedos_apertam_dois_botoes_e_soltar_libera() {
        val states = mutableListOf<PadState>()
        val spec = VirtualPadSpecs.forSystem(SystemRegistry.NEO_GEO)
        compose.setContent {
            RetroPlayTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B3A5C), Color(0xFF3B6E3A))))) {
                    TvCanvas { VirtualPadOverlay(spec, 0.6f, Color(0xFF8B6CFF), onState = { states += it }) }
                }
            }
        }
        compose.waitForIdle()

        // Coordenadas do modelo (dp na tela de referência) → pixels reais do celular.
        val node = compose.onNodeWithTag("virtual_pad")
        val size = node.fetchSemanticsNode().size
        val scale = size.height / 540f
        val model = VirtualPadModel(spec, size.width / scale, 540f)
        fun px(e: PadElement) = Offset(e.x * scale, e.y * scale)
        val a = model.elements.first { it.button == RetroPadButton.B } // "A" do Neo Geo
        val coin = model.elements.first { it.button == RetroPadButton.SELECT }

        node.performTouchInput {
            down(0, px(a))
            down(1, px(coin))
        }
        assertEquals(setOf(RetroPadButton.B, RetroPadButton.SELECT), states.last().buttons)
        screenshot("pad_neogeo")
        // Arrasta o dedo da ficha para o direcional (deslizar entre controles funciona).
        node.performTouchInput { moveTo(1, Offset((model.dpad.x - 70) * scale, model.dpad.y * scale)) }
        assertEquals(setOf(RetroPadButton.B), states.last().buttons)
        assertEquals(-1, states.last().dx)
        node.performTouchInput { up(0); up(1) }
        assertEquals(PadState(), states.last())
        assertTrue(states.size >= 3)
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/phone").apply { mkdirs() }
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
