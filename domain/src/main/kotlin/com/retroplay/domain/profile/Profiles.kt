package com.retroplay.domain.profile

import kotlinx.coroutines.flow.Flow

/**
 * Perfil de jogador. Cada perfil tem seus favoritos, histórico ("Continuar jogando", mais
 * jogados, tempo de jogo), save states e saves de bateria. Jogos e configurações são comuns.
 */
data class Profile(
    val id: Long,
    val name: String,
    val color: Int,
    /** Avatar escolhido ([Avatars]); null = perfil ainda não configurado (criado sozinho na 1ª abertura). */
    val avatar: String? = null,
    /** Cor do tema deste perfil (nome de AccentColor: LARANJA, VIOLETA...). */
    val accent: String = DEFAULT_ACCENT,
) {
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "?"

    /** Já passou pela criação de perfil (tem avatar). */
    val isSetUp: Boolean get() = avatar != null

    companion object {
        const val DEFAULT_ID = 1L
        const val DEFAULT_ACCENT = "LARANJA"
        const val MAX_NAME = 20
        const val MAX_PROFILES = 8

        /** Cores dos avatares, usadas em sequência para novos perfis. */
        val COLORS = listOf(0xFF8B6CFF.toInt(), 0xFF3FD0E0.toInt(), 0xFFFFB547.toInt(), 0xFF5BD68A.toInt(), 0xFFFF7A6B.toInt(), 0xFFE06BD0.toInt())

        fun colorFor(index: Int) = COLORS[Math.floorMod(index, COLORS.size)]

        /** Pasta de saves de um perfil: o perfil 1 usa a raiz (compatível com versões anteriores). */
        fun dirName(id: Long): String? = if (id == DEFAULT_ID) null else "p$id"
    }
}

/** Avatares desenhados pelo app (ilustrações próprias, sem personagens de jogos). */
object Avatars {
    val ALL = listOf(
        "controle", "fliperama", "estrela", "coracao", "raio", "coroa", "fantasma",
        "gato", "alien", "foguete", "bola", "dado", "planeta", "nota",
    )

    /** Avatar sugerido para o n-ésimo perfil novo. */
    fun suggestion(index: Int) = ALL[Math.floorMod(index, ALL.size)]
}

interface ProfileRepository {
    val profiles: Flow<List<Profile>>
    val active: Flow<Profile>
    suspend fun activeId(): Long
    suspend fun create(name: String, avatar: String? = null, accent: String? = null): Profile
    suspend fun rename(id: Long, name: String)

    /** Salva nome, avatar e cor do tema do perfil. */
    suspend fun update(id: Long, name: String, avatar: String, accent: String)

    /** Apaga o perfil, seu histórico e seus saves. Não apaga o último perfil. */
    suspend fun delete(id: Long): Boolean
    suspend fun switchTo(id: Long)
}
