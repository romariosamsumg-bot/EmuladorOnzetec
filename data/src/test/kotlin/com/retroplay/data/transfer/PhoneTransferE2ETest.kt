package com.retroplay.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.dat.DatDatabase
import com.retroplay.data.scan.FileAccess
import com.retroplay.data.testing.CrcForge
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.domain.transfer.PhoneTransferState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Ponta a ponta: a página REAL (assets/web/index.html) servida pelo servidor REAL e usada pelo
 * Chromium como um celular. Precisa de Node + Playwright; desligado por padrão.
 * Rodar: ./gradlew :data:testDebugUnitTest -Pretroplay.e2e=true --tests '*PhoneTransferE2ETest*'
 */
@RunWith(RobolectricTestRunner::class)
class PhoneTransferE2ETest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `celular envia jogos pela pagina e eles caem na pasta certa`() {
        assumeTrue(System.getProperty("retroplay.e2e") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = tmp.newFolder("Jogos")
        val destination = TransferDestination(
            root = { root },
            folderFor = { SystemRegistry.byId(it)!!.displayName },
            biosFolder = "BIOS",
            identifier = RomIdentifier(SystemRegistry, DatDatabase(context)),
            files = FileAccess(context.contentResolver),
            catalog = SystemRegistry,
        )
        val service = PhoneTransferService(
            serverFactory = { port, pin, cb ->
                PhoneTransferServer(port, pin, SystemRegistry, destination, page = { context.assets.open("web/index.html") }, onFileReceived = cb)
            },
            onLibraryChanged = {},
            addresses = { listOf("127.0.0.1") },
            firstPort = { 18080 },
        )
        service.acquire()
        val running = service.state.value as PhoneTransferState.Running
        try {
            val upload = tmp.newFolder("celular")
            val snes = File(upload, "Super Mario World (USA).sfc").apply { writeBytes(CrcForge.fake("smw", 0xB19ED489L)) }
            val md = File(upload, "Sonic.zip").apply {
                ZipOutputStream(outputStream()).use { z -> z.putNextEntry(ZipEntry("Sonic.md")); z.write(ByteArray(4096) { 3 }); z.closeEntry() }
            }
            val shots = File("build/screenshots").apply { mkdirs() }
            val script = File("src/test/e2e/phone_transfer.js").absolutePath
            val process = ProcessBuilder("node", script, running.url, running.qrUrl, shots.absolutePath, snes.absolutePath, md.absolutePath)
                .redirectErrorStream(true)
                .apply { environment()["NODE_PATH"] = System.getenv("NODE_PATH") ?: "" }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            println(output)
            assertEquals("Playwright: $output", 0, exit)
            assertTrue(output.contains("Super Nintendo") && output.contains("Mega Drive"))
            assertTrue(File(root, "Super Nintendo/Super Mario World (USA).sfc").isFile)
            assertTrue(File(root, "Mega Drive/Sonic.zip").isFile)
        } finally {
            service.release()
        }
    }
}
