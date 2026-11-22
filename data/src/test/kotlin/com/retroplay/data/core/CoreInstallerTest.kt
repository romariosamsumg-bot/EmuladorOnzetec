package com.retroplay.data.core

import com.retroplay.domain.core.CoreError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var storage: CoreStorage
    private var free = Long.MAX_VALUE

    private val fakeSo = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) + ByteArray(5000) { it.toByte() }

    @Before
    fun setUp() {
        server.start()
        storage = CoreStorage(tmp.newFolder("cores"))
    }

    @After
    fun tearDown() = server.close()

    private fun installer() = CoreInstaller(
        client = OkHttpClient(),
        storage = storage,
        freeSpace = { free },
        urlFor = { core, abi -> server.url("/$abi/${core}_libretro_android.so.zip").toString() },
        clock = { 1234L },
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun enqueue(body: ByteArray, code: Int = 200, etag: String? = "\"abc123\"") {
        val b = MockResponse.Builder().code(code).body(Buffer().write(body))
        if (etag != null) b.addHeader("ETag", etag)
        server.enqueue(b.build())
    }

    private suspend fun expectError(expected: CoreError) {
        try {
            installer().install("snes9x2010", "armeabi-v7a")
            fail("deveria falhar com $expected")
        } catch (e: CoreInstallException) {
            assertEquals(expected, e.error)
        }
        assertNull(storage.read("snes9x2010", "armeabi-v7a"))
        assertFalse(storage.libraryFile("snes9x2010", "armeabi-v7a").exists())
    }

    @Test
    fun `instala nucleo com versao hash e tamanho`() = runTest {
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo))
        val progress = mutableListOf<Pair<Long, Long>>()
        val installed = installer().install("snes9x2010", "armeabi-v7a") { d, t -> progress += d to t }

        assertEquals("/armeabi-v7a/snes9x2010_libretro_android.so.zip", server.takeRequest().url.encodedPath)
        assertEquals("abc123", installed.version)
        assertEquals(fakeSo.size.toLong(), installed.sizeBytes)
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(fakeSo).joinToString("") { "%02x".format(it) }
        assertEquals(expectedSha, installed.sha256)
        assertTrue(progress.isNotEmpty())
        assertEquals(progress.last().first, progress.last().second)

        // Persistido e relido do disco.
        val reread = storage.read("snes9x2010", "armeabi-v7a")
        assertNotNull(reread)
        assertEquals(installed, reread)
        assertTrue(storage.libraryFile("snes9x2010", "armeabi-v7a").readBytes().contentEquals(fakeSo))
        // Nada sobra na pasta temporária.
        assertEquals(0, storage.tempDir().listFiles()!!.size)
    }

    @Test
    fun `usa last-modified quando nao ha etag`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(Buffer().write(zipOf("snes9x2010_libretro_android.so" to fakeSo)))
                .addHeader("Last-Modified", "Sat, 21 Nov 2026 09:13:58 GMT").build(),
        )
        assertEquals("Sat, 21 Nov 2026 09:13:58 GMT", installer().install("snes9x2010", "armeabi-v7a").version)
    }

    @Test
    fun `http 404 vira erro de servidor`() = runTest {
        enqueue("nao existe".toByteArray(), code = 404)
        expectError(CoreError.SERVER)
    }

    @Test
    fun `zip corrompido e detectado`() = runTest {
        enqueue(ByteArray(3000) { 0x42 })
        expectError(CoreError.CORRUPTED)
    }

    @Test
    fun `zip sem o so esperado e corrompido`() = runTest {
        enqueue(zipOf("outro_libretro_android.so" to fakeSo))
        expectError(CoreError.CORRUPTED)
    }

    @Test
    fun `arquivo que nao e ELF e rejeitado`() = runTest {
        enqueue(zipOf("snes9x2010_libretro_android.so" to "<html>erro</html>".toByteArray()))
        expectError(CoreError.CORRUPTED)
    }

    @Test
    fun `sem espaco livre nao baixa`() = runTest {
        free = 1000
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo))
        expectError(CoreError.NO_SPACE)
    }

    @Test
    fun `servidor fora do ar vira sem rede`() = runTest {
        server.close()
        expectError(CoreError.NO_NETWORK)
    }

    @Test
    fun `abi sem suporte`() = runTest {
        try {
            installer().install("snes9x2010", "mips")
            fail()
        } catch (e: CoreInstallException) {
            assertEquals(CoreError.UNSUPPORTED_ABI, e.error)
        }
    }

    @Test
    fun `reinstalar substitui a versao anterior`() = runTest {
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo), etag = "\"v1\"")
        installer().install("snes9x2010", "armeabi-v7a")
        val v2 = fakeSo + byteArrayOf(1, 2, 3)
        enqueue(zipOf("snes9x2010_libretro_android.so" to v2), etag = "\"v2\"")
        val installed = installer().install("snes9x2010", "armeabi-v7a")
        assertEquals("v2", installed.version)
        assertEquals(v2.size.toLong(), storage.read("snes9x2010", "armeabi-v7a")!!.sizeBytes)
    }

    @Test
    fun `atualizar guarda a versao anterior e permite voltar`() = runTest {
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo), etag = "\"v1\"")
        installer().install("snes9x2010", "armeabi-v7a")
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo + byteArrayOf(9)), etag = "\"v2\"")
        installer().install("snes9x2010", "armeabi-v7a")
        assertTrue(storage.hasPrevious("snes9x2010", "armeabi-v7a"))
        assertTrue(storage.rollback("snes9x2010", "armeabi-v7a"))
        assertEquals("v1", storage.read("snes9x2010", "armeabi-v7a")!!.version)
        assertFalse(storage.hasPrevious("snes9x2010", "armeabi-v7a"))
    }

    @Test
    fun `versao remota sem baixar`() = runTest {
        server.enqueue(MockResponse.Builder().addHeader("ETag", "\"v9\"").build())
        assertEquals("v9", installer().remoteVersion("snes9x2010", "armeabi-v7a"))
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `so apagado por fora conta como nao instalado`() = runTest {
        enqueue(zipOf("snes9x2010_libretro_android.so" to fakeSo))
        installer().install("snes9x2010", "armeabi-v7a")
        storage.libraryFile("snes9x2010", "armeabi-v7a").writeBytes(byteArrayOf(1))
        assertNull(storage.read("snes9x2010", "armeabi-v7a"))
    }
}
