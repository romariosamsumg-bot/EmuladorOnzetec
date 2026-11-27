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
    /** @param neoGeo arcade: jogo de Neo Geo usa só A, B, C, D (os demais, até 6 botões). */
    fun forSystem(system: GameSystem, sixButtons: Boolean = true, neoGeo: Boolean = false): VirtualPadSpec {
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
            SystemRegistry.Ids.ARCADE -> VirtualPadSpec(
                face = if (neoGeo) {
                    listOf(listOf(of(RetroPadButton.Y), of(RetroPadButton.X)), listOf(of(RetroPadButton.B), of(RetroPadButton.A)))
                } else {
                    // Botões 1-2-3 em cima e 4-5-6 embaixo (socos e chutes nos jogos de luta).
                    listOf(listOf(of(RetroPadButton.B), of(RetroPadButton.A), of(RetroPadButton.Y)), listOf(of(RetroPadButton.X), of(RetroPadButton.L), of(RetroPadButton.R)))
                },
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            SystemRegistry.Ids.PLAYSTATION -> VirtualPadSpec(
                // △ em cima, ○ à direita, ✕ embaixo, □ à esquerda.
                face = listOf(listOf(of(RetroPadButton.X), of(RetroPadButton.A), of(RetroPadButton.B), of(RetroPadButton.Y))),
                diamond = true,
                // L1/R1 em cima; L2/R2 logo abaixo.
                shoulders = listOf(of(RetroPadButton.L), of(RetroPadButton.R), of(RetroPadButton.L2), of(RetroPadButton.R2)),
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            // Nintendinho, Game Boy e Game Boy Color: B e A lado a lado (A à direita), Select e Start.
            SystemRegistry.Ids.NES, SystemRegistry.Ids.GAME_BOY, SystemRegistry.Ids.GAME_BOY_COLOR -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.B), of(RetroPadButton.A))),
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            SystemRegistry.Ids.GBA -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.B), of(RetroPadButton.A))),
                shoulders = listOf(of(RetroPadButton.L), of(RetroPadButton.R)),
                center = listOf(of(RetroPadButton.SELECT), of(RetroPadButton.START)),
            )
            // Atari 2600: alavanca, um botão de fogo e as chaves Select/Reset do console.
            SystemRegistry.Ids.ATARI_2600 -> VirtualPadSpec(
                face = listOf(listOf(of(RetroPadButton.B))),
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

/**
 * Geometria do controle. [scale] = tamanho dos botões (0,8 a 1,3); [offsets] = deslocamento (dp)
 * de cada elemento em relação à posição padrão, salvo pelo editor de posições (por console).
 */
class VirtualPadModel(
    spec: VirtualPadSpec,
    val width: Float,
    val height: Float,
    val scale: Float = 1f,
    offsets: Map<String, Pair<Float, Float>> = emptyMap(),
) {
    val elements: List<PadElement>
    val dpad: PadElement

    init {
        val s = scale.coerceIn(0.6f, 1.5f)
        val dpadR = DPAD_RADIUS * s
        val clusterR = CLUSTER_RADIUS * s
        val spacing = SPACING * s
        val stagger = STAGGER * s
        val buttonR = BUTTON_RADIUS * s
        val pillW = PILL_W * s
        val pillH = PILL_H * s
        val shW = SHOULDER_W * s
        val shH = SHOULDER_H * s
        fun face(b: ConsoleButton, x: Float, y: Float) =
            PadElement(b.label, PadShape.CIRCLE, x, y, buttonR * 2, buttonR * 2, b.label, b.retroPad, b.highlighted)

        val list = ArrayList<PadElement>()
        list += PadElement("dpad", PadShape.CIRCLE, EDGE + dpadR, height - EDGE - dpadR, dpadR * 2, dpadR * 2, "")
        val cx = width - EDGE - clusterR
        val cy = height - EDGE - clusterR
        if (spec.diamond && spec.face.firstOrNull()?.size == 4) {
            val (top, right, bottom, left) = spec.face.first()
            listOf(top to (0f to -spacing), right to (spacing to 0f), bottom to (0f to spacing), left to (-spacing to 0f)).forEach { (b, o) ->
                list += face(b, cx + o.first, cy + o.second)
            }
        } else {
            val rows = spec.face
            val y0 = cy + (rows.size - 1) * spacing / 2
            rows.reversed().forEachIndexed { k, row ->
                val y = y0 - k * spacing
                row.forEachIndexed { j, b ->
                    val x = cx + (j - (row.size - 1) / 2f) * spacing + k * stagger - (rows.size - 1) * stagger / 2
                    list += face(b, x, y)
                }
            }
        }
        spec.center.forEachIndexed { i, b ->
            val x = width / 2 + (i - (spec.center.size - 1) / 2f) * (pillW + 24f)
            list += PadElement(b.label, PadShape.PILL, x, height - EDGE / 2 - pillH / 2, pillW, pillH, b.label, b.retroPad, b.highlighted)
        }
        // Ombros: pares (esquerdo, direito) em fileiras de cima para baixo (L1 R1, depois L2 R2).
        spec.shoulders.forEachIndexed { i, b ->
            val x = if (i % 2 == 0) EDGE + shW / 2 else width - EDGE - shW / 2
            val y = EDGE + shH / 2 + (i / 2) * (shH + SHOULDER_GAP)
            list += PadElement(b.label, PadShape.PILL, x, y, shW, shH, b.label, b.retroPad)
        }
        list += PadElement("menu", PadShape.CIRCLE, width / 2, EDGE / 2 + MENU_RADIUS, MENU_RADIUS * 2, MENU_RADIUS * 2, "☰")
        // Posições personalizadas, sempre dentro da tela.
        elements = list.map { e ->
            val o = offsets[e.id] ?: return@map e
            e.copy(
                x = (e.x + o.first).coerceIn(e.width / 2, width - e.width / 2),
                y = (e.y + o.second).coerceIn(e.height / 2, height - e.height / 2),
            )
        }
        dpad = elements.first { it.id == "dpad" }
    }

    /** Junta todos os dedos na tela num estado; cada dedo aperta no máximo um botão (o mais próximo). */
    fun stateFor(points: List<Pair<Float, Float>>): PadState {
        val buttons = HashSet<RetroPadButton>()
        var dx = 0
        var dy = 0
        var menu = false
        val r = dpad.width / 2
        for ((px, py) in points) {
            if (dpad.hit(px, py, r * 0.35f)) {
                val (x, y) = direction(px - dpad.x, py - dpad.y, r)
                if (x != 0) dx = x
                if (y != 0) dy = y
                continue
            }
            val hit = elements.filter { it !== dpad && it.hit(px, py, HIT_MARGIN) }.minByOrNull { hypot(px - it.x, py - it.y) } ?: continue
            if (hit.id == "menu") menu = true else hit.button?.let(buttons::add)
        }
        return PadState(buttons, dx, dy, menu)
    }

    /** Elemento mais perto de um toque (editor de posições). */
    fun elementAt(px: Float, py: Float): PadElement? =
        elements.filter { it.hit(px, py, HIT_MARGIN * 2) }.minByOrNull { hypot(px - it.x, py - it.y) }

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
        const val SHOULDER_GAP = 14f
        const val MENU_RADIUS = 24f
        const val HIT_MARGIN = 10f

        /** 8 direções; zona morta no centro. */
        fun direction(dx: Float, dy: Float, radius: Float = DPAD_RADIUS): Pair<Int, Int> {
            val len = hypot(dx, dy)
            if (len < radius * 0.22f) return 0 to 0
            val sin22 = 0.3827f
            val x = if (abs(dx) / len > sin22) (if (dx > 0) 1 else -1) else 0
            val y = if (abs(dy) / len > sin22) (if (dy > 0) 1 else -1) else 0
            return x to y
        }
    }
}

/** Posições personalizadas: "A:12.0,-30.5;dpad:0.0,10.0" (deslocamento em dp por elemento). */
object PadLayoutCodec {
    fun decode(raw: String): Map<String, Pair<Float, Float>> = raw.split(';').mapNotNull { part ->
        val id = part.substringBeforeLast(':', "")
        val xy = part.substringAfterLast(':', "").split(',')
        if (id.isEmpty() || xy.size != 2) return@mapNotNull null
        val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
        val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
        id to (x to y)
    }.toMap()

    fun encode(offsets: Map<String, Pair<Float, Float>>): String =
        offsets.filterValues { it.first != 0f || it.second != 0f }.entries.sortedBy { it.key }
            .joinToString(";") { (id, o) -> "$id:${"%.1f".format(java.util.Locale.ROOT, o.first)},${"%.1f".format(java.util.Locale.ROOT, o.second)}" }
}
