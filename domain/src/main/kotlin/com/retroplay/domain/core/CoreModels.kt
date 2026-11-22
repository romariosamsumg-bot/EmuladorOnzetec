package com.retroplay.domain.core

import com.retroplay.domain.system.CoreDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Política de atualização dos núcleos (Configurações → Núcleos, Fase 7/8).
 * NIGHTLY: oferece sempre a versão mais nova do buildbot.
 * STABLE: conservador — só atualiza quando o usuário pede e guarda a versão anterior.
 */
enum class CoreChannel { NIGHTLY, STABLE }

/** Núcleo baixado e pronto para uso. */
data class InstalledCore(
    val coreId: String,
    val abi: String,
    /** Caminho absoluto do .so. */
    val libraryPath: String,
    /** Versão conforme o servidor (ETag ou Last-Modified). */
    val version: String,
    val sha256: String,
    val sizeBytes: Long,
    val installedAtMillis: Long,
)

/** Motivos de falha, sempre traduzidos para mensagens simples na UI. */
enum class CoreError {
    /** Sem internet ou servidor inacessível. */
    NO_NETWORK,

    /** Servidor respondeu com erro (ex.: núcleo não existe para este ABI). */
    SERVER,

    /** Sem espaço livre suficiente. */
    NO_SPACE,

    /** Download corrompido ou sem o arquivo esperado. */
    CORRUPTED,

    /** Aparelho com ABI não suportado. */
    UNSUPPORTED_ABI,
    UNKNOWN,
}

sealed interface CoreInstallState {
    data object NotInstalled : CoreInstallState

    /** Na fila (ex.: aguardando rede). */
    data object Queued : CoreInstallState

    /** @param totalBytes -1 quando o servidor não informa o tamanho. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : CoreInstallState {
        val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    data class Installed(val core: InstalledCore) : CoreInstallState
    data class Failed(val error: CoreError) : CoreInstallState
}

/** Gerencia os núcleos libretro baixados sob demanda. */
interface CoreRepository {
    /** Estado do núcleo (instalação em andamento, instalado, falha...). */
    fun observe(core: CoreDescriptor): Flow<CoreInstallState>

    /** Agenda o download/instalação (idempotente se já estiver em andamento). */
    fun install(core: CoreDescriptor)

    suspend fun installed(core: CoreDescriptor): InstalledCore?

    suspend fun remove(core: CoreDescriptor)
}
