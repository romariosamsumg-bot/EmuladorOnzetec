package com.retroplay.fakes

import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Configurações em memória para testes de lógica (sem DataStore). */
class InMemorySettings : SettingsRepository {
    private val state = MutableStateFlow(SettingsSnapshot())
    override val snapshots: StateFlow<SettingsSnapshot> = state
    override suspend fun current() = state.value
    override suspend fun <T : Any> set(def: SettingDef<T>, scope: ScopeRef, value: T?) = setRaw(def.key, scope, value?.let(def::encode))
    override suspend fun setRaw(key: String, scope: ScopeRef, value: String?) {
        val k = scope.prefix + key
        state.value = SettingsSnapshot(if (value == null) state.value.raw - k else state.value.raw + (k to value))
    }
    override suspend fun clear(scope: ScopeRef, keyPrefix: String) {
        state.value = SettingsSnapshot(state.value.raw.filterKeys { !it.startsWith(scope.prefix + keyPrefix) })
    }
}
