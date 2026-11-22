package com.retroplay.data.transfer

import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransfer
import com.retroplay.domain.transfer.PhoneTransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Liga o servidor quando a primeira tela de importação aparece e desliga quando a última some.
 * Os jogos recebidos disparam UMA varredura alguns segundos depois do último arquivo.
 */
class PhoneTransferService(
    private val serverFactory: (port: Int, pin: String, onFileReceived: () -> Unit) -> TransferServerHandle,
    private val onLibraryChanged: () -> Unit,
    private val addresses: () -> List<String> = ::localIpv4Addresses,
    private val firstPort: () -> Int = { PhoneTransferServer.DEFAULT_PORT },
    /** PIN fixo escolhido nas Configurações (null = aleatório a cada vez). */
    private val fixedPin: () -> String? = { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val scanDelayMs: Long = 2_500,
) : PhoneTransfer {

    private val _state = MutableStateFlow<PhoneTransferState>(PhoneTransferState.Stopped)
    override val state: StateFlow<PhoneTransferState> = _state.asStateFlow()
    private val _incoming = MutableStateFlow<List<IncomingFile>>(emptyList())
    override val incoming: StateFlow<List<IncomingFile>> = _incoming.asStateFlow()

    private var users = 0
    private var server: TransferServerHandle? = null
    private var mirror: Job? = null
    private var pendingScan: Job? = null

    @Synchronized
    override fun acquire() {
        users++
        if (users == 1) start()
    }

    @Synchronized
    override fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) stop()
    }

    private fun start() {
        val ips = addresses()
        if (ips.isEmpty()) {
            _state.value = PhoneTransferState.Failed(PhoneTransferState.Failed.Reason.NO_NETWORK)
            return
        }
        val pin = fixedPin()?.takeIf { it.matches(Regex("\\d{4}")) } ?: newPin()
        val base = firstPort()
        for (port in base until base + PORT_ATTEMPTS) {
            val s = serverFactory(port, pin, ::scheduleScan)
            try {
                s.startServer()
            } catch (e: IOException) {
                continue // porta ocupada: tenta a próxima
            }
            server = s
            _incoming.value = emptyList()
            mirror = scope.launch { s.incoming.collect { _incoming.value = it } }
            val urls = ips.map { "http://$it:$port" }
            _state.value = PhoneTransferState.Running(urls.first(), urls.drop(1), pin, "${urls.first()}/?pin=$pin")
            return
        }
        _state.value = PhoneTransferState.Failed(PhoneTransferState.Failed.Reason.PORT_IN_USE)
    }

    private fun stop() {
        server?.stop()
        server = null
        mirror?.cancel()
        _state.value = PhoneTransferState.Stopped
    }

    private fun scheduleScan() {
        pendingScan?.cancel()
        pendingScan = scope.launch {
            delay(scanDelayMs)
            onLibraryChanged()
        }
    }

    companion object {
        private const val PORT_ATTEMPTS = 10
        private val random = SecureRandom()

        fun newPin(): String = "%04d".format(random.nextInt(10_000))

        /** IPv4 das interfaces ativas (Wi-Fi/Ethernet primeiro), sem loopback. */
        fun localIpv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { i -> if (i.name.startsWith("wlan") || i.name.startsWith("eth")) 0 else 1 }
                .flatMap { i -> i.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { !it.isLoopbackAddress && !it.isLinkLocalAddress } }
                .map { it.hostAddress!! }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
