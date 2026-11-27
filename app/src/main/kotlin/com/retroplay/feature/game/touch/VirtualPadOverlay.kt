package com.retroplay.feature.game.touch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Aparência do controle: estilo, cores do console, transparência e destaque. */
data class PadLook(val skin: PadSkin?, val opacity: Float, val accent: Color)

/**
 * Controle na tela, por cima do jogo. Um único detector de toques acompanha todos os dedos
 * (multitoque: direcional + vários botões ao mesmo tempo); a cada mudança, [onState] recebe o
 * estado completo (botões, direcional, menu).
 */
@Composable
fun VirtualPadOverlay(
    spec: VirtualPadSpec,
    opacity: Float,
    accent: Color,
    onState: (PadState) -> Unit,
    modifier: Modifier = Modifier,
    skin: PadSkin? = null,
    scale: Float = 1f,
    offsets: Map<String, Pair<Float, Float>> = emptyMap(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val model = remember(spec, maxWidth, maxHeight, scale, offsets) { VirtualPadModel(spec, maxWidth.value, maxHeight.value, scale, offsets) }
        var current by remember { mutableStateOf(PadState()) }
        val latest by rememberUpdatedState(onState)
        val measurer = rememberTextMeasurer()
        val look = PadLook(skin, opacity, accent)
        Canvas(
            Modifier.fillMaxSize().testTag("virtual_pad").pointerInput(model) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val points = event.changes.filter { it.pressed }.map { it.position.x.toDp().value to it.position.y.toDp().value }
                        event.changes.forEach { it.consume() }
                        val state = model.stateFor(points)
                        if (state != current) {
                            current = state
                            latest(state)
                        }
                    }
                }
            },
        ) { drawPad(model, current, look, measurer) }
    }
}

/**
 * Editor de posições: arraste cada botão (ou o direcional) com o dedo. [offsets] é o estado
 * atual; [onChange] recebe o novo mapa a cada movimento.
 */
@Composable
fun VirtualPadEditor(
    spec: VirtualPadSpec,
    look: PadLook,
    scale: Float,
    offsets: Map<String, Pair<Float, Float>>,
    onChange: (Map<String, Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val model = remember(spec, w, h, scale, offsets) { VirtualPadModel(spec, w, h, scale, offsets) }
        val latestOffsets by rememberUpdatedState(offsets)
        val latestChange by rememberUpdatedState(onChange)
        var dragging by remember { mutableStateOf<String?>(null) }
        val measurer = rememberTextMeasurer()
        Canvas(
            Modifier.fillMaxSize().testTag("pad_editor").pointerInput(spec, w, h, scale) {
                detectDragGestures(
                    onDragStart = { p ->
                        val m = VirtualPadModel(spec, w, h, scale, latestOffsets)
                        dragging = m.elementAt(p.x.toDp().value, p.y.toDp().value)?.id
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, amount ->
                    val id = dragging ?: return@detectDragGestures
                    change.consume()
                    val old = latestOffsets[id] ?: (0f to 0f)
                    latestChange(latestOffsets + (id to (old.first + amount.x.toDp().value to old.second + amount.y.toDp().value)))
                }
            },
        ) {
            drawPad(model, PadState(), look, measurer, highlightId = dragging)
        }
    }
}

/** Desenha o controle (cores do console no estilo clássico; translúcido no simples). */
fun DrawScope.drawPad(model: VirtualPadModel, current: PadState, look: PadLook, measurer: TextMeasurer, highlightId: String? = null) {
    val k = density
    val opacity = look.opacity
    val skin = look.skin
    val idle = Color.White.copy(alpha = 0.22f * opacity)
    val line = Color.White.copy(alpha = 0.75f * opacity)
    val pressedMinimal = look.accent.copy(alpha = 0.85f * opacity)
    fun pressedOf(c: Color) = lerp(c, Color.White, 0.45f).copy(alpha = (0.95f * opacity).coerceAtLeast(0.5f))
    fun fill(c: Color) = c.copy(alpha = (0.92f * opacity).coerceAtLeast(0.25f))

    model.elements.forEach { e ->
        val center = Offset(e.x * k, e.y * k)
        val selected = highlightId == e.id
        when {
            e.id == "dpad" -> when {
                skin == null -> drawDpad(center, e.width / 2 * k, current, idle, line, pressedMinimal)
                skin.stick -> drawStick(center, e.width / 2 * k, current, fill(skin.dpad), line, fill(skin.stickBall))
                else -> drawDpad(center, e.width / 2 * k, current, fill(skin.dpad), skin.dpadLine.copy(alpha = opacity), pressedOf(skin.dpad))
            }
            e.shape == PadShape.CIRCLE -> {
                val on = (e.button != null && e.button in current.buttons) || (e.id == "menu" && current.menu)
                val base = if (skin != null && e.id != "menu") skin.face[e.label] ?: skin.faceDefault else null
                val color = when {
                    base == null -> if (on) pressedMinimal else idle
                    on -> pressedOf(base)
                    else -> fill(base)
                }
                drawCircle(color, e.width / 2 * k, center)
                // Leve brilho em cima, como um botão de plástico.
                if (base != null) drawCircle(Color.White.copy(alpha = 0.12f * opacity), e.width / 2 * k * 0.72f, center.copy(y = center.y - e.width * k * 0.08f))
                drawCircle(if (e.highlighted) look.accent.copy(alpha = opacity) else line, e.width / 2 * k, center, style = Stroke(2f * k))
            }
            else -> {
                val on = e.button != null && e.button in current.buttons
                val isShoulder = e.y < model.height / 2
                val base = skin?.let { if (isShoulder) it.shoulder else it.pill }
                val color = when {
                    base == null -> if (on) pressedMinimal else idle
                    on -> pressedOf(base)
                    else -> fill(base)
                }
                val tl = Offset(center.x - e.width / 2 * k, center.y - e.height / 2 * k)
                val size = Size(e.width * k, e.height * k)
                val r = CornerRadius(e.height / 2 * k)
                drawRoundRect(color, tl, size, r)
                drawRoundRect(if (e.highlighted) look.accent.copy(alpha = opacity) else line, tl, size, r, style = Stroke(2f * k))
            }
        }
        if (selected) drawCircle(look.accent, (maxOf(e.width, e.height) / 2 + 6f) * k, center, style = Stroke(3f * k))
        if (e.label.isNotEmpty()) {
            val isShoulder = e.shape == PadShape.PILL && e.y < model.height / 2
            val textColor = when {
                skin == null -> Color.White
                e.shape == PadShape.PILL -> if (isShoulder) skin.shoulderText else skin.pillText
                else -> skin.faceText[e.label] ?: skin.faceTextDefault
            }.copy(alpha = opacity.coerceAtLeast(0.6f))
            val fontSize = (if (e.shape == PadShape.PILL) 14 else 20) * (e.height / (if (e.shape == PadShape.PILL) VirtualPadModel.PILL_H else VirtualPadModel.BUTTON_RADIUS * 2)).coerceIn(0.7f, 1.4f)
            // Os símbolos do PlayStation desenham menores que letras: compensa.
            val symbol = if (e.label in setOf("△", "○", "✕", "□")) 1.7f else 1f
            val text = measurer.measure(e.label, TextStyle(color = textColor, fontSize = (fontSize * symbol).sp, fontWeight = FontWeight.Bold))
            drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
        }
    }
}

/** Direcional em cruz; o braço apertado fica destacado. */
private fun DrawScope.drawDpad(c: Offset, r: Float, s: PadState, idle: Color, line: Color, pressed: Color) {
    val arm = r * 0.62f
    val corner = CornerRadius(arm * 0.25f)
    drawRoundRect(idle, Offset(c.x - r, c.y - arm / 2), Size(r * 2, arm), corner)
    drawRoundRect(idle, Offset(c.x - arm / 2, c.y - r), Size(arm, r * 2), corner)
    drawRoundRect(line, Offset(c.x - r, c.y - arm / 2), Size(r * 2, arm), corner, style = Stroke(2f * density))
    drawRoundRect(line, Offset(c.x - arm / 2, c.y - r), Size(arm, r * 2), corner, style = Stroke(2f * density))
    if (s.dx != 0) drawRoundRect(pressed, Offset(if (s.dx > 0) c.x + arm / 2 else c.x - r, c.y - arm / 2), Size(r - arm / 2, arm), corner)
    if (s.dy != 0) drawRoundRect(pressed, Offset(c.x - arm / 2, if (s.dy > 0) c.y + arm / 2 else c.y - r), Size(arm, r - arm / 2), corner)
}

/** Alavanca de fliperama: base redonda e bolinha que se inclina na direção apertada. */
private fun DrawScope.drawStick(c: Offset, r: Float, s: PadState, base: Color, line: Color, ball: Color) {
    drawCircle(base, r * 0.92f, c)
    drawCircle(line, r * 0.92f, c, style = Stroke(2f * density))
    val tip = Offset(c.x + s.dx * r * 0.42f, c.y + s.dy * r * 0.42f)
    drawLine(Color(0xFFB0B0B8), c, tip, strokeWidth = r * 0.16f, cap = StrokeCap.Round)
    drawCircle(ball, r * 0.36f, tip)
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.12f, Offset(tip.x - r * 0.1f, tip.y - r * 0.12f))
}
