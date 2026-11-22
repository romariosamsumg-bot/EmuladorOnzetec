package com.retroplay.domain.transfer

import com.retroplay.domain.system.SystemId
import kotlinx.coroutines.flow.StateFlow

sealed interface PhoneTransferState {
    data object Stopped : PhoneTransferState

    /**
     * @param url endereço principal (ex.: http://192.168.0.15:8080)
     * @param qrUrl endereço com o PIN embutido, para o QR code
     */
    data class Running(val url: String, val otherUrls: List<String>, val pin: String, val qrUrl: String) : PhoneTransferState

    data class Failed(val reason: Reason) : PhoneTransferState {
        enum class Reason { NO_NETWORK, PORT_IN_USE }
    }
}

enum class TransferError { TOO_BIG, NO_SPACE, NOT_A_GAME, INTERRUPTED, IO }

/** Um arquivo chegando do celular (mostrado ao vivo na TV). */
data class IncomingFile(
    val id: Long,
    val name: String,
    val received: Long,
    val total: Long,
    val status: Status,
    val system: SystemId? = null,
    val isBios: Boolean = false,
    val error: TransferError? = null,
) {
    enum class Status { RECEIVING, DONE, FAILED }
    val fraction: Float get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Transferência de jogos pelo celular: servidor HTTP local, ativo só enquanto alguma tela de
 * importação está visível (contagem de referências com [acquire]/[release]).
 */
interface PhoneTransfer {
    val state: StateFlow<PhoneTransferState>
    val incoming: StateFlow<List<IncomingFile>>
    fun acquire()
    fun release()
}
