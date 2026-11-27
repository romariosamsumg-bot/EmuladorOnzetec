package com.retroplay

import com.retroplay.domain.profile.Profile
import com.retroplay.feature.profiles.StartupActions
import com.retroplay.feature.profiles.StartupOptions
import kotlinx.coroutines.flow.Flow

/**
 * O que a MainActivity entrega à interface do app. Cada sabor (tv / mobile) implementa
 * `AppContent(args)` e `Activity.applyAppWindow()` no próprio conjunto de fontes.
 */
class AppContentArgs(
    val options: StartupOptions,
    /** null enquanto carrega. */
    val profiles: List<Profile>?,
    val activeId: Long,
    /** "Perguntar quem vai jogar" ligado. */
    val ask: Boolean,
    val actions: StartupActions,
    /** Pedidos para voltar à Início (ex.: "Sair" no menu do jogo). */
    val goHome: Flow<Unit>,
    val active: Profile?,
)
