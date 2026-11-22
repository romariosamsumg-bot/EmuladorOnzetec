package com.retroplay.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retroplay.core.common.DeviceKind

/** Altura de referência das telas (a TV em 960×540 dp). */
val TV_CANVAS_HEIGHT: Dp = 540.dp

/** Escala para a interface de TV caber numa tela com [availableHeight] (só reduz, nunca aumenta). */
fun tvCanvasScale(availableHeight: Dp): Float =
    if (availableHeight >= TV_CANVAS_HEIGHT || availableHeight <= 0.dp) 1f else availableHeight / TV_CANVAS_HEIGHT

/**
 * No celular (tela deitada, mais baixa que a TV), desenha a interface com a mesma proporção da TV,
 * reduzindo a densidade. Também respeita o recorte da câmera. Na TV não muda nada.
 */
@Composable
fun TvCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val scale = tvCanvasScale(maxHeight)
        if (scale == 1f) {
            content()
        } else {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) { content() }
        }
    }
}

/** Celular: sempre deitado e em tela cheia (barras do sistema aparecem ao deslizar a borda). */
fun Activity.applyPhoneWindow() {
    if (DeviceKind.isTv(this)) return
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
