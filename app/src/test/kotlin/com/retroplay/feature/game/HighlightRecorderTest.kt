package com.retroplay.feature.game

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightRecorderTest {

    /** Quadros a cada 40 ms; quadro-chave a cada segundo (25 quadros). */
    private fun frames(seconds: Int, startUs: Long = 0) = (0 until seconds * 25).map { i -> startUs + i * 40_000L to (i % 25 == 0) }

    @Test
    fun salva_os_ultimos_segundos_comecando_num_quadro_chave() {
        val all = frames(16)
        val clip = HighlightRecorder.pickForTest(all, 5_000_000)
        // Começa no quadro-chave de 10 s (o mais próximo antes do corte de ~11 s).
        assertEquals(10_000_000L, clip.first())
        assertEquals(all.last().first, clip.last())
    }

    @Test
    fun pausa_do_menu_nao_conta_como_tempo_de_jogo() {
        // 4 s de jogo, 5 minutos de menu, mais 4 s de jogo.
        val all = frames(4) + frames(4, startUs = 300_000_000L)
        val active = HighlightRecorder.activeForTest(all.map { it.first })
        // ~8 s de jogo (a pausa conta no máximo 250 ms).
        assertEquals(true, active in 7_800_000L..8_300_000L)
        val clip = HighlightRecorder.pickForTest(all, 6_000_000)
        // Os 6 s atravessam a pausa e começam no 1º bloco.
        assertEquals(true, clip.first() < 4_000_000L)
    }
}
