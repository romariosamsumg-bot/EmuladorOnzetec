package com.retroplay.feature.profiles

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ilustrações dos avatares, desenhadas em código (formas simples, sem personagens de jogos).
 * Coordenadas normalizadas: (0,0) é o canto superior esquerdo do quadrado do avatar, (1,1) o
 * inferior direito. [ink] é a cor do desenho; [paper] a do fundo (para os "furos").
 */
object AvatarArt {

    fun draw(scope: DrawScope, key: String, ink: Color, paper: Color) = with(scope) {
        val s = size.minDimension
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun sz(w: Float, h: Float) = Size(w * s, h * s)
        fun path(vararg pts: Pair<Float, Float>) = Path().apply {
            moveTo(pts[0].first * s, pts[0].second * s)
            pts.drop(1).forEach { lineTo(it.first * s, it.second * s) }
            close()
        }
        when (key) {
            "controle" -> {
                drawRoundRect(ink, o(0.16f, 0.34f), sz(0.68f, 0.34f), CornerRadius(0.17f * s))
                drawRect(paper, o(0.27f, 0.47f), sz(0.14f, 0.05f))
                drawRect(paper, o(0.315f, 0.425f), sz(0.05f, 0.14f))
                drawCircle(paper, 0.035f * s, o(0.64f, 0.45f))
                drawCircle(paper, 0.035f * s, o(0.72f, 0.53f))
            }
            "fliperama" -> {
                drawRoundRect(ink, o(0.22f, 0.62f), sz(0.56f, 0.16f), CornerRadius(0.04f * s))
                drawLine(ink, o(0.42f, 0.62f), o(0.42f, 0.36f), strokeWidth = 0.06f * s, cap = StrokeCap.Round)
                drawCircle(ink, 0.1f * s, o(0.42f, 0.3f))
                drawCircle(ink, 0.05f * s, o(0.62f, 0.56f))
            }
            "estrela" -> drawPath(star(s, 5, 0.36f, 0.15f), ink)
            "coracao" -> drawPath(
                Path().apply {
                    moveTo(0.5f * s, 0.78f * s)
                    cubicTo(0.12f * s, 0.52f * s, 0.18f * s, 0.2f * s, 0.5f * s, 0.36f * s)
                    cubicTo(0.82f * s, 0.2f * s, 0.88f * s, 0.52f * s, 0.5f * s, 0.78f * s)
                    close()
                },
                ink,
            )
            "raio" -> drawPath(path(0.56f to 0.16f, 0.3f to 0.54f, 0.48f to 0.54f, 0.42f to 0.84f, 0.7f to 0.44f, 0.52f to 0.44f), ink)
            "coroa" -> {
                drawPath(path(0.2f to 0.68f, 0.2f to 0.34f, 0.36f to 0.5f, 0.5f to 0.28f, 0.64f to 0.5f, 0.8f to 0.34f, 0.8f to 0.68f), ink)
                drawRect(ink, o(0.2f, 0.71f), sz(0.6f, 0.07f))
            }
            "fantasma" -> {
                drawPath(
                    Path().apply {
                        moveTo(0.24f * s, 0.8f * s)
                        lineTo(0.24f * s, 0.46f * s)
                        cubicTo(0.24f * s, 0.14f * s, 0.76f * s, 0.14f * s, 0.76f * s, 0.46f * s)
                        lineTo(0.76f * s, 0.8f * s)
                        lineTo(0.67f * s, 0.72f * s)
                        lineTo(0.585f * s, 0.8f * s)
                        lineTo(0.5f * s, 0.72f * s)
                        lineTo(0.415f * s, 0.8f * s)
                        lineTo(0.33f * s, 0.72f * s)
                        close()
                    },
                    ink,
                )
                drawCircle(paper, 0.055f * s, o(0.41f, 0.44f))
                drawCircle(paper, 0.055f * s, o(0.59f, 0.44f))
            }
            "gato" -> {
                drawPath(path(0.24f to 0.42f, 0.28f to 0.16f, 0.44f to 0.32f), ink)
                drawPath(path(0.76f to 0.42f, 0.72f to 0.16f, 0.56f to 0.32f), ink)
                drawCircle(ink, 0.27f * s, o(0.5f, 0.52f))
                drawCircle(paper, 0.045f * s, o(0.4f, 0.48f))
                drawCircle(paper, 0.045f * s, o(0.6f, 0.48f))
                drawPath(path(0.46f to 0.58f, 0.54f to 0.58f, 0.5f to 0.63f), paper)
            }
            "alien" -> {
                drawOval(ink, o(0.24f, 0.18f), sz(0.52f, 0.62f))
                drawOval(paper, o(0.31f, 0.4f), sz(0.15f, 0.1f))
                drawOval(paper, o(0.54f, 0.4f), sz(0.15f, 0.1f))
                drawLine(ink, o(0.36f, 0.22f), o(0.28f, 0.1f), strokeWidth = 0.035f * s, cap = StrokeCap.Round)
                drawLine(ink, o(0.64f, 0.22f), o(0.72f, 0.1f), strokeWidth = 0.035f * s, cap = StrokeCap.Round)
            }
            "foguete" -> {
                drawPath(path(0.5f to 0.14f, 0.62f to 0.32f, 0.62f to 0.66f, 0.38f to 0.66f, 0.38f to 0.32f), ink)
                drawPath(path(0.38f to 0.5f, 0.26f to 0.7f, 0.38f to 0.66f), ink)
                drawPath(path(0.62f to 0.5f, 0.74f to 0.7f, 0.62f to 0.66f), ink)
                drawCircle(paper, 0.055f * s, o(0.5f, 0.4f))
                drawPath(path(0.43f to 0.7f, 0.57f to 0.7f, 0.5f to 0.86f), ink)
            }
            "bola" -> {
                drawCircle(ink, 0.32f * s, o(0.5f, 0.5f))
                drawPath(star(s, 5, 0.1f, 0.1f, polygon = true), paper)
                for (i in 0 until 5) {
                    val a = -PI / 2 + i * 2 * PI / 5
                    drawLine(paper, o(0.5f + 0.1f * cos(a).toFloat(), 0.5f + 0.1f * sin(a).toFloat()), o(0.5f + 0.3f * cos(a).toFloat(), 0.5f + 0.3f * sin(a).toFloat()), strokeWidth = 0.025f * s)
                }
            }
            "dado" -> {
                drawRoundRect(ink, o(0.22f, 0.22f), sz(0.56f, 0.56f), CornerRadius(0.1f * s))
                listOf(0.35f to 0.35f, 0.65f to 0.35f, 0.5f to 0.5f, 0.35f to 0.65f, 0.65f to 0.65f).forEach { (x, y) -> drawCircle(paper, 0.05f * s, o(x, y)) }
            }
            "planeta" -> {
                drawCircle(ink, 0.22f * s, o(0.5f, 0.5f))
                drawOval(ink, o(0.14f, 0.43f), sz(0.72f, 0.14f), style = Stroke(width = 0.045f * s))
                drawOval(paper, o(0.3f, 0.47f), sz(0.4f, 0.06f))
            }
            "nota" -> {
                drawOval(ink, o(0.24f, 0.62f), sz(0.2f, 0.15f))
                drawOval(ink, o(0.54f, 0.56f), sz(0.2f, 0.15f))
                drawRect(ink, o(0.39f, 0.24f), sz(0.05f, 0.46f))
                drawRect(ink, o(0.69f, 0.18f), sz(0.05f, 0.46f))
                drawPath(path(0.39f to 0.24f, 0.74f to 0.18f, 0.74f to 0.27f, 0.39f to 0.33f), ink)
            }
            else -> drawCircle(ink, 0.2f * s, o(0.5f, 0.5f))
        }
    }

    private fun star(s: Float, points: Int, outer: Float, inner: Float, polygon: Boolean = false) = Path().apply {
        val n = if (polygon) points else points * 2
        for (i in 0 until n) {
            val r = if (polygon || i % 2 == 0) outer else inner
            val a = -PI / 2 + i * 2 * PI / n
            val x = (0.5f + r * cos(a).toFloat()) * s
            val y = (0.52f + r * sin(a).toFloat()) * s
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
