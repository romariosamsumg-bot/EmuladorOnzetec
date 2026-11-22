package com.retroplay.domain.settings

import com.retroplay.domain.system.SystemId
import kotlinx.coroutines.flow.Flow

/** Nível de uma configuração: o jogo sobrescreve o console, que sobrescreve o global. */
enum class Scope { GLOBAL, SYSTEM, GAME }

/** Onde um valor está gravado. */
data class ScopeRef(val scope: Scope, val id: String? = null) {
    /** Prefixo da chave no armazenamento. */
    val prefix: String get() = when (scope) {
        Scope.GLOBAL -> "g|"
        Scope.SYSTEM -> "s|$id|"
        Scope.GAME -> "j|$id|"
    }

    companion object {
        val Global = ScopeRef(Scope.GLOBAL)
        fun system(id: SystemId) = ScopeRef(Scope.SYSTEM, id.value)
        fun game(key: String) = ScopeRef(Scope.GAME, key)
    }
}

/** Identificador estável de um jogo para configurações e saves: "snes/Super Mario World (USA)". */
object GameKey {
    fun of(system: SystemId, fileName: String): String =
        system.value + "/" + fileName.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_")
}

/** Definição de uma configuração (tipo, padrão e se aceita console/jogo). */
sealed class SettingDef<T : Any>(val key: String, val default: T, val scoped: Boolean) {
    abstract fun encode(value: T): String
    abstract fun decode(raw: String): T?

    class Bool(key: String, default: Boolean, scoped: Boolean = false) : SettingDef<Boolean>(key, default, scoped) {
        override fun encode(value: Boolean) = value.toString()
        override fun decode(raw: String) = raw.toBooleanStrictOrNull()
    }

    /** Escolha entre valores fixos (gravados como texto estável). */
    class Choice(key: String, default: String, val options: List<String>, scoped: Boolean = false) : SettingDef<String>(key, default, scoped) {
        init { require(default in options) { "padrão fora das opções: $key" } }
        override fun encode(value: String) = value
        override fun decode(raw: String) = raw.takeIf { it in options }
    }

    class Text(key: String, default: String = "", scoped: Boolean = false) : SettingDef<String>(key, default, scoped) {
        override fun encode(value: String) = value
        override fun decode(raw: String) = raw
    }
}

/** Valor resolvido e o nível de onde veio (para o indicador de "personalizado"). */
data class Resolved<T>(val value: T, val source: Scope?) {
    /** null = valor padrão (nunca foi alterado). */
    val isDefault: Boolean get() = source == null
}

/** Foto de todas as configurações gravadas, com a resolução hierárquica. */
data class SettingsSnapshot(val raw: Map<String, String> = emptyMap()) {

    fun <T : Any> resolve(def: SettingDef<T>, system: SystemId? = null, gameKey: String? = null): Resolved<T> {
        if (def.scoped) {
            gameKey?.let { get(def, ScopeRef.game(it)) }?.let { return Resolved(it, Scope.GAME) }
            system?.let { get(def, ScopeRef.system(it)) }?.let { return Resolved(it, Scope.SYSTEM) }
        }
        get(def, ScopeRef.Global)?.let { return Resolved(it, Scope.GLOBAL) }
        return Resolved(def.default, null)
    }

    fun <T : Any> value(def: SettingDef<T>, system: SystemId? = null, gameKey: String? = null): T = resolve(def, system, gameKey).value

    /** Valor gravado exatamente neste nível (sem herança). */
    fun <T : Any> get(def: SettingDef<T>, scope: ScopeRef): T? = raw[scope.prefix + def.key]?.let(def::decode)

    /** Chaves (sem prefixo) gravadas num nível, ex.: opções de núcleo personalizadas. */
    fun keysIn(scope: ScopeRef, keyPrefix: String = ""): Map<String, String> =
        raw.filterKeys { it.startsWith(scope.prefix + keyPrefix) }.mapKeys { it.key.removePrefix(scope.prefix) }
}

interface SettingsRepository {
    val snapshots: Flow<SettingsSnapshot>
    suspend fun current(): SettingsSnapshot

    /** Grava (ou, com null, remove a personalização deste nível). */
    suspend fun <T : Any> set(def: SettingDef<T>, scope: ScopeRef, value: T?)

    /** Chave livre (ex.: opções de núcleo "coreopt.snes9x2010.snes9x_2010_aspect"). */
    suspend fun setRaw(key: String, scope: ScopeRef, value: String?)

    /** Remove todas as chaves de um nível que começam com [keyPrefix]. */
    suspend fun clear(scope: ScopeRef, keyPrefix: String = "")
}
