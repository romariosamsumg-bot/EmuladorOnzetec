package com.retroplay.domain.system

/** Resultado da detecção de sistema apenas pela extensão do arquivo. */
sealed interface ExtensionMatch {
    /** Só um sistema aceita a extensão. */
    data class Unique(val system: GameSystem) : ExtensionMatch

    /** Vários sistemas aceitam (ex.: .zip, .bin) — é preciso olhar o conteúdo/DAT ou perguntar. */
    data class Ambiguous(val candidates: List<GameSystem>) : ExtensionMatch

    data object Unsupported : ExtensionMatch
}

class SystemDetector(private val catalog: SystemCatalog) {

    fun matchByFileName(fileName: String): ExtensionMatch {
        val ext = extensionOf(fileName) ?: return ExtensionMatch.Unsupported
        val candidates = catalog.all.filter { ext in it.extensions }
        return when (candidates.size) {
            0 -> ExtensionMatch.Unsupported
            1 -> ExtensionMatch.Unique(candidates.single())
            else -> ExtensionMatch.Ambiguous(candidates)
        }
    }

    companion object {
        fun extensionOf(fileName: String): String? {
            val name = fileName.substringAfterLast('/').substringAfterLast('\\')
            val dot = name.lastIndexOf('.')
            if (dot <= 0 || dot == name.length - 1) return null
            return name.substring(dot + 1).lowercase()
        }
    }
}
