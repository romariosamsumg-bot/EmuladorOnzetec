package com.retroplay.feature.game.touch

import androidx.compose.ui.graphics.Color
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry

/** Estilo do controle na tela: inspirado no controle original do console, ou simples (translúcido). */
enum class PadStyle { CLASSIC, MINIMAL;
    companion object {
        fun of(value: String) = if (value == "minimal") MINIMAL else CLASSIC
    }
}

/**
 * Cores de um controle "clássico": lembram o controle original do console (cores e formato dos
 * botões), sem logotipos nem desenhos de marca.
 * @param face cor de cada botão de ação pelo rótulo; [faceText] a cor do rótulo/símbolo.
 * @param stick direcional como alavanca de fliperama (bolinha), em vez da cruz.
 */
data class PadSkin(
    val face: Map<String, Color>,
    val faceDefault: Color,
    val faceText: Map<String, Color> = emptyMap(),
    val faceTextDefault: Color = Color.White,
    val dpad: Color,
    val dpadLine: Color,
    val pill: Color,
    val pillText: Color = Color.White,
    val shoulder: Color = pill,
    val shoulderText: Color = pillText,
    val stick: Boolean = false,
    val stickBall: Color = Color(0xFFE53935),
)

object PadSkins {
    private val dark = Color(0xFF202026)

    /** Controle clássico do console (null = usa o estilo simples). */
    fun forSystem(id: SystemId): PadSkin? = when (id) {
        // Super Nintendo: losango colorido (como o controle japonês/europeu), cinza-claro.
        SystemRegistry.Ids.SNES -> PadSkin(
            face = mapOf("X" to Color(0xFF3B5BDB), "A" to Color(0xFFD7263D), "B" to Color(0xFFF2C14E), "Y" to Color(0xFF2E9E5B)),
            faceDefault = Color(0xFF6B5FA8),
            faceText = mapOf("B" to Color(0xFF2B2100)),
            dpad = Color(0xFF2B2B33), dpadLine = Color(0xFF5A5A66),
            pill = Color(0xFF7D7D88), shoulder = Color(0xFFC9C9D1), shoulderText = Color(0xFF2B2B33),
        )
        // Mega Drive: botões pretos grandes com letras claras.
        SystemRegistry.Ids.MEGA_DRIVE -> PadSkin(
            face = emptyMap(), faceDefault = Color(0xFF16161A), faceTextDefault = Color(0xFFE6E6EA),
            dpad = Color(0xFF16161A), dpadLine = Color(0xFF4A4A52),
            pill = Color(0xFF2E2E35),
        )
        // Master System: preto, botões 1 e 2.
        SystemRegistry.Ids.MASTER_SYSTEM -> PadSkin(
            face = mapOf("2" to Color(0xFF2A2A30)), faceDefault = Color(0xFF1C1C21), faceTextDefault = Color(0xFFF2F2F2),
            dpad = Color(0xFF1C1C21), dpadLine = Color(0xFF55555E),
            pill = Color(0xFFB8322E),
        )
        // Fliperama: alavanca de bolinha e botões coloridos.
        SystemRegistry.Ids.ARCADE -> PadSkin(
            face = mapOf(
                "A" to Color(0xFFE53935), "B" to Color(0xFFFDD835), "C" to Color(0xFF43A047),
                "D" to Color(0xFF1E88E5), "E" to Color(0xFFF5F5F5), "F" to Color(0xFF8E24AA),
            ),
            faceDefault = Color(0xFFE53935),
            faceText = mapOf("B" to Color(0xFF332B00), "E" to Color(0xFF222222)),
            dpad = Color(0xFF1A1A1F), dpadLine = Color(0xFF45454E),
            pill = Color(0xFF3A3A42), stick = true,
        )
        // PlayStation: botões cinza-escuro com símbolos coloridos, gatilhos cinza.
        SystemRegistry.Ids.PLAYSTATION -> PadSkin(
            face = emptyMap(), faceDefault = Color(0xFF2A2B30),
            faceText = mapOf("△" to Color(0xFF3CC3A0), "○" to Color(0xFFE5484D), "✕" to Color(0xFF7B96F2), "□" to Color(0xFFE77DB5)),
            dpad = Color(0xFF3A3B42), dpadLine = Color(0xFF62636C),
            pill = dark, shoulder = Color(0xFF55565E),
        )
        else -> null
    }
}
