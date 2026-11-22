package com.retroplay.data.settings

import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.CoreOption
import com.retroplay.domain.settings.GameKey
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.system.SystemRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val snes = SystemRegistry.Ids.SNES
    private val game = GameKey.of(snes, "Super Mario World (USA).sfc")

    @Test
    fun `grava resolve e remove personalizacoes`() = runTest {
        val repo = DataStoreSettingsRepository(File(tmp.root, "s.preferences_pb"), backgroundScope)
        repo.set(AppSettings.SHADER, ScopeRef.Global, "crt")
        repo.set(AppSettings.SHADER, ScopeRef.system(snes), "lcd")
        repo.set(AppSettings.SHADER, ScopeRef.game(game), "sharp")
        var s = repo.current()
        assertEquals("sharp", s.value(AppSettings.SHADER, snes, game))
        repo.set(AppSettings.SHADER, ScopeRef.game(game), null)
        s = repo.current()
        assertEquals(Scope.SYSTEM, s.resolve(AppSettings.SHADER, snes, game).source)
        repo.setRaw(AppSettings.coreOptionKey("snes9x2010", "a"), ScopeRef.system(snes), "1")
        repo.setRaw(AppSettings.coreOptionKey("snes9x2010", "b"), ScopeRef.system(snes), "2")
        repo.clear(ScopeRef.system(snes), AppSettings.coreOptionPrefix("snes9x2010"))
        s = repo.current()
        assertTrue(s.keysIn(ScopeRef.system(snes), "coreopt.").isEmpty())
        assertEquals("lcd", s.value(AppSettings.SHADER, snes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `configuracao global nao aceita nivel de console`() = runTest {
        DataStoreSettingsRepository(File(tmp.root, "x.preferences_pb"), backgroundScope).set(AppSettings.THEME, ScopeRef.system(snes), "oled")
    }

    @Test
    fun `cache de opcoes de nucleo`() {
        val cache = CoreOptionsCache(tmp.newFolder("co"))
        cache.save("snes9x2010", listOf(CoreOption.parse("snes9x_2010_aspect", "Aspect; 4:3|uncorrected", null)))
        val loaded = cache.load("snes9x2010").single()
        assertEquals(listOf("4:3", "uncorrected"), loaded.values)
        assertTrue(cache.load("fbneo").isEmpty())
    }

    @Test
    fun `uso de armazenamento`() {
        val files = tmp.newFolder("files")
        File(files, "states/snes/x").mkdirs()
        File(files, "states/snes/x/slot_1.state").writeBytes(ByteArray(100))
        File(files, "cores/arm64-v8a").mkdirs()
        File(files, "cores/arm64-v8a/a.so").writeBytes(ByteArray(50))
        val r = StorageUsage.report(files, tmp.newFolder("cache"), gamesBytes = 7)
        assertEquals(100L, r.saves)
        assertEquals(50L, r.cores)
        assertEquals(7L, r.games)
    }
}
