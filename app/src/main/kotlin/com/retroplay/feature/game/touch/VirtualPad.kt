package com.retroplay.feature.game.touch

import com.retroplay.domain.system.ConsoleButton
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.domain.system.SystemRegistry
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Controle virtual na tela (celular). A geometria é pura (em dp, na tela de referência da TV)
 * para ser testada sem Android: o desenho e o toque usam exatamente as mesmas posições.
 */
data class VirtualPadSpec(
    /** Botões de ação em linhas, de cima para baixo. */
    val face: List<List<ConsoleButton>>,
    /** Losango (SNES): cima, direita, baixo, esquerda. */
    val diamond: Boolean = false,
    val shoulders: List<ConsoleButton> = emptyList(),
    /** Botões pequenos no centro de baixo (Select/Start/Ficha/Pause). */
    val center: List<ConsoleButton> = emptyList(),
)

object VirtualPadSpecs {
    fun forSystem(system: GameSystem, sixButtons: Boolean = true): VirtualPadSpec {
        val b = system.controllerLayout.buttons
        fun of(rp: RetroPadButton) = b.first { it.retroPad == rp }
        return when (system.id) {
            SystemRegistry.Ids.SNES -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.X), of(RetroPadButton.A), of(RetroPadButton.B), of(RetroPadButton.Y))),
                diamond = true,
                shoulders = listOf(of(RetroPadButton.L), of(RetroPadButton.R)),
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            SystemRegistry.Ids.MEGA_DRIVE -> {
                // Na ordem do controle: A B C embaixo, X Y Z em cima (6 botões).
                val bottom = listOf(of(RetroPadButton.Y), of(RetroPadButton.B), of(RetroPadButton.A))
                val top = listOf(of(RetroPadButton.L), of(RetroPadButton.X), of(RetroPadButton.R))
                VirtualPadSpec(
                    face = if (sixButtons) listOf(top, bottom) else listOf(bottom),
                    center = if (sixButtons) listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)) else listOf(of(RetroPadButton.START)),
                )
            }
            SystemRegistry.Ids.MASTER_SYSTEM -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.B), of(RetroPadButton.A))),
                center = listOf(of(RetroPadButton.START)),
            )
            SystemRegistry.Ids.NEO_GEO -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.Y), of(RetroPadButton.X)), listOf(of(RetroPadButton.B), of(RetroPadButton.A))),
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            // Console novo: tudo que não é direcional vira botão de ação.
            else -> VirtualPadSpec(face = b.filter { it.retroPad !in DIRECTIONS }.chunked(3))
        }
    }

    val DIRECTIONS = setOf(RetroPadButton.UP, RetroPadButton.DOWN, RetroPadButton.LEFT, RetroPadButton.RIGHT)
}

enum class PadShape { CIRCLE, PILL }

/** Um elemento desenhado: botão, direcional ou menu. Coordenadas do centro, em dp. */
data class PadElement(
    val id: String,
    val shape: PadShape,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val label: String,
    val button: RetroPadButton? = null,
    val highlighted: Boolean = false,
) {
    fun hit(px: Float, py: Float, margin: Float): Boolean = when (shape) {
        PadShape.CIRCLE -> hypot(px - x, py - y) <= width / 2 + margin
        PadShape.PILL -> abs(px - x) <= width / 2 + margin && abs(py - y) <= height / 2 + margin
    }
}

/** Saída do controle num instante: botões pressionados, direcional (-1, 0, 1) e menu. */
data class PadState(val buttons: Set<RetroPadButton> = emptySet(), val dx: Int = 0, val dy: Int = 0, val menu: Boolean = false)

class VirtualPadModel(spec: VirtualPadSpec, val width: Float, val height: Float) {
    val elements: List<PadElement>
    val dpad: PadElement

    init {
        val list = ArrayList<PadElement>()
        dpad = PadElement("dpad", PadShape.CIRCLE, EDGE + DPAD_RADIUS, height - EDGE - DPAD_RADIUS, DPAD_RADIUS * 2, DPAD_RADIUS * 2, "")
        list += dpad
        val cx = width - EDGE - CLUSTER_RADIUS
        val cy = height - EDGE - CLUSTER_RADIUS
        if (spec.diamond && spec.face.firstOrNull()?.size == 4) {
            val (top, right, bottom, left) = spec.face.first()
            listOf(top to (0f to -SPACING), right to (SPACING to 0f), bottom to (0f to SPACING), left to (-SPACING to 0f)).forEach { (b, o) ->
                list += face(b, cx + o.first, cy + o.second)
            }
        } else {
            val rows = spec.face
            val y0 = cy + (rows.size - 1) * SPACING / 2
            rows.reversed().forEachIndexed { k, row ->
                val y = y0 - k * SPACING
                row.forEachIndexed { j, b ->
                    val x = cx + (j - (row.size - 1) / 2f) * SPACING + k * STAGGER - (rows.size - 1) * STAGGER / 2
                    list += face(b, x, y)
                }
            }
        }
        spec.center.forEachIndexed { i, b ->
            val x = width / 2 + (i - (spec.center.size - 1) / 2f) * (PILL_W + 24f)
            list += PadElement(b.label, PadShape.PILL, x, height - EDGE / 2 - PILL_H / 2, PILL_W, PILL_H, b.label, b.retroPad, b.highlighted)
        }
        spec.shoulders.forEachIndexed { i, b ->
            val x = if (i == 0) EDGE + SHOULDER_W / 2 else width - EDGE - SHOULDER_W / 2
            list += PadElement(b.label, PadShape.PILL, x, EDGE + SHOULDER_H / 2, SHOULDER_W, SHOULDER_H, b.label, b.retroPad)
        }
        list += PadElement("menu", PadShape.CIRCLE, width / 2, EDGE / 2 + MENU_RADIUS, MENU_RADIUS * 2, MENU_RADIUS * 2, "☰")
        elements = list
    }

    private fun face(b: ConsoleButton, x: Float, y: Float) =
        PadElement(b.label, PadShape.CIRCLE, x, y, BUTTON_RADIUS * 2, BUTTON_RADIUS * 2, b.label, b.retroPad, b.highlighted)

    /** Junta todos os dedos na tela num estado; cada dedo aperta no máximo um botão (o mais próximo). */
    fun stateFor(points: List<Pair<Float, Float>>): PadState {
        val buttons = HashSet<RetroPadButton>()
        var dx = 0
        var dy = 0
        var menu = false
        for ((px, py) in points) {
            if (dpad.hit(px, py, DPAD_RADIUS * 0.35f)) {
                val (x, y) = direction(px - dpad.x, py - dpad.y)
                if (x != 0) dx = x
                if (y != 0) dy = y
                continue
            }
            val hit = elements.filter { it !== dpad && it.hit(px, py, HIT_MARGIN) }.minByOrNull { hypot(px - it.x, py - it.y) } ?: continue
            if (hit.id == "menu") menu = true else hit.button?.let(buttons::add)
        }
        return PadState(buttons, dx, dy, menu)
    }

    companion object {
        const val EDGE = 36f
        const val DPAD_RADIUS = 92f
        const val CLUSTER_RADIUS = 100f
        const val BUTTON_RADIUS = 34f
        const val SPACING = 74f
        const val STAGGER = 22f
        const val PILL_W = 96f
        const val PILL_H = 40f
        const val SHOULDER_W = 120f
        const val SHOULDER_H = 48f
        const val MENU_RADIUS = 24f
        const val HIT_MARGIN = 10f

        /** 8 direções; zona morta no centro. */
        fun direction(dx: Float, dy: Float): Pair<Int, Int> {
            val len = hypot(dx, dy)
            if (len < DPAD_RADIUS * 0.22f) return 0 to 0
            val sin22 = 0.3827f
            val x = if (abs(dx) / len > sin22) (if (dx > 0) 1 else -1) else 0
            val y = if (abs(dy) / len > sin22) (if (dy > 0) 1 else -1) else 0
            return x to y
        }
    }
}
