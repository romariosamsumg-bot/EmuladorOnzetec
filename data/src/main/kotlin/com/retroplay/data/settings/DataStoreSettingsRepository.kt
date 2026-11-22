package com.retroplay.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Configurações no DataStore (Preferences) em modo multiprocesso: a UI e o processo do jogo
 * (":game") leem e gravam o mesmo arquivo com segurança.
 * Chaves: "g|chave" (global), "s|<console>|chave", "j|<jogo>|chave".
 */
class DataStoreSettingsRepository(
    file: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : SettingsRepository {

    private val store: DataStore<Preferences> = MultiProcessDataStoreFactory.create(
        serializer = PreferencesFileSerializer,
        scope = scope,
        produceFile = { file.also { it.parentFile?.mkdirs() } },
    )

    override val snapshots: Flow<SettingsSnapshot> = store.data.map { prefs ->
        SettingsSnapshot(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
    }

    override suspend fun current(): SettingsSnapshot = snapshots.first()

    override suspend fun <T : Any> set(def: SettingDef<T>, scope: ScopeRef, value: T?) {
        require(def.scoped || scope == ScopeRef.Global) { "${def.key} só existe no nível global" }
        setRaw(def.key, scope, value?.let(def::encode))
    }

    override suspend fun setRaw(key: String, scope: ScopeRef, value: String?) {
        val prefKey = stringPreferencesKey(scope.prefix + key)
        store.edit { prefs -> if (value == null) prefs.remove(prefKey) else prefs[prefKey] = value }
    }

    /** Uma única gravação (atômica) para o backup inteiro. */
    override suspend fun importRaw(raw: Map<String, String>, replace: Boolean) {
        val valid = raw.filterKeys { com.retroplay.domain.settings.SettingsKeys.parse(it) != null }
        store.edit { prefs ->
            if (replace) prefs.asMap().keys.filter { it.name !in valid }.forEach { prefs.remove(it) }
            valid.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
        }
    }

    override suspend fun clear(scope: ScopeRef, keyPrefix: String) {
        store.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(scope.prefix + keyPrefix) }.forEach { prefs.remove(it) }
        }
    }
}
