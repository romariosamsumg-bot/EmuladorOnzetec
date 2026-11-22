package com.retroplay.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.retroplay.data.scan.FoundFile
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Endereço smb://servidor[:porta]/compartilhamento/caminho. */
data class SmbLocation(val host: String, val port: Int, val share: String, val path: String) {
    /** Caminho no formato do SMB (barras invertidas). */
    val smbPath: String get() = path.trim('/').replace('/', '\\')

    fun child(name: String) = copy(path = if (path.isEmpty()) name else "$path/$name")

    fun uri(): String = buildString {
        append("smb://").append(host)
        if (port != DEFAULT_PORT) append(':').append(port)
        append('/').append(enc(share))
        if (path.isNotEmpty()) append('/').append(path.split('/').joinToString("/", transform = ::enc))
    }

    val label: String get() = listOf(host, share, path).filter { it.isNotEmpty() }.joinToString("/")

    companion object {
        const val DEFAULT_PORT = 445

        fun isSmb(uri: String) = uri.startsWith("smb://")

        fun parse(uri: String): SmbLocation? {
            if (!isSmb(uri)) return null
            val rest = uri.removePrefix("smb://")
            val authority = rest.substringBefore('/')
            val parts = rest.substringAfter('/', "").split('/').filter { it.isNotEmpty() }.map(::dec)
            if (authority.isEmpty() || parts.isEmpty()) return null
            val host = authority.substringBefore(':')
            val port = authority.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
            return SmbLocation(host, port, parts.first(), parts.drop(1).joinToString("/"))
        }

        /** Monta a partir do que o usuário digitou (aceita "\\servidor\pasta" e "smb://"). */
        fun fromInput(host: String, share: String, path: String): SmbLocation? {
            val h = host.trim().removePrefix("smb://").trim('\\', '/')
            val s = share.trim().trim('\\', '/')
            if (h.isEmpty() || s.isEmpty() || '/' in s || '\\' in s) return null
            val p = path.trim().replace('\\', '/').trim('/')
            val port = h.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
            return SmbLocation(h.substringBefore(':'), port, s, p)
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}

/** Usuário vazio = convidado. */
data class SmbCredentials(val user: String = "", val password: String = "", val domain: String = "") {
    fun toAuth(): AuthenticationContext =
        if (user.isBlank()) AuthenticationContext.guest() else AuthenticationContext(user.trim(), password.toCharArray(), domain.trim())
}

/** Erros de rede traduzidos para mensagens ao usuário. */
class SmbException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { UNREACHABLE, LOGIN_FAILED, SHARE_NOT_FOUND, PATH_NOT_FOUND, OTHER }
}

/**
 * Pasta da rede (compartilhamento do Windows, NAS, roteador com USB). Reaproveita a conexão por
 * servidor/compartilhamento e reconecta sozinho se ela cair.
 */
class SmbAccess(private val credentials: (SmbLocation) -> SmbCredentials?) {

    private val client = SMBClient(
        SmbConfig.builder().withTimeout(15, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).withMultiProtocolNegotiate(true).build(),
    )
    private val shares = ConcurrentHashMap<String, DiskShare>()

    private fun key(loc: SmbLocation) = "${loc.host}:${loc.port}/${loc.share}".lowercase()

    @Synchronized
    private fun share(loc: SmbLocation, creds: SmbCredentials? = null): DiskShare {
        val k = key(loc)
        shares[k]?.let { if (it.isConnected && creds == null) return it else runCatching { it.close() } }
        val auth = (creds ?: credentials(loc) ?: SmbCredentials()).toAuth()
        val connection = try {
            client.connect(loc.host, loc.port)
        } catch (e: Exception) {
            throw SmbException(SmbException.Reason.UNREACHABLE, "Servidor inacessível: ${loc.host}", e)
        }
        val session = try {
            connection.authenticate(auth)
        } catch (e: Exception) {
            runCatching { connection.close() }
            throw SmbException(SmbException.Reason.LOGIN_FAILED, "Usuário ou senha incorretos", e)
        }
        val share = try {
            session.connectShare(loc.share) as DiskShare
        } catch (e: Exception) {
            runCatching { session.close(); connection.close() }
            throw SmbException(SmbException.Reason.SHARE_NOT_FOUND, "Compartilhamento não encontrado: ${loc.share}", e)
        }
        shares[k] = share
        return share
    }

    /** Executa com uma nova tentativa se a conexão guardada tiver caído. */
    private fun <T> withShare(loc: SmbLocation, block: (DiskShare) -> T): T = try {
        block(share(loc))
    } catch (e: SmbException) {
        throw e
    } catch (e: Exception) {
        shares.remove(key(loc))?.let { runCatching { it.close() } }
        block(share(loc))
    }

    /** Conecta com as credenciais informadas e conta os itens da pasta (para "Testar"). */
    fun test(loc: SmbLocation, creds: SmbCredentials): Int {
        val share = share(loc, creds)
        if (loc.path.isNotEmpty() && !share.folderExists(loc.smbPath)) {
            throw SmbException(SmbException.Reason.PATH_NOT_FOUND, "Pasta não encontrada: ${loc.path}")
        }
        return share.list(loc.smbPath).count { it.fileName != "." && it.fileName != ".." }
    }

    /** Lista recursivamente; lança [IOException] se a pasta não estiver acessível (não apaga jogos). */
    fun walk(rootUri: String, maxDepth: Int = 6): Sequence<FoundFile> {
        val root = SmbLocation.parse(rootUri) ?: throw IOException("Endereço SMB inválido: $rootUri")
        val share = try {
            share(root).also { if (root.path.isNotEmpty() && !it.folderExists(root.smbPath)) throw IOException("Pasta não encontrada") }
        } catch (e: Exception) {
            throw IOException("Pasta da rede inacessível: ${root.label}", e)
        }
        return sequence {
            suspend fun SequenceScope<FoundFile>.visit(dir: SmbLocation, parents: List<String>, depth: Int) {
                val entries = runCatching { share.list(dir.smbPath) }.getOrNull() ?: return
                for (e in entries.sortedBy { it.fileName.lowercase() }) {
                    val name = e.fileName
                    if (name == "." || name == ".." || name.startsWith(".")) continue
                    val isDir = e.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    if (isDir) {
                        if (depth < maxDepth) visit(dir.child(name), parents + name, depth + 1)
                    } else {
                        yield(FoundFile(dir.child(name).uri(), name, e.endOfFile, e.lastWriteTime.toEpochMillis(), parents))
                    }
                }
            }
            visit(root, emptyList(), 0)
        }
    }

    private fun openFile(uri: String): com.hierynomus.smbj.share.File {
        val loc = SmbLocation.parse(uri) ?: throw IOException("Endereço SMB inválido: $uri")
        return withShare(loc) { share ->
            share.openFile(loc.smbPath, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
        }
    }

    fun openStream(uri: String): InputStream {
        val file = openFile(uri)
        val inner = file.inputStream
        return object : InputStream() {
            override fun read() = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
            override fun close() { try { inner.close() } finally { file.close() } }
        }
    }

    /** Leitura com posicionamento (índice de .zip/.7z sem baixar o arquivo inteiro). */
    fun openChannel(uri: String): SeekableByteChannel = SmbChannel(openFile(uri))

    fun delete(uri: String): Boolean {
        val loc = SmbLocation.parse(uri) ?: return false
        return withShare(loc) { share ->
            if (share.fileExists(loc.smbPath)) share.rm(loc.smbPath)
            true
        }
    }

    fun close() {
        shares.values.forEach { runCatching { it.close() } }
        shares.clear()
        runCatching { client.close() }
    }
}

/** Canal somente leitura sobre um arquivo SMB. */
private class SmbChannel(private val file: com.hierynomus.smbj.share.File) : SeekableByteChannel {
    private var pos = 0L
    private var open = true
    private val size = file.fileInformation.standardInformation.endOfFile

    override fun read(dst: ByteBuffer): Int {
        if (pos >= size) return -1
        val n = minOf(dst.remaining().toLong(), size - pos, MAX_READ).toInt()
        if (n == 0) return 0
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = file.read(buf, pos + got, got, n - got)
            if (r <= 0) break
            got += r
        }
        if (got == 0) return -1
        dst.put(buf, 0, got)
        pos += got
        return got
    }

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun position() = pos
    override fun position(newPosition: Long): SeekableByteChannel = apply { pos = newPosition }
    override fun size() = size
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
    override fun isOpen() = open
    override fun close() {
        if (open) { open = false; file.close() }
    }

    private companion object {
        const val MAX_READ = 1L shl 20
    }
}
