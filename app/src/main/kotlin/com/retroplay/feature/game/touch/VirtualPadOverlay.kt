package com.retroplay.feature.game.touch

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Controle na tela, por cima do jogo. Um único detector de toques acompanha todos os dedos;
 * a cada mudança, [onState] recebe o estado completo (botões, direcional, menu).
 */
@Composable
fun VirtualPadOverlay(spec: VirtualPadSpec, opacity: Float, accent: Color, onState: (PadState) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val model = remember(spec, maxWidth, maxHeight) { VirtualPadModel(spec, maxWidth.value, maxHeight.value) }
        var current by remember { mutableStateOf(PadState()) }
        val latest by rememberUpdatedState(onState)
        val measurer = rememberTextMeasurer()
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
        ) {
            val k = density
            val idle = Color.White.copy(alpha = 0.22f * opacity)
            val line = Color.White.copy(alpha = 0.75f * opacity)
            val pressed = accent.copy(alpha = 0.85f * opacity)
            model.elements.forEach { e ->
                val center = Offset(e.x * k, e.y * k)
                when {
                    e.id == "dpad" -> drawDpad(center, e.width / 2 * k, current, idle, line, pressed)
                    e.shape == PadShape.CIRCLE -> {
                        val on = (e.button != null && e.button in current.buttons) || (e.id == "menu" && current.menu)
                        drawCircle(if (on) pressed else idle, e.width / 2 * k, center)
                        drawCircle(if (e.highlighted) accent.copy(alpha = opacity) else line, e.width / 2 * k, center, style = Stroke(2f * k))
                    }
                    else -> {
                        val on = e.button != null && e.button in current.buttons
                        val tl = Offset(center.x - e.width / 2 * k, center.y - e.height / 2 * k)
                        val size = Size(e.width * k, e.height * k)
                        val r = CornerRadius(e.height / 2 * k)
                        drawRoundRect(if (on) pressed else idle, tl, size, r)
                        drawRoundRect(if (e.highlighted) accent.copy(alpha = opacity) else line, tl, size, r, style = Stroke(2f * k))
                    }
                }
                if (e.label.isNotEmpty()) {
                    val text = measurer.measure(e.label, TextStyle(color = Color.White.copy(alpha = opacity), fontSize = (if (e.shape == PadShape.PILL) 14 else 20).sp, fontWeight = FontWeight.Bold))
                    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
                }
            }
        }
    }
}

/** Direcional em cruz; o braço apertado fica com a cor de destaque. */
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
