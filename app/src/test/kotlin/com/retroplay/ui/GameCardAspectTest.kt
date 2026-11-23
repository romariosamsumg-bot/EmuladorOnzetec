package com.retroplay.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.domain.library.CoverUrls
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.ui.components.CoverAspects
import com.retroplay.ui.components.GameCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O card assume a proporção da capa encontrada (e usa a do console enquanto ela não chega). */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GameCardAspectTest {

    @get:Rule
    val compose = createComposeRule()

    private fun ratioOf(tag: String): Float {
        val b = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
        return (b.right - b.left).value / (b.bottom - b.top).value
    }

    @Test
    fun sem_capa_usa_a_proporcao_do_console_e_com_capa_a_da_imagem() {
        val snes = SystemRegistry.byId(SystemRegistry.Ids.SNES)!!
        val md = SystemRegistry.byId(SystemRegistry.Ids.MEGA_DRIVE)!!
        val semCapa = FakeLibraryRepository.game(1, SystemRegistry.Ids.MEGA_DRIVE, "Jogo Sem Capa Carregada")
        // Capa japonesa de SNES em pé (como algumas da libretro-thumbnails): o card fica em pé também.
        val comCapa = FakeLibraryRepository.game(2, SystemRegistry.Ids.SNES, "Umihara Kawase", thumbs = 7)
        CoverAspects.put(CoverUrls.url(snes, comCapa)!!, 0.72f)
        compose.setContent {
            androidx.compose.foundation.layout.Row {
                GameCard(md, semCapa, semCapa.title, {}, width = 150.dp, testTag = "a")
                GameCard(snes, comCapa, comCapa.title, {}, width = 150.dp, testTag = "b")
            }
        }
        assertEquals(md.coverAspect, ratioOf("a"), 0.02f)
        assertEquals(0.72f, ratioOf("b"), 0.02f)
        // Mesma altura nas fileiras.
        val ha = compose.onNodeWithTag("a").getBoundsInRoot().let { it.bottom - it.top }
        val hb = compose.onNodeWithTag("b").getBoundsInRoot().let { it.bottom - it.top }
        assertEquals(ha.value, hb.value, 0.5f)
    }

    @Test
    fun proporcoes_absurdas_sao_limitadas() {
        assertEquals(2.0f, CoverAspects.clamp(9f))
        assertEquals(0.5f, CoverAspects.clamp(0.1f))
        assertEquals(1.38f, CoverAspects.clamp(1.38f))
    }
}
