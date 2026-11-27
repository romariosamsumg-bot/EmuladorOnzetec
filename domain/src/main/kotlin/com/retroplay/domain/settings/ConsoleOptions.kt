package com.retroplay.domain.settings

import com.retroplay.domain.settings.SettingDef.Bool
import com.retroplay.domain.settings.SettingDef.Choice
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry

/**
 * Opções próprias de cada console, com nomes amigáveis. Valem por console e por jogo e são
 * traduzidas para as variáveis do núcleo em uso por [ConsoleCoreOptions].
 */
object ConsoleSettings {
    /** Neo Geo: MVS (arcade), AES (console doméstico) ou UniBIOS (se estiver na neogeo.zip). */
    val NEOGEO_MODE = Choice("console.neogeo_mode", "mvs", listOf("mvs", "aes", "unibios"), scoped = true)

    /** Neo Geo: jogar sem fichas (DIP "Free play" do MVS). */
    val NEOGEO_FREEPLAY = Bool("console.neogeo_freeplay", false, scoped = true)

    /** Mega Drive: controle de 3 ou 6 botões. */
    val MD_PAD = Choice("console.md_pad", "6", listOf("3", "6"), scoped = true)

    /** Mega Drive: região do console. */
    val MD_REGION = Choice("console.md_region", "auto", listOf("auto", "ntsc-u", "ntsc-j", "pal"), scoped = true)

    /** Master System: som FM (YM2413). */
    val SMS_FM = Bool("console.sms_fm", true, scoped = true)

    /** Super Nintendo: reduzir lentidão (overclock da CPU e do SuperFX). */
    val SNES_REDUCE_SLOWDOWN = Bool("console.snes_slowdown", false, scoped = true)

    fun forSystem(system: SystemId): List<SettingDef<*>> = when (system) {
        SystemRegistry.Ids.ARCADE -> listOf(NEOGEO_MODE, NEOGEO_FREEPLAY)
        SystemRegistry.Ids.MEGA_DRIVE -> listOf(MD_PAD, MD_REGION)
        SystemRegistry.Ids.MASTER_SYSTEM -> listOf(SMS_FM)
        SystemRegistry.Ids.SNES -> listOf(SNES_REDUCE_SLOWDOWN)
        else -> emptyList()
    }
}

/** Tipo de controle pedido ao núcleo (retro_set_controller_port_device). */
enum class PadType { MD_3_BUTTON, MD_6_BUTTON }

/**
 * O que o núcleo recebe ao abrir o jogo: variáveis libretro e, se for o caso, o tipo de controle.
 * As opções de núcleo editadas à mão nas Configurações são aplicadas depois e prevalecem.
 */
data class CoreSetup(val variables: Map<String, String>, val padType: PadType? = null)

object ConsoleCoreOptions {

    /**
     * @param romset nome do romset de arcade (ex.: "kof98"); o free play do FBNeo é um DIP
     *  com o nome do romset na chave.
     */
    fun setup(system: SystemId, coreId: String, snap: SettingsSnapshot, gameKey: String?, romset: String? = null): CoreSetup {
        fun <T : Any> v(def: SettingDef<T>) = snap.value(def, system, gameKey)
        val vars = LinkedHashMap<String, String>()
        var pad: PadType? = null
        when (system) {
            SystemRegistry.Ids.ARCADE -> when (coreId) {
                "fbneo" -> {
                    vars["fbneo-neogeo-mode"] = when (v(ConsoleSettings.NEOGEO_MODE)) {
                        "aes" -> "AES_EUR"
                        "unibios" -> "UNIBIOS"
                        else -> "MVS_EUR"
                    }
                    if (romset != null) vars[freePlayKey(romset)] = if (v(ConsoleSettings.NEOGEO_FREEPLAY)) "On" else "Off"
                }
                "mame2000" -> {
                    // Pula o aviso de abertura; no modo econômico, pula quadros quando a TV não dá conta.
                    vars["mame2000-skip_disclaimer"] = "enabled"
                    vars["mame2000-frameskip"] = if (snap.value(AppSettings.LOW_POWER)) "auto" else "disabled"
                }
            }
            SystemRegistry.Ids.MEGA_DRIVE -> {
                val six = v(ConsoleSettings.MD_PAD) == "6"
                val region = v(ConsoleSettings.MD_REGION)
                when (coreId) {
                    "genesis_plus_gx" -> {
                        vars["genesis_plus_gx_region_detect"] = region
                        pad = if (six) PadType.MD_6_BUTTON else PadType.MD_3_BUTTON
                    }
                    "picodrive" -> {
                        vars["picodrive_region"] = when (region) {
                            "ntsc-u" -> "US"
                            "ntsc-j" -> "Japan NTSC"
                            "pal" -> "Europe"
                            else -> "Auto"
                        }
                        val type = if (six) "6 button pad" else "3 button pad"
                        vars["picodrive_input1"] = type
                        vars["picodrive_input2"] = type
                    }
                }
            }
            SystemRegistry.Ids.MASTER_SYSTEM -> {
                val fm = v(ConsoleSettings.SMS_FM)
                when (coreId) {
                    "genesis_plus_gx" -> vars["genesis_plus_gx_ym2413"] = if (fm) "enabled" else "disabled"
                    "picodrive" -> vars["picodrive_smsfm"] = if (fm) "on" else "off"
                }
            }
            SystemRegistry.Ids.SNES -> {
                val reduce = v(ConsoleSettings.SNES_REDUCE_SLOWDOWN)
                when (coreId) {
                    "snes9x2010" -> {
                        vars["snes9x_2010_overclock_cycles"] = if (reduce) "compatible" else "disabled"
                        vars["snes9x_2010_overclock"] = if (reduce) "20 MHz" else "10 MHz (Default)"
                    }
                    "snes9x" -> {
                        vars["snes9x_overclock_cycles"] = if (reduce) "compatible" else "disabled"
                        vars["snes9x_overclock_superfx"] = if (reduce) "200%" else "100%"
                    }
                }
            }
            // Game Boy: o Gambatte colore os jogos que têm paleta oficial do Super Game Boy/GBC.
            SystemRegistry.Ids.GAME_BOY -> if (coreId == "gambatte") vars["gambatte_gb_colorization"] = "auto"
            SystemRegistry.Ids.PLAYSTATION -> if (coreId == "pcsx_rearmed") {
                // Recompilador dinâmico ligado (o que deixa o núcleo leve em ARM) e sem o logo da BIOS.
                vars["pcsx_rearmed_drc"] = "enabled"
                vars["pcsx_rearmed_show_bios_bootlogo"] = "disabled"
                // Modo econômico: pula quadros quando a TV/celular não dá conta (o som não engasga).
                vars["pcsx_rearmed_frameskip_type"] = if (snap.value(AppSettings.LOW_POWER)) "auto" else "disabled"
            }
        }
        return CoreSetup(vars, pad)
    }

    /** Chave do DIP "Free play" criada pelo FBNeo: "fbneo-dipswitch-<romset>-Free_play". */
    fun freePlayKey(romset: String) = "fbneo-dipswitch-$romset-Free_play"

    /** Descrição do tipo de controle no genesis_plus_gx (SET_CONTROLLER_INFO) e o id libretro. */
    fun padDescription(type: PadType) = when (type) {
        PadType.MD_3_BUTTON -> "MD Joypad 3 Button"
        PadType.MD_6_BUTTON -> "MD Joypad 6 Button"
    }

    /** RETRO_DEVICE_SUBCLASS(JOYPAD, n): 3 botões = 257, 6 botões = 513 (conferido no núcleo). */
    fun padFallbackId(type: PadType) = when (type) {
        PadType.MD_3_BUTTON -> 257
        PadType.MD_6_BUTTON -> 513
    }
}
