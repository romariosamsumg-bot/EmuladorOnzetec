package com.retroplay.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.saves.SaveStateStore
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.domain.system.SystemRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryTest {

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

    private lateinit var db: LibraryDatabase
    private val settings = MemSettings()
    private lateinit var repo: RoomProfileRepository
    private lateinit var library: RoomLibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).addCallback(CREATE_DEFAULT_PROFILE).allowMainThreadQueries().build()
        repo = RoomProfileRepository(db, settings, listOf(tmp.root))
        library = RoomLibraryRepository(
            db, androidx.work.WorkManager.getInstance(context),
            GamesFolderManager(context, SystemRegistry), com.retroplay.data.scan.FileAccess(context.contentResolver),
            activeProfile = settings.snapshots.map { it.raw["g|profile.active"]?.toLongOrNull() ?: 1L },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun perfis_tem_favoritos_e_historico_separados() = runTest {
        db.folders().upsert(FolderEntity(1, "/Jogos", "FILE", "Jogos", true, 0))
        db.games().upsert(listOf(GameEntity(uri = "/Jogos/mario.sfc", source = "FILE", folderId = 1, fileName = "mario.sfc", sizeBytes = 1, lastModified = 1,
            system = "snes", title = "Mario", datName = null, region = null, year = null, developer = null, genre = null, players = null, thumbs = 0,
            thumbName = null, crc = null, status = "IDENTIFIED", romsetStatus = "NOT_APPLICABLE", missingFiles = 0, addedAt = 0, gameKey = "snes/mario")))
        val id = db.games().byId(1)!!.id

        assertEquals(listOf("Jogador 1"), repo.profiles.first().map { it.name })
        library.setFavorite(id, true)
        library.recordPlayStarted(id, 500)

        val ana = repo.create("Ana")
        assertEquals(Profile.colorFor(1), ana.color)
        repo.switchTo(ana.id)
        assertEquals(ana.id, repo.activeId())
        val forAna = library.observeAll().first().single()
        assertFalse(forAna.isFavorite)
        assertNull(forAna.lastPlayedAt)

        repo.switchTo(1)
        val forP1 = library.observeAll().first().single()
        assertTrue(forP1.isFavorite)
        assertEquals(1, forP1.playCount)
    }

    @Test
    fun apagar_perfil_remove_historico_e_saves_mas_nunca_o_ultimo() = runTest {
        assertFalse(repo.delete(1))
        val ana = repo.create("  Ana  ")
        assertEquals("Ana", ana.name)
        repo.switchTo(ana.id)
        val saves = SaveStateStore(tmp.root) { ana.id }
        saves.write(SystemRegistry.Ids.SNES, "mario.sfc", 1, byteArrayOf(1))
        assertTrue(java.io.File(tmp.root, "p${ana.id}/snes/mario/slot_1.state").isFile)
        // O perfil 1 não enxerga os saves da Ana.
        assertNull(saves.forProfile(1).read(SystemRegistry.Ids.SNES, "mario.sfc", 1))

        assertTrue(repo.delete(ana.id))
        assertFalse(java.io.File(tmp.root, "p${ana.id}").exists())
        assertEquals(1L, repo.activeId())
        assertEquals(1, repo.profiles.first().size)
        repo.rename(1, "Casa")
        assertEquals("Casa", repo.active.first().name)
    }
}
