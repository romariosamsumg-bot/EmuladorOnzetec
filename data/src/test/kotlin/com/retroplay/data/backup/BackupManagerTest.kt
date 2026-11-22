package com.retroplay.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.library.CREATE_DEFAULT_PROFILE
import com.retroplay.data.library.FolderEntity
import com.retroplay.data.library.GameEntity
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.data.library.PlayStatsEntity
import com.retroplay.data.library.ProfileEntity
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class MemSettings : SettingsRepository {
        val state = MutableStateFlow(SettingsSnapshot())
        override val snapshots = state
        override suspend fun current() = state.value
        override suspend fun <T : Any> set(def: SettingDef<T>, scope: ScopeRef, value: T?) = setRaw(def.key, scope, value?.let(def::encode))
        override suspend fun setRaw(key: String, scope: ScopeRef, value: String?) {
            val k = scope.prefix + key
            state.value = SettingsSnapshot(if (value == null) state.value.raw - k else state.value.raw + (k to value))
        }
        override suspend fun clear(scope: ScopeRef, keyPrefix: String) = Unit
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun newDb() = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).addCallback(CREATE_DEFAULT_PROFILE).allowMainThreadQueries().build()
    private lateinit var dbA: LibraryDatabase
    private lateinit var dbB: LibraryDatabase

    @Before
    fun setUp() { dbA = newDb(); dbB = newDb() }

    @After
    fun tearDown() { dbA.close(); dbB.close() }

    private suspend fun addGame(db: LibraryDatabase) {
        db.folders().upsert(FolderEntity(1, "/Jogos", "FILE", "Jogos", true, 0))
        db.games().upsert(listOf(GameEntity(uri = "/Jogos/mario.sfc", source = "FILE", folderId = 1, fileName = "mario.sfc", sizeBytes = 1, lastModified = 1,
            system = "snes", title = "Mario", datName = null, region = null, year = null, developer = null, genre = null, players = null, thumbs = 0,
            thumbName = null, crc = null, status = "IDENTIFIED", romsetStatus = "NOT_APPLICABLE", missingFiles = 0, addedAt = 0, gameKey = "snes/mario")))
    }

    @Test
    fun backup_e_restauracao_em_outra_tv() = runTest {
        // TV A: saves, configurações, perfil extra com favorito e nome personalizado.
        val filesA = tmp.newFolder("a")
        File(filesA, "states/snes/mario/slot_1.state").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        File(filesA, "saves/p2/snes/mario.srm").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(9)) }
        File(filesA, "cores/armeabi-v7a/snes9x2010.so").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(100)) }
        addGame(dbA)
        dbA.profiles().insert(ProfileEntity(2, "Ana", 7, 5))
        dbA.stats().upsert(listOf(PlayStatsEntity(2, "snes/mario", favorite = true, playCount = 4, lastPlayedAt = 99, playTimeMs = 1000)))
        dbA.games().rename(1, "Meu Mario")
        val settingsA = MemSettings().apply { setRaw("video.shader", ScopeRef.Global, "crt"); setRaw("console.md_pad", ScopeRef(com.retroplay.domain.settings.Scope.SYSTEM, "megadrive"), "3") }
        val out = ByteArrayOutputStream()
        val summary = BackupManager(filesA, dbA, settingsA).export(out)
        assertEquals(2, summary.files) // núcleos não entram
        assertEquals(2, summary.profiles)

        // TV B: biblioteca já varrida com o mesmo jogo, configurações diferentes.
        val filesB = tmp.newFolder("b")
        addGame(dbB)
        val settingsB = MemSettings().apply { setRaw("ui.theme", ScopeRef.Global, "oled") }
        val restored = BackupManager(filesB, dbB, settingsB).restore(out.toByteArray().inputStream())
        assertEquals(2, restored.files)
        assertTrue(File(filesB, "states/snes/mario/slot_1.state").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(File(filesB, "saves/p2/snes/mario.srm").isFile)
        assertFalse(File(filesB, "cores").exists())
        assertFalse(File(filesB, "restore-tmp").exists())
        assertEquals(mapOf("g|video.shader" to "crt", "s|megadrive|console.md_pad" to "3"), settingsB.current().raw)
        assertEquals(listOf("Jogador 1", "Ana"), dbB.profiles().all().map { it.name })
        val stats = dbB.games().byIdWithStats(1, 2)!!
        assertEquals(true, stats.favorite)
        assertEquals(4, stats.playCount)
        assertEquals("Meu Mario", stats.game.customTitle)
    }

    @Test
    fun zip_malicioso_nao_escreve_fora_das_pastas_de_saves() = runTest {
        val bytes = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z ->
                z.putNextEntry(ZipEntry(BackupManager.MANIFEST)); z.write("""{"format":1,"createdAt":1}""".toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("files/states/../../evil.txt")); z.write(1); z.closeEntry()
                z.putNextEntry(ZipEntry("files/shared_prefs/x.xml")); z.write(1); z.closeEntry()
                z.putNextEntry(ZipEntry("files/saves/ok.srm")); z.write(1); z.closeEntry()
            }
        }.toByteArray()
        val dir = tmp.newFolder("c")
        val r = BackupManager(File(dir, "files").apply { mkdirs() }, dbB, MemSettings()).restore(bytes.inputStream())
        assertEquals(1, r.files)
        assertFalse(File(dir, "evil.txt").exists())
        assertFalse(File(dir, "files/shared_prefs/x.xml").exists())
        assertTrue(File(dir, "files/saves/ok.srm").isFile)
    }

    @Test
    fun arquivo_que_nao_e_backup() = runTest {
        val zipSemManifesto = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("x.sfc")); z.write(1); z.closeEntry() } }.toByteArray()
        listOf(zipSemManifesto, "texto qualquer".toByteArray()).forEach { bytes ->
            try {
                BackupManager(tmp.newFolder(), dbB, MemSettings()).restore(bytes.inputStream())
                fail("deveria recusar")
            } catch (e: BackupException) {
                assertTrue(e.reason == BackupException.Reason.NOT_A_BACKUP || e.reason == BackupException.Reason.CORRUPTED)
            }
        }
    }

    @Test
    fun exportar_e_importar_so_configuracoes() = runTest {
        val a = MemSettings().apply { setRaw("emu.rewind", ScopeRef.Global, "true") }
        val out = ByteArrayOutputStream()
        assertEquals(1, BackupManager(tmp.root, dbA, a).exportSettings(out))
        val b = MemSettings().apply { setRaw("ui.theme", ScopeRef.Global, "oled") }
        assertEquals(1, BackupManager(tmp.root, dbB, b).importSettings(out.toByteArray().inputStream()))
        assertEquals(mapOf("g|emu.rewind" to "true"), b.current().raw)
        try {
            BackupManager(tmp.root, dbB, b).importSettings("{}lixo".byteInputStream()); fail()
        } catch (e: BackupException) {
            assertEquals(BackupException.Reason.NOT_A_BACKUP, e.reason)
        }
    }
}
