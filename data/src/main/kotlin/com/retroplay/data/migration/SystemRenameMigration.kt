package com.retroplay.data.migration

import com.retroplay.domain.settings.SettingsRepository
import java.io.File

/**
 * Renomeia um sistema nas configurações e nas pastas de saves (ex.: "neogeo" → "arcade" no app
 * 1.2). Pode rodar sempre ao abrir o app: sem nada antigo, não faz nada.
 */
class SystemRenameMigration(
    private val settings: SettingsRepository,
    /** Pastas que têm uma subpasta por sistema (e por perfil "p<id>"): states/ e saves/. */
    private val roots: List<File>,
    private val from: String,
    private val to: String,
) {
    /** @return quantas configurações e pastas foram migradas. */
    suspend fun run(): Int = migrateSettings() + migrateFolders()

    private suspend fun migrateSettings(): Int {
        val raw = settings.current().raw
        val renamed = renameKeys(raw, from, to)
        if (renamed == raw) return 0
        settings.importRaw(renamed, replace = true)
        return raw.keys.count { it != renameKey(it, from, to) }
    }

    private fun migrateFolders(): Int {
        var n = 0
        roots.filter { it.isDirectory }.forEach { root ->
            val bases = listOf(root) + (root.listFiles { f -> f.isDirectory && f.name.matches(Regex("p\\d+")) }?.toList() ?: emptyList())
            bases.forEach { base ->
                val old = File(base, from)
                if (old.isDirectory) {
                    moveInto(old, File(base, to))
                    n++
                }
            }
        }
        return n
    }

    /** Move o conteúdo; se o arquivo já existe no destino, o mais novo fica. */
    private fun moveInto(src: File, dest: File) {
        if (!dest.exists() && src.renameTo(dest)) return
        dest.mkdirs()
        src.listFiles()?.forEach { f ->
            val target = File(dest, f.name)
            when {
                f.isDirectory -> moveInto(f, target)
                !target.exists() || f.lastModified() > target.lastModified() -> { target.delete(); f.renameTo(target) || run { f.copyTo(target, overwrite = true); f.delete() } }
                else -> f.delete()
            }
        }
        src.delete()
    }

    companion object {
        fun renameKey(key: String, from: String, to: String): String = when {
            key.startsWith("s|$from|") -> "s|$to|" + key.removePrefix("s|$from|")
            key.startsWith("j|$from/") -> "j|$to/" + key.removePrefix("j|$from/")
            else -> key
        }

        /** Em caso de conflito, a chave já no nome novo prevalece. */
        fun renameKeys(raw: Map<String, String>, from: String, to: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            raw.forEach { (k, v) -> val nk = renameKey(k, from, to); if (nk == k || nk !in raw) out[nk] = v }
            return out
        }
    }
}
