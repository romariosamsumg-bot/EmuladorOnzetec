package com.retroplay.data.transfer

import com.retroplay.domain.transfer.PhoneTransferState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import com.retroplay.domain.transfer.IncomingFile
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneTransferServiceTest {

    private val scope = TestScope(StandardTestDispatcher())

    /** Servidor falso: não abre socket; falha nas portas marcadas como ocupadas. */
    private class FakeServer(val port: Int, private val busy: Boolean) : TransferServerHandle {
        var running = false
        override val incoming = MutableStateFlow<List<IncomingFile>>(emptyList())
        override fun startServer() { if (busy) throw IOException("ocupada"); running = true }
        override fun stop() { running = false }
    }

    @Test
    fun `liga na primeira tela e desliga quando a ultima sai`() {
        val servers = mutableListOf<FakeServer>()
        val service = PhoneTransferService(
            serverFactory = { port, _, _ -> FakeServer(port, busy = port == 8080).also { servers += it } },
            onLibraryChanged = {},
            addresses = { listOf("192.168.0.15") },
            scope = scope,
        )
        service.acquire()
        val running = service.state.value as PhoneTransferState.Running
        // 8080 ocupada → usa 8081
        assertEquals("http://192.168.0.15:8081", running.url)
        assertEquals("http://192.168.0.15:8081/?pin=${running.pin}", running.qrUrl)
        assertTrue(running.pin.matches(Regex("\\d{4}")))
        service.acquire() // segunda tela
        service.release()
        assertTrue(service.state.value is PhoneTransferState.Running)
        service.release()
        assertEquals(PhoneTransferState.Stopped, service.state.value)
        assertTrue(servers.none { it.running })
    }

    @Test
    fun `porta e pin fixos das configuracoes`() {
        val service = PhoneTransferService(
            { p, _, _ -> FakeServer(p, false) }, {}, addresses = { listOf("10.0.0.2") }, scope = scope,
            firstPort = { 9000 }, fixedPin = { "1234" },
        )
        service.acquire()
        val r = service.state.value as PhoneTransferState.Running
        assertEquals("http://10.0.0.2:9000", r.url)
        assertEquals("1234", r.pin)
    }

    @Test
    fun `sem rede avisa`() {
        val service = PhoneTransferService({ p, _, _ -> FakeServer(p, false) }, {}, addresses = { emptyList() }, scope = scope)
        service.acquire()
        assertEquals(PhoneTransferState.Failed(PhoneTransferState.Failed.Reason.NO_NETWORK), service.state.value)
    }

    @Test
    fun `varios arquivos geram uma unica varredura`() {
        var scans = 0
        var onFile: (() -> Unit)? = null
        val service = PhoneTransferService(
            serverFactory = { p, _, cb -> onFile = cb; FakeServer(p, false) },
            onLibraryChanged = { scans++ },
            addresses = { listOf("10.0.0.2") },
            scope = scope,
            scanDelayMs = 1000,
        )
        service.acquire()
        repeat(5) { onFile!!(); scope.advanceTimeBy(300) }
        scope.runCurrent()
        assertEquals(0, scans)
        scope.advanceTimeBy(1500)
        scope.runCurrent()
        assertEquals(1, scans)
    }
}
