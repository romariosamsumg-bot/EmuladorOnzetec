package com.retroplay.data.testing

import java.util.zip.CRC32

/**
 * Gera arquivos falsos com um CRC32 escolhido (acrescenta 4 bytes calculados).
 * Permite testar a identificação contra o banco REAL de DATs sem usar nenhuma ROM de verdade.
 */
object CrcForge {
    private val table = LongArray(256) { n ->
        var c = n.toLong()
        repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
        c
    }
    private val reverse = IntArray(256).also { rev -> table.forEachIndexed { i, v -> rev[((v ushr 24) and 0xFF).toInt()] = i } }

    fun forge(prefix: ByteArray, targetCrc: Long): ByteArray {
        var w = targetCrc xor 0xFFFFFFFFL
        val idx = IntArray(4)
        for (k in 3 downTo 0) {
            val i = reverse[((w ushr 24) and 0xFF).toInt()]
            idx[k] = i
            w = ((w xor table[i]) shl 8) and 0xFFFFFFFFL
        }
        var crc = CRC32().apply { update(prefix) }.value xor 0xFFFFFFFFL
        val tail = ByteArray(4)
        for (k in 0..3) {
            tail[k] = ((crc xor idx[k].toLong()) and 0xFF).toByte()
            crc = (crc ushr 8) xor table[idx[k]]
        }
        return (prefix + tail).also { check(CRC32().apply { update(it) }.value == targetCrc) }
    }

    fun fake(seed: String, targetCrc: Long) = forge(("arquivo de teste $seed ").toByteArray(), targetCrc)
}
