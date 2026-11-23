package com.retroplay.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.dat.DatDatabase
import com.retroplay.data.scan.FileAccess
import com.retroplay.data.testing.CrcForge
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.domain.transfer.IncomingFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class PhoneTransferServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val client = OkHttpClient()
    private lateinit var root: File
    private lateinit var server: PhoneTransferServer
    private var port = 0
    private var received = 0
    private var free = Long.MAX_VALUE / 4
    private val dat by lazy { DatDatabase(context) }

    private fun start(maxBytes: Long = PhoneTransferServer.DEFAULT_MAX_BYTES) {
        root = tmp.newFolder("Jogos")
        val destination = TransferDestination(
            root = { root },
            folderFor = { SystemRegistry.byId(it)!!.displayName },
            biosFolder = "BIOS",
            identifier = RomIdentifier(SystemRegistry, dat),
            files = FileAccess(context.contentResolver),
            catalog = SystemRegistry,
            freeSpace = { free },
        )
        port = ServerSocket(0).use { it.localPort }
        server = PhoneTransferServer(
            port, "4321", SystemRegistry, destination,
            page = { "<html>Retrô Play</html>".byteInputStream() },
            maxFileBytes = maxBytes,
            onFileReceived = { received++ },
        )
        server.startServer()
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
    }

    private fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun upload(name: String, bytes: ByteArray, pin: String = "4321", system: String = "auto") =
        client.newCall(
            Request.Builder()
                .url(url("/api/upload?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "&system=$system"))
                .header("X-Pin", pin)
                .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build(),
        ).execute().use { it.code to it.body.string() }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bos ->
        ZipOutputStream(bos).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }.toByteArray()

    private fun info(pin: String) = client.newCall(Request.Builder().url(url("/api/info")).header("X-Pin", pin).build()).execute().use { it.code to it.body.string() }

    @Test
    fun `pagina abre sem pin e a api exige pin`() {
        start()
        client.newCall(Request.Builder().url(url("/")).build()).execute().use {
            assertEquals(200, it.code)
            assertTrue(it.body.string().contains("Retrô Play"))
        }
        assertEquals(401, info("0000").first)
        val (code, body) = info("4321")
        assertEquals(200, code)
        assertTrue(body.contains("\"megadrive\"") && body.contains("\"Arcade\""))
    }

    @Test
    fun `bloqueia apos 5 pins errados`() {
        start()
        repeat(5) { assertEquals(401, info("1111").first) }
        assertEquals(403, info("4321").first)
    }

    @Test
    fun `rom de snes vai para a pasta do console identificado`() {
        start()
        val (code, body) = upload("Super Mario World (USA).sfc", CrcForge.fake("smw", 0xB19ED489L))
        assertEquals(200, code)
        assertTrue(body.contains("\"system\":\"snes\""))
        assertTrue(File(root, "Super Nintendo/Super Mario World (USA).sfc").isFile)
        val tracked = server.incoming.value.single()
        assertEquals(IncomingFile.Status.DONE, tracked.status)
        assertEquals(SystemRegistry.Ids.SNES, tracked.system)
        assertEquals(1, received)
        // Nada sobra na pasta temporária.
        assertEquals(0, File(root, TransferDestination.TEMP_DIR).listFiles()!!.size)
    }

    @Test
    fun `zip com rom de mega drive vai para mega drive e neogeo zip para bios`() {
        start()
        assertEquals(200, upload("jogo.zip", zip("Jogo.md" to CrcForge.fake("md", 0x1234L))).first)
        assertTrue(File(root, "Mega Drive/jogo.zip").isFile)
        val (_, roms) = dat.romset(SystemRegistry.Ids.ARCADE, "neogeo")!!
        val bios = zip(*roms.map { it.name to CrcForge.fake(it.name, it.crc) }.toTypedArray())
        val (code, body) = upload("neogeo.zip", bios)
        assertEquals(200, code)
        assertTrue(body.contains("\"bios\":true"))
        assertTrue(File(root, "BIOS/neogeo.zip").isFile)
    }

    @Test
    fun `console escolhido no celular vale para arquivos nao reconhecidos`() {
        start()
        assertEquals(200, upload("quebrado.zip", ByteArray(300) { 7 }, system = "arcade").first)
        assertTrue(File(root, "Arcade/quebrado.zip").isFile)
        // Sem escolha: fica na raiz da pasta Jogos (a TV pergunta o console depois).
        assertEquals(200, upload("outro.zip", ByteArray(300) { 9 }).first)
        assertTrue(File(root, "outro.zip").isFile)
    }

    @Test
    fun `rejeita arquivo grande demais extensao invalida e o que nao e jogo`() {
        start(maxBytes = 1000)
        assertEquals(413, upload("grande.sfc", ByteArray(5000)).first)
        assertEquals(415, upload("virus.exe", ByteArray(10)).first)
        val (code, body) = upload("docs.zip", zip("leiame.pdf" to ByteArray(10)))
        assertEquals(415, code)
        assertTrue(body.contains("not_a_game"))
        assertFalse(File(root, "docs.zip").exists())
        assertEquals(0, received)
    }

    @Test
    fun `sem espaco na tv`() {
        start()
        free = 1000
        assertEquals(507, upload("jogo.sfc", ByteArray(2000)).first)
    }

    @Test
    fun `nome malicioso e duplicado`() {
        start()
        assertEquals(200, upload("../../../etc/evil.sfc", ByteArray(64) { 1 }).first)
        assertTrue(File(root, "Super Nintendo/evil.sfc").isFile)
        assertEquals(200, upload("evil.sfc", ByteArray(64) { 2 }).first)
        assertTrue(File(root, "Super Nintendo/evil (2).sfc").isFile)
        assertEquals(null, PhoneTransferServer.sanitize("..."))
        assertEquals("a b.sfc", PhoneTransferServer.sanitize("C:\\x\\a b.sfc"))
    }

    @Test
    fun `envio grande em streaming chega inteiro`() {
        start()
        val big = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        assertEquals(200, upload("grande.md", big).first)
        val f = File(root, "Mega Drive/grande.md")
        assertTrue(f.readBytes().contentEquals(big))
    }
}
