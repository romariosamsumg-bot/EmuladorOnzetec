package com.retroplay.domain.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRegistryTest {

    @Test
    fun `registro contem exatamente os 5 consoles suportados`() {
        assertEquals(
            listOf("snes", "megadrive", "mastersystem", "arcade", "psx"),
            SystemRegistry.all.map { it.id.value },
        )
    }

    @Test
    fun `ids sao unicos`() {
        val ids = SystemRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `nucleos padrao conforme especificacao`() {
        assertEquals("snes9x2010", SystemRegistry.SNES.defaultCore.id)
        assertEquals("genesis_plus_gx", SystemRegistry.MEGA_DRIVE.defaultCore.id)
        assertEquals("genesis_plus_gx", SystemRegistry.MASTER_SYSTEM.defaultCore.id)
        assertEquals("mame2000", SystemRegistry.ARCADE.defaultCore.id)
        assertEquals(listOf("mame2000", "fbneo", "mame2003_plus"), SystemRegistry.ARCADE.cores.map { it.id })
        assertEquals("snes9x_libretro_android.so", SystemRegistry.SNES.cores[1].libraryFileName)
    }

    @Test
    fun `mega drive e master system compartilham o mesmo nucleo mas sao sistemas distintos`() {
        assertEquals(SystemRegistry.MEGA_DRIVE.defaultCore, SystemRegistry.MASTER_SYSTEM.defaultCore)
        assertTrue(SystemRegistry.MEGA_DRIVE.id != SystemRegistry.MASTER_SYSTEM.id)
    }

    @Test
    fun `apenas o neo geo exige bios`() {
        // A BIOS do Neo Geo é exigida jogo a jogo (só os de Neo Geo), não pelo Arcade inteiro.
        assertEquals(emptyList<GameSystem>(), SystemRegistry.all.filter { it.requiresBios })
        assertEquals("neogeo.zip", SystemRegistry.ARCADE.bios.single().fileName)
        assertEquals(IdentificationStrategy.ARCADE_ROMSET, SystemRegistry.ARCADE.identification)
    }

    @Test
    fun `neo geo tem botoes A B C D Ficha e Start com Ficha em destaque`() {
        val layout = SystemRegistry.ARCADE.controllerLayout
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "Ficha", "Start"), layout.buttons.map { it.label })
        val coin = layout.buttonFor(RetroPadButton.SELECT)
        assertNotNull(coin)
        assertTrue(coin!!.highlighted)
    }

    @Test
    fun `master system mapeia pause no start`() {
        assertEquals("Pause", SystemRegistry.MASTER_SYSTEM.controllerLayout.buttonFor(RetroPadButton.START)?.label)
    }

    @Test
    fun `byId encontra e retorna null para desconhecido`() {
        assertEquals(SystemRegistry.SNES, SystemRegistry.byId(SystemId("snes")))
        assertEquals(null, SystemRegistry.byId(SystemId("n64")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `layout com botao do retropad repetido e rejeitado`() {
        ControllerLayout(listOf(ConsoleButton("1", RetroPadButton.A), ConsoleButton("2", RetroPadButton.A)))
    }

    @Test
    fun `extensoes sao normalizadas`() {
        SystemRegistry.all.forEach { system ->
            assertFalse(system.extensions.any { it.startsWith(".") || it != it.lowercase() })
        }
    }
}
