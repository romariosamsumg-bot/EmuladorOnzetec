package com.retroplay.feature.game.touch

import androidx.compose.ui.unit.dp
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.tvCanvasScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VirtualPadModelTest {

    // Tela de referência: celular 20:9 deitado, escalado para 540 dp de altura.
    private val w = 1200f
    private val h = 540f

    private fun model(system: com.retroplay.domain.system.GameSystem, six: Boolean = true) =
        VirtualPadModel(VirtualPadSpecs.forSystem(system, six), w, h)

    @Test
    fun todos_os_consoles_cabem_na_tela_sem_botoes_sobrepostos() {
        SystemRegistry.all.forEach { sys ->
            listOf(true, false).forEach { six ->
                val m = model(sys, six)
                m.elements.forEach { e ->
                    assertTrue("${sys.shortName} ${e.id} fora da tela", e.x - e.width / 2 >= 0 && e.x + e.width / 2 <= w && e.y - e.height / 2 >= 0 && e.y + e.height / 2 <= h)
                }
                val circles = m.elements.filter { it.shape == PadShape.CIRCLE && it.id != "dpad" && it.id != "menu" }
                for (i in circles.indices) for (j in i + 1 until circles.size) {
                    val a = circles[i]; val b = circles[j]
                    assertTrue("${sys.shortName}: ${a.id} encosta em ${b.id}", hypot(a.x - b.x, a.y - b.y) >= a.width / 2 + b.width / 2)
                }
                // Cada botão do console (menos o direcional) está na tela, exceto o Mode no modo 3 botões.
                val onScreen = m.elements.mapNotNull { it.button }.toSet()
                val expected = sys.controllerLayout.buttons.map { it.retroPad }.toSet() - VirtualPadSpecs.DIRECTIONS
                val missing = expected - onScreen
                assertTrue("${sys.shortName} sem $missing", missing.isEmpty() || (sys.id == SystemRegistry.Ids.MEGA_DRIVE && !six))
            }
        }
    }

    @Test
    fun tocar_no_centro_de_cada_botao_aperta_ele() {
        SystemRegistry.all.forEach { sys ->
            val m = model(sys)
            m.elements.filter { it.button != null }.forEach { e ->
                assertEquals("${sys.shortName} ${e.id}", setOf(e.button), m.stateFor(listOf(e.x to e.y)).buttons)
            }
        }
    }

    @Test
    fun varios_dedos_e_direcional() {
        val m = model(SystemRegistry.SNES)
        val a = m.elements.first { it.button == RetroPadButton.A }
        val b = m.elements.first { it.button == RetroPadButton.B }
        val d = m.dpad
        val s = m.stateFor(listOf(a.x to a.y, b.x to b.y, (d.x + 60) to (d.y - 60)))
        assertEquals(setOf(RetroPadButton.A, RetroPadButton.B), s.buttons)
        assertEquals(1 to -1, s.dx to s.dy) // diagonal cima-direita
        assertEquals(0 to 0, m.stateFor(listOf(d.x to d.y)).let { it.dx to it.dy }) // zona morta
        assertEquals(-1 to 0, m.stateFor(listOf((d.x - 80) to (d.y + 10))).let { it.dx to it.dy })
        assertEquals(0 to 1, m.stateFor(listOf(d.x to (d.y + 70))).let { it.dx to it.dy })
        // Fora de qualquer botão: nada. Menu no topo.
        assertEquals(PadState(), m.stateFor(listOf(w / 2 to h / 2)))
        val menu = m.elements.first { it.id == "menu" }
        assertTrue(m.stateFor(listOf(menu.x to menu.y)).menu)
        assertFalse(m.stateFor(emptyList()).menu)
    }

    @Test
    fun neo_geo_tem_ficha_em_destaque_e_mega_drive_3_ou_6_botoes() {
        val neo = VirtualPadModel(VirtualPadSpecs.forSystem(SystemRegistry.ARCADE, neoGeo = true), w, h)
        val coin = neo.elements.first { it.button == RetroPadButton.SELECT }
        assertEquals("Ficha", coin.label)
        assertTrue(coin.highlighted)
        assertEquals(listOf("A", "B", "C", "D"), neo.elements.filter { it.shape == PadShape.CIRCLE && it.button != null }.map { it.label }.sorted())
        // Outros jogos de arcade: 6 botões (1-2-3 em cima, 4-5-6 embaixo).
        val luta = model(SystemRegistry.ARCADE)
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), luta.elements.filter { it.shape == PadShape.CIRCLE && it.button != null }.map { it.label }.sorted())
        assertTrue(luta.elements.first { it.label == "A" }.y < luta.elements.first { it.label == "D" }.y)
        assertEquals(6, model(SystemRegistry.MEGA_DRIVE, six = true).elements.count { it.shape == PadShape.CIRCLE && it.button != null })
        assertEquals(3, model(SystemRegistry.MEGA_DRIVE, six = false).elements.count { it.shape == PadShape.CIRCLE && it.button != null })
        assertEquals("Pause", model(SystemRegistry.MASTER_SYSTEM).elements.first { it.button == RetroPadButton.START }.label)
    }

    @Test
    fun escala_da_interface_no_celular() {
        assertEquals(1f, tvCanvasScale(540.dp))
        assertEquals(1f, tvCanvasScale(720.dp)) // tablet/TV grande: não aumenta
        assertEquals(393f / 540f, tvCanvasScale(393.dp), 0.001f)
    }

    @Test
    fun playstation_tem_losango_e_quatro_gatilhos_sem_sobrepor() {
        val m = model(SystemRegistry.PLAYSTATION)
        val pills = m.elements.filter { it.button in setOf(RetroPadButton.L, RetroPadButton.R, RetroPadButton.L2, RetroPadButton.R2) }
        assertEquals(listOf("L1", "R1", "L2", "R2"), pills.map { it.label })
        val l1 = pills[0]; val l2 = pills[2]
        assertEquals(l1.x, l2.x, 0.01f)
        assertTrue("L2 encosta em L1", l2.y - l1.y >= l1.height)
        // △ em cima e ✕ embaixo do losango.
        val tri = m.elements.first { it.label == "△" }
        val cross = m.elements.first { it.label == "✕" }
        assertTrue(tri.y < cross.y)
        assertEquals(tri.x, cross.x, 0.01f)
        // O L2 não invade o direcional.
        assertTrue(l2.y + l2.height / 2 < m.dpad.y - m.dpad.height / 2)
    }

    @Test
    fun posicoes_personalizadas_e_tamanho() {
        val spec = VirtualPadSpecs.forSystem(SystemRegistry.SNES)
        val base = VirtualPadModel(spec, w, h)
        val a = base.elements.first { it.label == "A" }
        val moved = VirtualPadModel(spec, w, h, offsets = mapOf("A" to (-300f to -100f), "dpad" to (40f to 0f)))
        val a2 = moved.elements.first { it.label == "A" }
        assertEquals(a.x - 300f, a2.x, 0.01f)
        assertEquals(a.y - 100f, a2.y, 0.01f)
        assertEquals(base.dpad.x + 40f, moved.dpad.x, 0.01f)
        // O toque segue a posição nova.
        assertEquals(setOf(RetroPadButton.A), moved.stateFor(listOf(a2.x to a2.y)).buttons)
        // Nunca sai da tela.
        val far = VirtualPadModel(spec, w, h, offsets = mapOf("A" to (5000f to 5000f)))
        val a3 = far.elements.first { it.label == "A" }
        assertTrue(a3.x + a3.width / 2 <= w && a3.y + a3.height / 2 <= h)
        // Maior: botões e direcional crescem.
        val big = VirtualPadModel(spec, w, h, scale = 1.3f)
        assertEquals(a.width * 1.3f, big.elements.first { it.label == "A" }.width, 0.01f)
        assertEquals(base.dpad.width * 1.3f, big.dpad.width, 0.01f)
        // Gravação: só o que mudou, e volta igual.
        val enc = PadLayoutCodec.encode(mapOf("A" to (-300f to -100f), "B" to (0f to 0f), "✕" to (1.25f to 2f)))
        assertEquals("A:-300.0,-100.0;✕:1.3,2.0", enc)
        assertEquals(mapOf("A" to (-300f to -100f), "✕" to (1.3f to 2f)), PadLayoutCodec.decode(enc))
        assertTrue(PadLayoutCodec.decode("lixo;x:1").isEmpty())
    }
}
