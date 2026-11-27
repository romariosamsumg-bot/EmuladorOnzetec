package com.retroplay.domain.settings

import com.retroplay.domain.settings.SettingDef.Bool
import com.retroplay.domain.settings.SettingDef.Choice
import com.retroplay.domain.settings.SettingDef.Text

/**
 * Todas as configurações do app. scoped = true aceita valor por console e por jogo.
 * Os textos (nome e descrição) ficam na UI, em strings.xml.
 */
object AppSettings {
    // ---- Vídeo
    val SHADER = Choice("video.shader", "none", listOf("none", "crt", "lcd", "sharp", "smooth"), scoped = true)
    val ASPECT = Choice("video.aspect", "original", listOf("original", "pixel"), scoped = true)
    val SHOW_FPS = Bool("video.fps", false)
    val LOW_POWER = Bool("video.low_power", false)
    /** Highlights: grava continuamente os últimos 15 s do jogo (vídeo) para salvar pelo menu. */
    val HIGHLIGHTS = Bool("video.highlights", true)

    // ---- Áudio
    val AUDIO_ENABLED = Bool("audio.enabled", true, scoped = true)
    val AUDIO_LATENCY = Choice("audio.latency", "low", listOf("low", "normal"))
    val MUTE_IN_MENU = Bool("audio.mute_menu", true)

    // ---- Controles
    /** Mapeamento "tecla Android=BOTÃO_RETROPAD,..." (vazio = padrão posicional). */
    val MAPPING = Text("controls.mapping", "", scoped = true)
    /** Botões do RetroPad com turbo, separados por vírgula. */
    val TURBO = Text("controls.turbo", "", scoped = true)
    val REMOTE_AS_GAMEPAD = Bool("controls.remote", true)
    val ANALOG_TO_DPAD = Bool("controls.analog_dpad", true)
    val DEADZONE = Choice("controls.deadzone", "50", listOf("10", "20", "30", "40", "50", "60", "70"))
    val NINTENDO_LAYOUT = Bool("controls.nintendo_layout", false)
    val HOTKEYS = Bool("controls.hotkeys", true)
    val HOTKEY_MODIFIER = Choice("controls.hotkey_modifier", "select", listOf("select", "l3"))
    /** Botões do controle que abrem o menu do jogo juntos (em qualquer ordem). */
    val MENU_COMBO = Choice("controls.menu_combo", "start_select", listOf("start_select", "l3_r3", "l1_r1_start", "off"))
    /** Controle na tela (celular): automático (some com controle físico), sempre ou nunca. */
    val TOUCH_PAD = Choice("controls.touch_pad", "auto", listOf("auto", "always", "never"))
    val TOUCH_PAD_OPACITY = Choice("controls.touch_opacity", "60", listOf("30", "45", "60", "80", "100"))
    val TOUCH_HAPTICS = Bool("controls.touch_haptics", true)
    /** Controle na tela: "classic" (parecido com o original do console) ou "minimal" (translúcido). */
    val TOUCH_STYLE = Choice("controls.touch_style", "classic", listOf("classic", "minimal"))
    /** Tamanho dos botões na tela (%). */
    val TOUCH_SIZE = Choice("controls.touch_size", "100", listOf("80", "90", "100", "115", "130"))
    /** Posições personalizadas dos botões (por console): "A:12.0,-30.0;dpad:0.0,10.0". */
    val TOUCH_LAYOUT = Text("controls.touch_layout", "")

    /** Portas dos controles: "descritor=porta;..." */
    val PORTS = Text("controls.ports", "")

    // ---- Emulação
    val FF_SPEED = Choice("emu.ff_speed", "2", listOf("2", "3", "4", "6", "8"), scoped = true)
    val FF_MODE = Choice("emu.ff_mode", "toggle", listOf("toggle", "hold"))
    val REWIND = Bool("emu.rewind", false, scoped = true)
    val REWIND_MB = Choice("emu.rewind_mb", "32", listOf("16", "32", "64", "128"))
    val AUTO_SAVE = Bool("emu.auto_save", true, scoped = true)
    val AUTO_LOAD = Bool("emu.auto_load", false, scoped = true)
    val SLOTS = Choice("emu.slots", "9", listOf("3", "6", "9"))
    /** Núcleo escolhido (vazio = o padrão do console). */
    val CORE = Text("emu.core", "", scoped = true)

    // ---- Biblioteca
    val SCAN_ON_START = Bool("library.scan_on_start", true)
    val HIDE_UNIDENTIFIED = Bool("library.hide_unidentified", false)

    // ---- Núcleos
    val CORE_CHANNEL = Choice("cores.channel", "stable", listOf("stable", "nightly"))
    val CORE_LAST_CHECK = Text("cores.last_check", "0")

    // ---- Interface
    val LANGUAGE = Choice("ui.language", "system", listOf("system", "pt-BR", "en", "es"))
    val THEME = Choice("ui.theme", "dark", listOf("dark", "oled"))
    val ACCENT = Choice("ui.accent", "LARANJA", listOf("LARANJA", "VIOLETA", "CIANO", "AMBAR", "VERDE", "CORAL"))
    val ANIMATIONS = Choice("ui.animations", "COMPLETAS", listOf("COMPLETAS", "REDUZIDAS", "DESLIGADAS"))
    val HERO_ANIMATED = Bool("ui.hero_animated", true)
    /** Sons da interface (navegar, selecionar, voltar...). */
    val NAV_SOUND = Bool("ui.sounds", true)
    val UI_VOLUME = Choice("ui.sounds_volume", "50", listOf("25", "50", "75", "100"))
    /** Música de fundo nos menus. */
    val MUSIC = Bool("ui.music", true)
    val MUSIC_VOLUME = Choice("ui.music_volume", "20", listOf("10", "20", "35", "50", "75"))
    /** Vibração do celular e tremor do controle nas interações. */
    val HAPTICS = Bool("ui.haptics", true)
    val CARD_SIZE = Choice("ui.card_size", "medium", listOf("small", "medium", "large"))
    /** Fileiras da Home ocultas (chaves separadas por vírgula). */
    val HIDDEN_ROWS = Text("ui.hidden_rows", "")
    /** Ordem das fileiras da Home (chaves de grupo separadas por vírgula). */
    val ROW_ORDER = Text("ui.row_order", HomeRowGroups.DEFAULT_ORDER.joinToString(","))

    // ---- Rede
    val TRANSFER_PORT = Choice("net.port", "8080", listOf("8080", "8081", "8888", "9000"))
    val TRANSFER_FIXED_PIN = Text("net.fixed_pin", "")
    val COVERS_DOWNLOAD = Bool("net.covers", true)
    val COVERS_WIFI_ONLY = Bool("net.covers_wifi_only", false)

    // ---- Perfis
    val ACTIVE_PROFILE = Text("profile.active", "1")
    val PROFILE_ASK = Bool("profile.ask", true)

    // ---- Controle parental
    val PARENTAL_PIN = Text("parental.pin", "")
    val PARENTAL_SETTINGS = Bool("parental.settings", false)
    val PARENTAL_DELETE = Bool("parental.delete", false)

    /** Prefixo das opções de núcleo: "coreopt.<núcleo>.<chave libretro>". */
    fun coreOptionKey(coreId: String, optionKey: String) = "coreopt.$coreId.$optionKey"
    fun coreOptionPrefix(coreId: String) = "coreopt.$coreId."
}

/** Grupos de fileiras da Home que o usuário pode ocultar/reordenar. */
object HomeRowGroups {
    const val CONTINUE = "continue"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"
    const val SYSTEMS = "systems"
    const val MOST_PLAYED = "most_played"
    const val POPULAR = "popular"
    const val GENRES = "genres"
    val DEFAULT_ORDER = listOf(CONTINUE, FAVORITES, POPULAR, RECENT, SYSTEMS, MOST_PLAYED, GENRES)
}

/** Opção de núcleo libretro (formato legado "Descrição; padrão|valor2|valor3"). */
data class CoreOption(val key: String, val description: String, val values: List<String>, val current: String) {
    val default: String get() = values.firstOrNull() ?: current

    companion object {
        fun parse(key: String, legacy: String, current: String?): CoreOption {
            val desc = legacy.substringBefore(';').trim()
            val values = legacy.substringAfter(';', "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
            return CoreOption(key, desc.ifEmpty { key }, values, current?.takeIf { it.isNotEmpty() } ?: values.firstOrNull().orEmpty())
        }
    }
}
