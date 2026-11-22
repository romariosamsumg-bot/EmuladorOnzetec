package com.retroplay.domain.settings

import com.retroplay.domain.system.SystemRegistry.Ids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As chaves e valores foram conferidos nos núcleos reais (tools/cores/list_core_options.py e fonte do FBNeo). */
class ConsoleOptionsTest {

    private fun snap(vararg kv: Pair<String, String>) = SettingsSnapshot(kv.toMap())

    @Test
    fun neo_geo_modo_e_free_play_por_jogo() {
        val empty = ConsoleCoreOptions.setup(Ids.NEO_GEO, "fbneo", snap(), "neogeo/kof98", "kof98")
        assertEquals("MVS_EUR", empty.variables["fbneo-neogeo-mode"])
        assertEquals("Off", empty.variables["fbneo-dipswitch-kof98-Free_play"])

        val s = snap(
            "s|neogeo|console.neogeo_mode" to "aes",
            "j|neogeo/kof98|console.neogeo_mode" to "unibios",
            "j|neogeo/kof98|console.neogeo_freeplay" to "true",
        )
        val kof = ConsoleCoreOptions.setup(Ids.NEO_GEO, "fbneo", s, "neogeo/kof98", "kof98")
        assertEquals("UNIBIOS", kof.variables["fbneo-neogeo-mode"])
        assertEquals("On", kof.variables["fbneo-dipswitch-kof98-Free_play"])
        val mslug = ConsoleCoreOptions.setup(Ids.NEO_GEO, "fbneo", s, "neogeo/mslug", "mslug")
        assertEquals("AES_EUR", mslug.variables["fbneo-neogeo-mode"])
        assertEquals("Off", mslug.variables["fbneo-dipswitch-mslug-Free_play"])
    }

    @Test
    fun mega_drive_seis_botoes_e_regiao_nos_dois_nucleos() {
        val s = snap("s|megadrive|console.md_pad" to "3", "s|megadrive|console.md_region" to "ntsc-j")
        val gpgx = ConsoleCoreOptions.setup(Ids.MEGA_DRIVE, "genesis_plus_gx", s, null)
        assertEquals(PadType.MD_3_BUTTON, gpgx.padType)
        assertEquals("ntsc-j", gpgx.variables["genesis_plus_gx_region_detect"])
        val pico = ConsoleCoreOptions.setup(Ids.MEGA_DRIVE, "picodrive", s, null)
        assertNull(pico.padType)
        assertEquals("3 button pad", pico.variables["picodrive_input1"])
        assertEquals("Japan NTSC", pico.variables["picodrive_region"])
        // Padrão: 6 botões, região automática.
        val def = ConsoleCoreOptions.setup(Ids.MEGA_DRIVE, "genesis_plus_gx", snap(), null)
        assertEquals(PadType.MD_6_BUTTON, def.padType)
        assertEquals("auto", def.variables["genesis_plus_gx_region_detect"])
        assertEquals(513, ConsoleCoreOptions.padFallbackId(PadType.MD_6_BUTTON))
    }

    @Test
    fun master_system_fm_e_snes_slowdown() {
        assertEquals("enabled", ConsoleCoreOptions.setup(Ids.MASTER_SYSTEM, "genesis_plus_gx", snap(), null).variables["genesis_plus_gx_ym2413"])
        val off = snap("s|mastersystem|console.sms_fm" to "false")
        assertEquals("disabled", ConsoleCoreOptions.setup(Ids.MASTER_SYSTEM, "genesis_plus_gx", off, null).variables["genesis_plus_gx_ym2413"])
        assertEquals("off", ConsoleCoreOptions.setup(Ids.MASTER_SYSTEM, "picodrive", off, null).variables["picodrive_smsfm"])
        // Master System não recebe opções de Mega Drive mesmo usando o mesmo núcleo.
        assertTrue(ConsoleCoreOptions.setup(Ids.MASTER_SYSTEM, "genesis_plus_gx", snap(), null).variables.keys.none { it.contains("region") })

        val on = snap("g|console.snes_slowdown" to "true")
        assertEquals("compatible", ConsoleCoreOptions.setup(Ids.SNES, "snes9x2010", on, null).variables["snes9x_2010_overclock_cycles"])
        assertEquals("200%", ConsoleCoreOptions.setup(Ids.SNES, "snes9x", on, null).variables["snes9x_overclock_superfx"])
        assertEquals("disabled", ConsoleCoreOptions.setup(Ids.SNES, "snes9x", snap(), null).variables["snes9x_overclock_cycles"])
    }

    @Test
    fun opcoes_listadas_por_console() {
        assertEquals(listOf(ConsoleSettings.NEOGEO_MODE, ConsoleSettings.NEOGEO_FREEPLAY), ConsoleSettings.forSystem(Ids.NEO_GEO))
        assertEquals(listOf(ConsoleSettings.SMS_FM), ConsoleSettings.forSystem(Ids.MASTER_SYSTEM))
    }
}
