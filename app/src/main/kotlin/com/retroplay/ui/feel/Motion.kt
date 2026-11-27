package com.retroplay.ui.feel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.retroplay.core.ui.theme.AnimationLevel
import com.retroplay.core.ui.theme.LocalRetroUiPrefs

/**
 * Física de mola (spring) da interface: o card focado "salta" um pouco além do tamanho final e
 * assenta (amortecimento 0,5), em vez de crescer em linha reta. Animações desligadas = sem mola.
 */
val FocusSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 420f)

/** Pressionar (toque): afunda rápido e volta com uma mola mais firme. */
val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** Escala com mola quando [focused]. Lido só no desenho: não recompõe o card a cada quadro. */
@Composable
fun Modifier.springFocusScale(focused: Boolean, target: Float = 1.1f): Modifier {
    val level = LocalRetroUiPrefs.current.animations
    val goal = if (focused && level != AnimationLevel.DESLIGADAS) target else 1f
    val scale by animateFloatAsState(goal, if (level == AnimationLevel.COMPLETAS) FocusSpring else spring(stiffness = Spring.StiffnessHigh), label = "focusScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Toque com "afundar e voltar" (celular). */
@Composable
fun Modifier.pressBounce(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, PressSpring, label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Tremor horizontal (erro): chame [shake] e use [Modifier.shake] no elemento. */
@Stable
class Shaker {
    internal val offset = Animatable(0f)

    suspend fun shake() {
        offset.snapTo(0f)
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 380
                -14f at 40
                12f at 100
                -9f at 160
                6f at 220
                -3f at 290
            },
        )
    }
}

@Composable
fun rememberShaker() = remember { Shaker() }

@Composable
fun Modifier.shake(shaker: Shaker): Modifier {
    val density = LocalDensity.current.density
    return graphicsLayer { translationX = shaker.offset.value * density }
}
