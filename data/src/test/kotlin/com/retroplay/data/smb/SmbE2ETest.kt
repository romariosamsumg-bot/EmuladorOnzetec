package com.retroplay.data.smb

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.dat.DatDatabase
import com.retroplay.data.library.FolderEntity
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.data.rom.RomPreparer
import com.retroplay.data.scan.ArchiveReader
import com.retroplay.data.scan.FileAccess
import com.retroplay.data.scan.LibraryScanner
import com.retroplay.data.testing.CrcForge
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pasta da rede contra um Samba de verdade (smbd local na porta 4455).
 * Rode com: ./gradlew :data:testDebugUnitTest --tests '*SmbE2E*' -Pretroplay.smb=true
 * (precisa do Samba instalado e de root para criar o usuário de teste).
 */
@RunWith(RobolectricTestRunner::class)
class SmbE2ETest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var smbd: Process
    private lateinit var share: File
    private lateinit var conf: File
    private val port = 4455
    private val access = SmbAccess { loc -> if (loc.share == "privado") SmbCredentials("rptester", "Senha123") else null }

    private fun sh(vararg cmd: String, input: String? = null): Int {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        input?.let { p.outputStream.use { o -> o.write(it.toByteArray()) } }
        p.inputStream.readBytes()
        return p.waitFor()
    }

    @Before
    fun startSamba() {
        assumeTrue("defina -Pretroplay.smb=true", System.getProperty("retroplay.smb") == "true")
        assumeTrue("smbd não instalado", File("/usr/sbin/smbd").exists())
        val base = tmp.newFolder("samba")
        share = File(base, "jogos").apply { mkdirs() }
        val priv = File(base, "priv").apply { mkdirs() }
        listOf("run", "lock", "state", "cache", "private", "ncalrpc").forEach { File(base, it).mkdirs() }
        conf = File(base, "smb.conf")
        conf.writeText(
            """
            [global]
              server role = standalone server
              smb ports = $port
              interfaces = 127.0.0.1
              bind interfaces only = yes
              pid directory = $base/run
              lock directory = $base/lock
              state directory = $base/state
              cache directory = $base/cache
              private dir = $base/private
              ncalrpc dir = $base/ncalrpc
              log file = $base/log.%m
              map to guest = Bad User
              guest account = root
              server min protocol = SMB2
              load printers = no
              disable spoolss = yes
            [jogos]
              path = $share
              read only = no
              guest ok = yes
              force user = root
            [privado]
              path = $priv
              valid users = rptester
              read only = yes
              force user = root
            """.trimIndent(),
        )
        sh("useradd", "-M", "rptester")
        assertEquals(0, sh("smbpasswd", "-c", conf.path, "-a", "-s", "rptester", input = "Senha123\nSenha123\n"))
        File(priv, "segredo.sfc").writeBytes(ByteArray(1024))
        // Sessão própria (setsid): ao sair, o smbd sinaliza o grupo de processos dele, não o do Gradle.
        smbd = ProcessBuilder("setsid", "/usr/sbin/smbd", "--foreground", "--no-process-group", "--debug-stdout", "-s", conf.path)
            .redirectErrorStream(true).redirectOutput(File(base, "smbd.out")).start()
        // Espera a porta abrir.
        repeat(50) {
            if (runCatching { java.net.Socket("127.0.0.1", port).close() }.isSuccess) return
            Thread.sleep(200)
        }
        fail("smbd não subiu: " + File(base, "smbd.out").readText().takeLast(2000))
    }

    @After
    fun stop() {
        if (::smbd.isInitialized) {
            access.close()
            sh("pkill", "-f", conf.path)
            smbd.waitFor()
        }
    }

    private fun root(shareName: String = "jogos", path: String = "") = SmbLocation("127.0.0.1", port, shareName, path)

    @Test
    fun varredura_identifica_jogos_na_rede_e_prepara_para_jogar() = runTest {
        // Super Mario World com o CRC do DAT No-Intro, dentro de um .zip em subpasta.
        val smw = CrcForge.fake("smw", 0xB19ED489L)
        File(share, "Super Nintendo").mkdirs()
        ZipOutputStream(File(share, "Super Nintendo/mario.zip").outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Super Mario World (USA).sfc")); z.write(smw); z.closeEntry()
            z.putNextEntry(ZipEntry("leia.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        File(share, ".oculto").writeText("x")

        assertEquals(2, access.test(root(), SmbCredentials()))
        val found = access.walk(root().uri()).toList()
        assertEquals(listOf("mario.zip"), found.map { it.name })
        assertEquals(listOf("Super Nintendo"), found.single().parents)

        // Índice do zip lido pela rede com posicionamento (sem baixar tudo).
        val entries = access.openChannel(found.single().uri).use { ArchiveReader.entries(it, "zip") }
        assertEquals(setOf("Super Mario World (USA).sfc", "leia.txt"), entries.map { it.name }.toSet())

        // Varredura completa da biblioteca numa pasta SMB.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        val files = FileAccess(context.contentResolver, access)
        db.folders().upsert(FolderEntity(uri = root().uri(), source = GameSource.SMB.name, displayName = "rede", isDefault = false, addedAt = 0))
        LibraryScanner(db, files, RomIdentifier(SystemRegistry, DatDatabase(context)), SystemRegistry) { 1L }.scan()
        val game = db.games().observeAll(1).first().single().game
        assertEquals("snes", game.system)
        assertEquals("IDENTIFIED", game.status)
        assertTrue(game.title.startsWith("Super Mario World"))
        db.close()

        // Para jogar: copia da rede para o cache e extrai a ROM.
        val rom = RomPreparer(tmp.newFolder("cache"), files).prepare(game.uri, game.fileName, SystemRegistry.SNES.extensions)
        assertTrue(rom.readBytes().contentEquals(smw))

        // Apagar pela rede.
        assertTrue(files.delete(game.uri))
        assertFalse(File(share, "Super Nintendo/mario.zip").exists())
    }

    @Test
    fun usuario_e_senha() {
        assertEquals(1, access.test(root("privado"), SmbCredentials("rptester", "Senha123")))
        assertEquals("segredo.sfc", access.walk(root("privado").uri()).single().name)
        try {
            SmbAccess { null }.test(root("privado"), SmbCredentials("rptester", "errada"))
            fail("senha errada deveria falhar")
        } catch (e: SmbException) {
            assertTrue(e.reason == SmbException.Reason.LOGIN_FAILED || e.reason == SmbException.Reason.SHARE_NOT_FOUND)
        }
        try {
            access.test(root("naoexiste"), SmbCredentials())
            fail()
        } catch (e: SmbException) {
            assertEquals(SmbException.Reason.SHARE_NOT_FOUND, e.reason)
        }
        try {
            access.test(root("jogos", "sem/pasta"), SmbCredentials())
            fail()
        } catch (e: SmbException) {
            assertEquals(SmbException.Reason.PATH_NOT_FOUND, e.reason)
        }
        try {
            SmbAccess { null }.test(SmbLocation("127.0.0.1", 4499, "x", ""), SmbCredentials())
            fail()
        } catch (e: SmbException) {
            assertEquals(SmbException.Reason.UNREACHABLE, e.reason)
        }
    }
}
