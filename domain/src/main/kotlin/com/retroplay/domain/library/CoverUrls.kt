package com.retroplay.domain.library

import com.retroplay.domain.system.GameSystem

/**
 * Capas do projeto libretro-thumbnails (espelho no GitHub; thumbnails.libretro.com está fora do ar).
 * O nome é o nome canônico do DAT com os caracteres proibidos (e comercial, asterisco, barras,
 * dois-pontos, crase, menor/maior, interrogação, barra vertical e aspas) trocados por "_".
 */
object CoverUrls {
    const val BASE = "https://raw.githubusercontent.com/libretro-thumbnails"

    fun repo(system: GameSystem) = system.libretroDatabaseName.replace(' ', '_')

    fun sanitize(name: String) = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

    fun url(system: GameSystem, name: String, type: CoverType): String =
        "$BASE/${repo(system)}/master/${type.folder}/${encode(sanitize(name))}.png"

    fun url(system: GameSystem, game: Game, type: CoverType? = game.cover): String? {
        val name = game.coverName ?: return null
        return type?.let { url(system, name, it) }
    }

    /** Codificação de segmento de caminho (RFC 3986): espaço vira %20, não "+". */
    private fun encode(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c.toChar() in "-._~") {
                append(c.toChar())
            } else {
                append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
    }
}
