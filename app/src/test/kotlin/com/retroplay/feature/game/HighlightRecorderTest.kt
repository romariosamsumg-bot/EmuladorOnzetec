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

    @Test
    fun som_do_trecho_comeca_no_primeiro_quadro_e_tem_a_duracao_do_video() {
        // 3 trechos de 0,5 s a 1000 Hz (estéreo), com o valor = índice do trecho.
        val rate = 1000
        val chunks = (0 until 3).map { k -> (k * 500_000_000L) to ShortArray(500 * 2) { k.toShort() } }
        // Vídeo começa em 0,75 s e dura 0,5 s: metade do trecho 1 e metade do trecho 2.
        val pcm = HighlightRecorder.sliceAudio(chunks, rate, fromNs = 750_000_000L, durationUs = 500_000L)
        assertEquals(500 * 2, pcm.size)
        assertEquals(1.toShort(), pcm.first())
        assertEquals(2.toShort(), pcm.last())
    }

    @Test
    fun reamostragem_mantem_a_duracao() {
        val pcm = ShortArray(32040 * 2) { (it % 100).toShort() } // 1 s a 32040 Hz
        val out = HighlightRecorder.resampleStereo(pcm, 32040, 44100)
        assertEquals(44100 * 2, out.size)
    }
}
