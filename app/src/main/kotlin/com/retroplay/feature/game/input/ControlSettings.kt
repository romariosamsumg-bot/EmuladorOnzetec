package com.retroplay.feature.game.input

import android.view.KeyEvent
import com.retroplay.domain.system.RetroPadButton

/**
 * Formato do mapeamento salvo nas configurações: "tecla Android=BOTÃO_RETROPAD,..."
 * (ex.: "96=B,97=A"). Vazio = mapeamento posicional padrão.
 */
object MappingCodec {
    fun encode(map: Map<Int, RetroPadButton>): String = map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value.name}" }

    fun decode(raw: String): Map<Int, RetroPadButton> = raw.split(',').mapNotNull { part ->
        val (k, v) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val key = k.trim().toIntOrNull() ?: return@mapNotNull null
        val button = runCatching { RetroPadButton.valueOf(v.trim()) }.getOrNull() ?: return@mapNotNull null
        key to button
    }.toMap()

    /**
     * Mapa final do gamepad: o padrão posicional, trocado A/B e X/Y no layout Nintendo, e por cima
     * as teclas remapeadas pelo usuário (uma tecla física por botão do RetroPad).
     */
    fun gamepadMap(custom: Map<Int, RetroPadButton>, nintendoLayout: Boolean): Map<Int, RetroPadButton> {
        val base = InputRouter.DEFAULT_GAMEPAD.toMutableMap()
        if (nintendoLayout) {
            base[KeyEvent.KEYCODE_BUTTON_A] = RetroPadButton.A
            base[KeyEvent.KEYCODE_BUTTON_B] = RetroPadButton.B
            base[KeyEvent.KEYCODE_BUTTON_X] = RetroPadButton.X
            base[KeyEvent.KEYCODE_BUTTON_Y] = RetroPadButton.Y
        }
        if (custom.isEmpty()) return base
        // Remove as teclas que antes levavam aos botões remapeados e aplica as novas.
        val remapped = custom.values.toSet()
        base.entries.removeAll { it.value in remapped && it.key !in custom }
        return base + custom
    }

    fun decodeTurbo(raw: String): Set<RetroPadButton> =
        raw.split(',').mapNotNull { runCatching { RetroPadButton.valueOf(it.trim()) }.getOrNull() }.toSet()

    fun encodeTurbo(buttons: Set<RetroPadButton>): String = buttons.sortedBy { it.ordinal }.joinToString(",") { it.name }

    /** "descritor=porta;..." → mapa (portas 0–3). */
    fun decodePorts(raw: String): Map<String, Int> = raw.split(';').mapNotNull { part ->
        val i = part.lastIndexOf('=')
        if (i <= 0) return@mapNotNull null
        part.substring(i + 1).toIntOrNull()?.takeIf { it in 0..3 }?.let { part.substring(0, i) to it }
    }.toMap()

    fun encodePorts(map: Map<String, Int>): String = map.entries.joinToString(";") { "${it.key}=${it.value}" }
}

/** Perfil do gamepad pelo fabricante (USB vendor id). */
enum class GamepadProfile(val label: String) {
    XBOX("Xbox"), PLAYSTATION("PlayStation"), EIGHT_BITDO("8BitDo"), GENERIC("Genérico");

    companion object {
        fun fromVendor(vendorId: Int): GamepadProfile = when (vendorId) {
            0x045E -> XBOX
            0x054C -> PLAYSTATION
            0x2DC8 -> EIGHT_BITDO
            else -> GENERIC
        }
    }
}
