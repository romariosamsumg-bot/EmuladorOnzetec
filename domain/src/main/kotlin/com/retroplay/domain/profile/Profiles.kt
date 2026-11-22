package com.retroplay.domain.profile

import kotlinx.coroutines.flow.Flow

/**
 * Perfil de jogador. Cada perfil tem seus favoritos, histórico ("Continuar jogando", mais
 * jogados, tempo de jogo), save states e saves de bateria. Jogos e configurações são comuns.
 */
data class Profile(val id: Long, val name: String, val color: Int) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    companion object {
        const val DEFAULT_ID = 1L

        /** Cores dos avatares, usadas em sequência para novos perfis. */
        val COLORS = listOf(0xFF8B6CFF.toInt(), 0xFF3FD0E0.toInt(), 0xFFFFB547.toInt(), 0xFF5BD68A.toInt(), 0xFFFF7A6B.toInt(), 0xFFE06BD0.toInt())

        fun colorFor(index: Int) = COLORS[Math.floorMod(index, COLORS.size)]

        /** Pasta de saves de um perfil: o perfil 1 usa a raiz (compatível com versões anteriores). */
        fun dirName(id: Long): String? = if (id == DEFAULT_ID) null else "p$id"
    }
}

interface ProfileRepository {
    val profiles: Flow<List<Profile>>
    val active: Flow<Profile>
    suspend fun activeId(): Long
    suspend fun create(name: String): Profile
    suspend fun rename(id: Long, name: String)

    /** Apaga o perfil, seu histórico e seus saves. Não apaga o último perfil. */
    suspend fun delete(id: Long): Boolean
    suspend fun switchTo(id: Long)
}
