package com.retroplay.feature.game.input

/**
 * Atribui portas (jogador 1–4) aos controles na ordem em que são usados.
 * O controle remoto/teclado sempre é o jogador 1.
 */
class PortAssigner(private val maxPorts: Int = 4) {
    private val ports = LinkedHashMap<Int, Int>()

    fun portFor(deviceId: Int, isGamepad: Boolean): Int {
        if (!isGamepad) return 0
        ports[deviceId]?.let { return it }
        val free = (0 until maxPorts).firstOrNull { it !in ports.values } ?: return maxPorts - 1
        ports[deviceId] = free
        return free
    }

    /** Libera a porta quando o controle é desconectado. */
    fun release(deviceId: Int) {
        ports.remove(deviceId)
    }
}

/** Combina direcional digital (HAT) com o analógico esquerdo para jogos que só leem o D-pad. */
object StickToDpad {
    fun combine(hat: Float, stick: Float, threshold: Float = 0.5f): Float = when {
        hat != 0f -> hat
        stick >= threshold -> 1f
        stick <= -threshold -> -1f
        else -> 0f
    }
}
