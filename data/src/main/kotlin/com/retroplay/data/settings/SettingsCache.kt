package com.retroplay.data.settings

import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Última foto das configurações, para quem precisa ler de forma síncrona (ex.: servidor). */
class SettingsCache(repo: SettingsRepository, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    val latest: StateFlow<SettingsSnapshot> = repo.snapshots.stateIn(scope, SharingStarted.Eagerly, SettingsSnapshot())
}
