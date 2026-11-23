package com.retroplay.data.migration

import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemRenameMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class Mem(initial: Map<String, String>) : SettingsRepository {
        val state = MutableStateFlow(SettingsSnapshot(initial))
        override val snapshots = state
        override suspend fun current() = state.value
        override suspend fun <T : Any> set(def: SettingDef<T>, scope: ScopeRef, value: T?) = Unit
        override suspend fun setRaw(key: String, scope: ScopeRef, value: String?) = Unit
        override suspend fun clear(scope: ScopeRef, keyPrefix: String) = Unit
        override suspend fun importRaw(raw: Map<String, String>, replace: Boolean) { state.value = SettingsSnapshot(raw) }
    }

    @Test
    fun configuracoes_e_saves_do_neo_geo_viram_arcade() = runTest {
        val settings = Mem(mapOf(
            "g|video.shader" to "crt",
            "s|neogeo|console.neogeo_mode" to "aes",
            "j|neogeo/mslug|emu.rewind" to "true",
            "s|snes|video.shader" to "lcd",
        ))
        val states = tmp.newFolder("states")
        val saves = tmp.newFolder("saves")
        File(states, "neogeo/mslug/slot_1.state").apply { parentFile!!.mkdirs(); writeText("1") }
        File(states, "p2/neogeo/kof98/slot_0.state").apply { parentFile!!.mkdirs(); writeText("2") }
        File(saves, "neogeo/mslug.srm").apply { parentFile!!.mkdirs(); writeText("3") }
        File(states, "arcade/sf2/slot_1.state").apply { parentFile!!.mkdirs(); writeText("4") } // já no nome novo

        val m = SystemRenameMigration(settings, listOf(states, saves), "neogeo", "arcade")
        assertTrue(m.run() > 0)
        assertEquals(
            mapOf("g|video.shader" to "crt", "s|arcade|console.neogeo_mode" to "aes", "j|arcade/mslug|emu.rewind" to "true", "s|snes|video.shader" to "lcd"),
            settings.current().raw,
        )
        assertEquals("1", File(states, "arcade/mslug/slot_1.state").readText())
        assertEquals("4", File(states, "arcade/sf2/slot_1.state").readText())
        assertEquals("2", File(states, "p2/arcade/kof98/slot_0.state").readText())
        assertEquals("3", File(saves, "arcade/mslug.srm").readText())
        assertFalse(File(states, "neogeo").exists())
        // Rodar de novo não muda nada.
        assertEquals(0, m.run())
    }
}
