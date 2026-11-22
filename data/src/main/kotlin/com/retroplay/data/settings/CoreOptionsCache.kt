package com.retroplay.data.settings

import com.retroplay.domain.settings.CoreOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * As opções de um núcleo só são conhecidas com ele carregado (no processo do jogo).
 * O jogo grava aqui a lista; a tela de Configurações lê para permitir a edição.
 */
class CoreOptionsCache(private val dir: File) {

    @Serializable
    private data class Entry(val key: String, val description: String, val values: List<String>)

    private val json = Json { ignoreUnknownKeys = true }

    fun save(coreId: String, options: List<CoreOption>) {
        if (options.isEmpty()) return
        dir.mkdirs()
        val body = json.encodeToString(ListSerializer(Entry.serializer()), options.map { Entry(it.key, it.description, it.values) })
        val tmp = File(dir, "$coreId.json.tmp")
        tmp.writeText(body)
        tmp.renameTo(File(dir, "$coreId.json"))
    }

    fun load(coreId: String): List<CoreOption> {
        val f = File(dir, "$coreId.json").takeIf { it.isFile } ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Entry.serializer()), f.readText()) }.getOrDefault(emptyList())
            .map { CoreOption(it.key, it.description, it.values, it.values.firstOrNull().orEmpty()) }
    }
}
