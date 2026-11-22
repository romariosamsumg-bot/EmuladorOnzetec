package com.retroplay.data.saves

import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.profile.Profile
import java.io.File

/**
 * Save states no disco: `<raiz>/<console>/<jogo>/slot_N.state` + `slot_N.png` (miniatura).
 * A chave do jogo é o nome do arquivo (sem extensão): os saves sobrevivem a uma nova varredura.
 * Usado pelo processo do jogo (grava) e pela UI (lista, "Continuar jogando").
 */
class SaveStateStore(private val baseRoot: File, private val profile: () -> Long = { Profile.DEFAULT_ID }) {

    /** Cada perfil tem seus saves; o perfil 1 usa a raiz (saves de versões anteriores). */
    private val root: File get() = Profile.dirName(profile())?.let { File(baseRoot, it) } ?: baseRoot

    /** Os saves de um perfil específico (o processo do jogo usa o perfil lido ao abrir). */
    fun forProfile(id: Long) = SaveStateStore(baseRoot) { id }

    fun dir(system: SystemId, fileName: String): File =
        File(root, "${system.value}/${key(fileName)}")

    fun list(system: SystemId, fileName: String): List<SaveSlot> {
        val dir = dir(system, fileName)
        return SaveSlot.ALL.mapNotNull { slot ->
            val state = File(dir, "slot_$slot.state").takeIf { it.isFile } ?: return@mapNotNull null
            val thumb = File(dir, "slot_$slot.png").takeIf { it.isFile }
            SaveSlot(slot, state.lastModified(), thumb?.absolutePath, state.length())
        }
    }

    /** O save mais recente (automático ou manual). */
    fun latest(system: SystemId, fileName: String): SaveSlot? = list(system, fileName).maxByOrNull { it.savedAt }

    fun read(system: SystemId, fileName: String, slot: Int): ByteArray? =
        File(dir(system, fileName), "slot_$slot.state").takeIf { it.isFile }?.readBytes()

    fun write(system: SystemId, fileName: String, slot: Int, state: ByteArray, thumbnailPng: ByteArray? = null) {
        require(slot in SaveSlot.ALL) { "slot inválido: $slot" }
        val dir = dir(system, fileName).apply { mkdirs() }
        atomicWrite(File(dir, "slot_$slot.state"), state)
        val thumb = File(dir, "slot_$slot.png")
        if (thumbnailPng != null) atomicWrite(thumb, thumbnailPng) else thumb.delete()
    }

    /** Grava a miniatura depois (a captura de tela é assíncrona). */
    fun writeThumbnail(system: SystemId, fileName: String, slot: Int, png: ByteArray) {
        val dir = dir(system, fileName)
        if (File(dir, "slot_$slot.state").isFile) atomicWrite(File(dir, "slot_$slot.png"), png)
    }

    fun delete(system: SystemId, fileName: String, slot: Int) {
        val dir = dir(system, fileName)
        File(dir, "slot_$slot.state").delete()
        File(dir, "slot_$slot.png").delete()
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, ".${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        /** Nome do arquivo → pasta segura ("Super Mario World (USA).sfc" → "Super Mario World (USA)"). */
        fun key(fileName: String): String =
            fileName.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "jogo" }
    }
}
