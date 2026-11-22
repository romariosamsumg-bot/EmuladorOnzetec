package com.retroplay.feature.cores

import com.retroplay.diagnostics.CoverPolicyInterceptor
import com.retroplay.diagnostics.ErrorLog
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.core.InstalledCore
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.InMemorySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoreAutoUpdaterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Cores(private val installed: Set<String>, private val outdated: Set<String>) : CoreRepository {
        val installs = mutableListOf<String>()
        override fun observe(core: CoreDescriptor): Flow<CoreInstallState> = flowOf(CoreInstallState.NotInstalled)
        override fun install(core: CoreDescriptor) { installs += core.id }
        override suspend fun installed(core: CoreDescriptor) =
            if (core.id in installed) InstalledCore(core.id, "arm64-v8a", "/x", "1", "h", 1, 0) else null
        override suspend fun remove(core: CoreDescriptor) = Unit
        override suspend fun checkForUpdate(core: CoreDescriptor): Boolean? = core.id in outdated
    }

    @Test
    fun canal_estavel_nunca_atualiza_sozinho() = runBlocking {
        val cores = Cores(setOf("snes9x2010"), setOf("snes9x2010"))
        val updater = CoreAutoUpdater(cores, SystemRegistry, InMemorySettings())
        assertEquals(emptyList<String>(), updater.runIfDue())
        assertTrue(cores.installs.isEmpty())
    }

    @Test
    fun nightly_atualiza_so_os_instalados_desatualizados_uma_vez_por_dia() = runBlocking {
        val settings = InMemorySettings().apply { set(AppSettings.CORE_CHANNEL, ScopeRef.Global, "nightly") }
        val cores = Cores(installed = setOf("snes9x2010", "genesis_plus_gx"), outdated = setOf("snes9x2010", "fbneo"))
        val updater = CoreAutoUpdater(cores, SystemRegistry, settings)
        assertEquals(listOf("snes9x2010"), updater.runIfDue(now = CoreAutoUpdater.DAY_MS * 10))
        // Menos de 24 h depois: não verifica de novo.
        assertEquals(emptyList<String>(), updater.runIfDue(now = CoreAutoUpdater.DAY_MS * 10 + 1000))
        assertEquals(listOf("snes9x2010"), updater.runIfDue(now = CoreAutoUpdater.DAY_MS * 11 + 1))
        assertEquals(listOf("snes9x2010", "snes9x2010"), cores.installs)
    }

    @Test
    fun registro_de_erros_guarda_so_o_final() {
        val f = tmp.newFile("errors.txt")
        f.writeText((1..2000).joinToString("\n") { "linha $it ".repeat(4) })
        ErrorLog.trim(f, max = 4096)
        val text = f.readText()
        assertTrue(f.length() <= 4096)
        assertTrue(text.startsWith("linha"))
        assertTrue(text.trimEnd().endsWith("linha 2000"))
    }

    @Test
    fun politica_de_capas() {
        var enabled = true
        var wifiOnly = false
        var metered = true
        val p = CoverPolicyInterceptor({ enabled }, { wifiOnly }, { metered })
        assertTrue(p.allowed())
        wifiOnly = true
        assertFalse(p.allowed())
        metered = false
        assertTrue(p.allowed())
        enabled = false
        assertFalse(p.allowed())
    }
}
