package com.retroplay.data.transfer

import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemDetector
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.TransferError
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** O que o [PhoneTransferService] precisa de um servidor (permite testes sem sockets). */
interface TransferServerHandle {
    val incoming: StateFlow<List<IncomingFile>>

    /** @throws IOException se a porta estiver ocupada. */
    fun startServer()
    fun stop()
}

/**
 * Servidor HTTP local da transferência pelo celular.
 *
 * - GET  /             página web (responsiva) de envio
 * - GET  /api/info     confere o PIN e devolve consoles, limite e espaço livre
 * - POST /api/upload   corpo = bytes do arquivo (streaming direto para o disco, nunca na RAM)
 *
 * Proteções: PIN de 4 dígitos em todas as chamadas da API, bloqueio temporário após erros de
 * PIN, limite de tamanho por arquivo, checagem de espaço e só extensões de jogos.
 */
class PhoneTransferServer(
    port: Int,
    private val pin: String,
    private val catalog: SystemCatalog,
    private val destination: TransferDestination,
    private val page: () -> InputStream,
    private val maxFileBytes: Long = DEFAULT_MAX_BYTES,
    private val onFileReceived: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : NanoHTTPD(port), TransferServerHandle {

    private val json = Json { encodeDefaults = true }
    private val ids = AtomicLong()
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()
    private val extensions = catalog.all.flatMap { it.extensions }.toSet()

    private val _incoming = MutableStateFlow<List<IncomingFile>>(emptyList())
    override val incoming: StateFlow<List<IncomingFile>> = _incoming.asStateFlow()

    override fun startServer() = start(SOCKET_TIMEOUT_MS, false)

    override fun serve(session: IHTTPSession): Response = try {
        val path = session.uri.substringBefore('?')
        when {
            session.method == Method.GET && (path == "/" || path == "/index.html") ->
                newChunkedResponse(Response.Status.OK, "text/html; charset=utf-8", page())
            session.method == Method.GET && path == "/api/info" -> withPin(session) { info() }
            session.method == Method.POST && path == "/api/upload" -> withPin(session) { upload(session) }
            else -> error(Response.Status.NOT_FOUND, "not_found")
        }
    } catch (e: Exception) {
        error(Response.Status.INTERNAL_ERROR, "io")
    }

    // ------------------------------------------------------------------ PIN

    private fun withPin(session: IHTTPSession, block: () -> Response): Response {
        val ip = session.remoteIpAddress ?: "?"
        val (count, until) = failures[ip] ?: (0 to 0L)
        if (until > clock()) return error(Response.Status.FORBIDDEN, "locked", close = true)
        if (session.headers["x-pin"] != pin) {
            val n = count + 1
            failures[ip] = n to (if (n >= MAX_PIN_ATTEMPTS) clock() + LOCK_MS else 0L)
            return error(Response.Status.UNAUTHORIZED, "bad_pin", close = true)
        }
        failures.remove(ip)
        return block()
    }

    // ------------------------------------------------------------------ API

    @Serializable
    private data class Console(val id: String, val name: String, val extensions: List<String>)

    @Serializable
    private data class Info(val consoles: List<Console>, val maxBytes: Long, val freeBytes: Long)

    @Serializable
    private data class UploadResult(val ok: Boolean, val error: String? = null, val system: String? = null, val bios: Boolean = false, val name: String? = null)

    private fun info(): Response = jsonResponse(
        Response.Status.OK,
        json.encodeToString(
            Info.serializer(),
            Info(catalog.all.map { Console(it.id.value, it.displayName, it.extensions.sorted()) }, maxFileBytes, destination.freeBytes()),
        ),
    )

    private fun upload(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toLongOrNull()
            ?: return error(Response.Status.LENGTH_REQUIRED, "length_required", close = true)
        val name = sanitize(session.parameters["name"]?.firstOrNull())
            ?: return error(Response.Status.BAD_REQUEST, "bad_name", close = true)
        if (SystemDetector.extensionOf(name) !in extensions) {
            return error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "not_a_game", close = true)
        }
        val requested = session.parameters["system"]?.firstOrNull()
            ?.takeIf { it != "auto" }?.let(::SystemId)?.takeIf { catalog.byId(it) != null }
        if (length > maxFileBytes) return error(TOO_LARGE, "too_big", close = true)
        if (length + SPACE_MARGIN > destination.freeBytes()) return error(INSUFFICIENT_STORAGE, "no_space", close = true)

        val id = ids.incrementAndGet()
        track(IncomingFile(id, name, 0, length, IncomingFile.Status.RECEIVING))
        val temp = destination.tempFile(name)
        try {
            receive(session.inputStream, temp, length) { received -> update(id) { it.copy(received = received) } }
        } catch (e: IOException) {
            temp.delete()
            update(id) { it.copy(status = IncomingFile.Status.FAILED, error = TransferError.INTERRUPTED) }
            return error(Response.Status.BAD_REQUEST, "interrupted", close = true)
        }
        return when (val placement = destination.accept(temp, name, requested)) {
            Placement.NotAGame -> {
                update(id) { it.copy(status = IncomingFile.Status.FAILED, error = TransferError.NOT_A_GAME) }
                error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "not_a_game")
            }
            is Placement.Bios -> {
                update(id) { it.copy(received = length, status = IncomingFile.Status.DONE, isBios = true) }
                onFileReceived()
                jsonResponse(Response.Status.OK, json.encodeToString(UploadResult.serializer(), UploadResult(true, bios = true, name = placement.file.name)))
            }
            is Placement.Game -> {
                update(id) { it.copy(received = length, status = IncomingFile.Status.DONE, system = placement.system) }
                onFileReceived()
                val result = UploadResult(true, system = placement.system?.value, name = placement.file.name)
                jsonResponse(Response.Status.OK, json.encodeToString(UploadResult.serializer(), result))
            }
        }
    }

    /** Copia exatamente [length] bytes do corpo da requisição para o disco. */
    private fun receive(input: InputStream, dest: File, length: Long, onProgress: (Long) -> Unit) {
        dest.outputStream().buffered(256 * 1024).use { out ->
            val buf = ByteArray(64 * 1024)
            var left = length
            var lastReport = 0L
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw IOException("conexão encerrada antes do fim")
                out.write(buf, 0, n)
                left -= n
                val received = length - left
                if (received - lastReport >= PROGRESS_STEP || left == 0L) {
                    onProgress(received)
                    lastReport = received
                }
            }
        }
    }

    private fun track(file: IncomingFile) = _incoming.update { (listOf(file) + it).take(MAX_TRACKED) }
    private fun update(id: Long, f: (IncomingFile) -> IncomingFile) = _incoming.update { list -> list.map { if (it.id == id) f(it) else it } }

    private fun jsonResponse(status: Response.IStatus, body: String) =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun error(status: Response.IStatus, code: String, close: Boolean = false): Response =
        jsonResponse(status, json.encodeToString(UploadResult.serializer(), UploadResult(false, error = code))).also {
            // Corpo não lido: fecha a conexão para os bytes restantes não serem lidos como outra requisição.
            if (close) it.closeConnection(true)
        }

    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB (maiores romsets de Neo Geo ~700 MB)
        const val MAX_PIN_ATTEMPTS = 5
        const val LOCK_MS = 60_000L
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val SPACE_MARGIN = 64L * 1024 * 1024
        private const val PROGRESS_STEP = 256L * 1024
        private const val MAX_TRACKED = 30

        private val TOO_LARGE = object : Response.IStatus {
            override fun getDescription() = "413 Payload Too Large"
            override fun getRequestStatus() = 413
        }
        private val INSUFFICIENT_STORAGE = object : Response.IStatus {
            override fun getDescription() = "507 Insufficient Storage"
            override fun getRequestStatus() = 507
        }

        /** Só o nome do arquivo, sem caminho, sem caracteres de controle, sem começar com ponto. */
        fun sanitize(raw: String?): String? {
            val name = raw?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
                ?.filter { it >= ' ' && it !in "<>:\"|?*" }
                ?.trimStart('.')
                ?.take(180)
            return name?.takeIf { it.isNotBlank() && '.' in it }
        }
    }
}
