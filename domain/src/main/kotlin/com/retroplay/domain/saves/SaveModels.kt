package com.retroplay.domain.saves

/** Espaço de save state: 0 = automático, 1–9 = manuais. */
data class SaveSlot(
    val slot: Int,
    val savedAt: Long,
    /** Caminho da miniatura PNG (null se não foi possível capturar). */
    val thumbnailPath: String?,
    val sizeBytes: Long,
) {
    val isAuto: Boolean get() = slot == AUTO

    companion object {
        const val AUTO = 0
        val MANUAL = 1..9
        val ALL = listOf(AUTO) + MANUAL
    }
}

/**
 * Buffer circular de estados para o rewind, limitado em bytes (o usuário escolhe quantos MB;
 * a TV tem só 2 GB de RAM). Quando cheio, descarta os estados mais antigos.
 */
class RewindBuffer(private val maxBytes: Long) {
    private val states = ArrayDeque<ByteArray>()
    var usedBytes = 0L
        private set

    val size: Int get() = states.size

    fun push(state: ByteArray) {
        if (state.size > maxBytes) return
        states.addLast(state)
        usedBytes += state.size
        while (usedBytes > maxBytes) usedBytes -= states.removeFirst().size
    }

    /** Estado mais recente (e o remove), ou null se vazio. */
    fun pop(): ByteArray? = states.removeLastOrNull()?.also { usedBytes -= it.size }

    /** Volta [steps] estados de uma vez (ex.: "voltar 5 segundos"). */
    fun rewind(steps: Int): ByteArray? {
        var last: ByteArray? = null
        repeat(steps) { last = pop() ?: return last }
        return last
    }

    fun clear() {
        states.clear()
        usedBytes = 0
    }
}

/** FPS a partir dos instantes dos quadros (janela de 1 segundo). */
class FpsCounter(private val windowNanos: Long = 1_000_000_000L) {
    private val frames = ArrayDeque<Long>()

    fun onFrame(nanos: Long): Int {
        frames.addLast(nanos)
        while (frames.isNotEmpty() && nanos - frames.first() > windowNanos) frames.removeFirst()
        return frames.size
    }
}
