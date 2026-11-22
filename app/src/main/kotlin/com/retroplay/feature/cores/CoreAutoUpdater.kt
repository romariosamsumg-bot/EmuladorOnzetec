package com.retroplay.feature.cores

import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.system.SystemCatalog
import javax.inject.Inject

/**
 * Canal "nightly": uma vez por dia, ao abrir o app, atualiza os núcleos instalados que têm
 * versão nova no servidor. No canal "estável" nada é atualizado sozinho.
 */
class CoreAutoUpdater @Inject constructor(
    private val cores: CoreRepository,
    private val catalog: SystemCatalog,
    private val settings: SettingsRepository,
) {
    /** @return núcleos cuja atualização foi agendada. */
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): List<String> {
        val snap = settings.current()
        if (snap.value(AppSettings.CORE_CHANNEL) != "nightly") return emptyList()
        val last = snap.value(AppSettings.CORE_LAST_CHECK).toLongOrNull() ?: 0L
        if (now - last < DAY_MS) return emptyList()
        settings.set(AppSettings.CORE_LAST_CHECK, ScopeRef.Global, now.toString())
        val updated = mutableListOf<String>()
        catalog.all.flatMap { it.cores }.distinctBy { it.id }.forEach { core ->
            if (cores.installed(core) != null && cores.checkForUpdate(core) == true) {
                cores.install(core)
                updated += core.id
            }
        }
        return updated
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
