package com.retroplay.feature.settings

import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import android.app.ActivityManager
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.text.format.Formatter
import android.view.InputDevice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.R
import com.retroplay.core.common.AbiDetector
import com.retroplay.diagnostics.ErrorLog
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.library.Game
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ConsoleSettings
import com.retroplay.domain.settings.HomeRowGroups
import com.retroplay.domain.settings.Scope
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingDef
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.domain.system.SystemId
import com.retroplay.feature.game.input.GamepadProfile
import com.retroplay.feature.game.input.MappingCodec
import com.retroplay.ui.components.rememberInitialFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Conteúdo do painel direito. Cada linha mostra nome, descrição e valor resolvido
 * (jogo → console → global → padrão) com o indicador de herança.
 */
class SettingsPane(
    val snap: SettingsSnapshot,
    val scope: ScopeRef,
    val system: SystemId?,
    val gameKey: String?,
    val vm: SettingsViewModel,
    val openDialog: (@Composable () -> Unit) -> Unit,
    val closeDialog: () -> Unit,
    val onOpenTransfer: () -> Unit,
) {
    private val inGlobal get() = scope.scope == Scope.GLOBAL
    private val currentSystem: GameSystem? get() = system?.let { vm.catalog.byId(it) }

    fun LazyListScope.content(category: SettingsCategory) {
        when (category) {
            SettingsCategory.VIDEO -> video()
            SettingsCategory.AUDIO -> audio()
            SettingsCategory.CONTROLS -> controls()
            SettingsCategory.EMULATION -> emulation()
            SettingsCategory.BIOS -> bios()
            SettingsCategory.LIBRARY -> library()
            SettingsCategory.CORES -> cores()
            SettingsCategory.INTERFACE -> ui()
            SettingsCategory.NETWORK -> network()
            SettingsCategory.STORAGE -> storage()
            SettingsCategory.PARENTAL -> parental()
            SettingsCategory.ABOUT -> about()
        }
    }

    // ------------------------------------------------------------------ categorias

    private fun LazyListScope.video() {
        choice(AppSettings.SHADER, R.string.set_shader, R.string.set_shader_desc)
        choice(AppSettings.ASPECT, R.string.set_aspect, R.string.set_aspect_desc)
        bool(AppSettings.SHOW_FPS, R.string.set_fps, R.string.set_fps_desc)
        bool(AppSettings.LOW_POWER, R.string.set_low_power, R.string.set_low_power_desc)
        if (inGlobal) bool(AppSettings.HIGHLIGHTS, R.string.set_highlights, R.string.set_highlights_desc)
        scopeNote()
        noteRes(R.string.video_note, "video")
    }

    private fun LazyListScope.audio() {
        bool(AppSettings.AUDIO_ENABLED, R.string.set_audio, R.string.set_audio_desc)
        choice(AppSettings.AUDIO_LATENCY, R.string.set_latency, R.string.set_latency_desc)
        bool(AppSettings.MUTE_IN_MENU, R.string.set_mute_menu, R.string.set_mute_menu_desc)
        scopeNote()
        noteRes(R.string.audio_note, "audio")
    }

    private fun LazyListScope.controls() {
        if (inGlobal) {
            item(key = "gamepads_h") { Header(stringResource(R.string.controls_connected)) }
            item(key = "gamepads") { Gamepads() }
        }
        item(key = "remap") { RemapRow() }
        item(key = "turbo") { TurboRow() }
        choice(AppSettings.TOUCH_PAD, R.string.set_touch_pad, R.string.set_touch_pad_desc)
        choice(AppSettings.TOUCH_PAD_OPACITY, R.string.set_touch_opacity, R.string.set_touch_opacity_desc)
        if (inGlobal) {
            choice(AppSettings.TOUCH_STYLE, R.string.set_touch_style, R.string.set_touch_style_desc)
            choice(AppSettings.TOUCH_SIZE, R.string.set_touch_size, R.string.set_touch_size_desc)
        }
        item(key = "touch_layout") {
            SettingRow(stringResource(R.string.set_touch_layout), stringResource(R.string.set_touch_layout_desc), null, "touch_layout") {
                val sys = system
                if (sys != null) openDialog { PadEditorHost(sys) } else openDialog { PadConsolePicker() }
            }
        }
        bool(AppSettings.TOUCH_HAPTICS, R.string.set_touch_haptics, R.string.set_touch_haptics_desc)
        bool(AppSettings.REMOTE_AS_GAMEPAD, R.string.set_remote, R.string.set_remote_desc)
        bool(AppSettings.ANALOG_TO_DPAD, R.string.set_analog, R.string.set_analog_desc)
        choice(AppSettings.DEADZONE, R.string.set_deadzone, R.string.set_deadzone_desc)
        bool(AppSettings.NINTENDO_LAYOUT, R.string.set_nintendo, R.string.set_nintendo_desc)
        choice(AppSettings.MENU_COMBO, R.string.set_menu_combo, R.string.set_menu_combo_desc)
        bool(AppSettings.HOTKEYS, R.string.set_hotkeys, R.string.set_hotkeys_desc)
        choice(AppSettings.HOTKEY_MODIFIER, R.string.set_modifier, R.string.set_modifier_desc)
        scopeNote()
        noteRes(R.string.hotkeys_list, "hotkeys")
        when (system) {
            com.retroplay.domain.system.SystemRegistry.Ids.MASTER_SYSTEM -> noteRes(R.string.sms_pause_note, "sms_pause")
            com.retroplay.domain.system.SystemRegistry.Ids.ARCADE -> noteRes(R.string.neogeo_coin_note, "neo_coin")
            else -> Unit
        }
        if (inGlobal) noteRes(R.string.multiplayer_note, "multiplayer")
    }

    private fun LazyListScope.emulation() {
        choice(AppSettings.FF_SPEED, R.string.set_ff_speed, R.string.set_ff_speed_desc)
        choice(AppSettings.FF_MODE, R.string.set_ff_mode, R.string.set_ff_mode_desc)
        bool(AppSettings.REWIND, R.string.set_rewind, R.string.set_rewind_desc)
        if (inGlobal) item(key = AppSettings.REWIND_MB.key) {
            val ram = rememberTotalRam()
            ChoiceRow(AppSettings.REWIND_MB, R.string.set_rewind_mb, stringResource(R.string.set_rewind_mb_desc, ram))
        }
        if (inGlobal && snap.value(AppSettings.REWIND_MB).toInt() > 32) noteRes(R.string.rewind_ram_warning, "rewind_ram")
        bool(AppSettings.AUTO_SAVE, R.string.set_auto_save, R.string.set_auto_save_desc)
        bool(AppSettings.AUTO_LOAD, R.string.set_auto_load, R.string.set_auto_load_desc)
        choice(AppSettings.SLOTS, R.string.set_slots, R.string.set_slots_desc)
        // Núcleo por console (no Global aparece um por console; nos outros níveis, o do console).
        val systems = if (inGlobal) vm.catalog.all else listOfNotNull(currentSystem)
        systems.filter { it.cores.size > 1 }.forEach { sys -> item(key = "core_${sys.id.value}") { CoreChoiceRow(sys) } }
        coreOptionCores().forEach { core -> item(key = "coreopt_${core.id}") { CoreOptionsRow(core) } }
        consoleOptions()
        scopeNote()
        noteRes(R.string.emulation_note, "emulation")
    }

    /** Opções próprias do console (modo do Neo Geo, 3/6 botões, região, FM, lentidão). */
    private fun LazyListScope.consoleOptions() {
        item(key = "console_h") { Header(stringResource(R.string.console_options)) }
        val sys = system
        if (sys == null) {
            noteRes(R.string.console_options_global_note, "console_global")
            return
        }
        ConsoleSettings.forSystem(sys).forEach { def ->
            when (def) {
                ConsoleSettings.NEOGEO_MODE -> item(key = def.key) {
                    val uni by vm.unibios.collectAsStateWithLifecycle()
                    val missing = stringResource(R.string.neogeo_mode_unibios_missing)
                    ChoiceRow(ConsoleSettings.NEOGEO_MODE, R.string.set_neogeo_mode, stringResource(R.string.set_neogeo_mode_desc), if (uni == false) mapOf("unibios" to missing) else emptyMap())
                }
                ConsoleSettings.NEOGEO_FREEPLAY -> bool(ConsoleSettings.NEOGEO_FREEPLAY, R.string.set_neogeo_freeplay, R.string.set_neogeo_freeplay_desc)
                ConsoleSettings.MD_PAD -> choice(ConsoleSettings.MD_PAD, R.string.set_md_pad, R.string.set_md_pad_desc)
                ConsoleSettings.MD_REGION -> choice(ConsoleSettings.MD_REGION, R.string.set_md_region, R.string.set_md_region_desc)
                ConsoleSettings.SMS_FM -> bool(ConsoleSettings.SMS_FM, R.string.set_sms_fm, R.string.set_sms_fm_desc)
                ConsoleSettings.SNES_REDUCE_SLOWDOWN -> bool(ConsoleSettings.SNES_REDUCE_SLOWDOWN, R.string.set_snes_slowdown, R.string.set_snes_slowdown_desc)
                else -> Unit
            }
        }
    }

    private fun LazyListScope.bios() {
        item(key = "bios_status") {
            val bios by vm.bios.collectAsStateWithLifecycle()
            val status = bios.filter { it.setName == "neogeo" }.maxByOrNull { if (it.completeForAll) 2 else if (it.complete) 1 else 0 }
            val first = rememberInitialFocus(false)
            SettingRow(
                title = stringResource(R.string.bios_header),
                description = status?.uri?.let(::readablePath) ?: stringResource(R.string.bios_missing_desc),
                value = stringResource(
                    when {
                        status == null -> R.string.bios_missing
                        status.completeForAll -> R.string.bios_ok
                        status.complete -> R.string.bios_old_version
                        else -> R.string.bios_incomplete
                    },
                ),
                tag = "bios_status",
                modifier = Modifier.focusRequester(first),
            ) { vm.scanNow() }
        }
        item(key = "bios_psx") {
            val bios by vm.bios.collectAsStateWithLifecycle()
            val status = bios.filter { it.system == com.retroplay.domain.system.SystemRegistry.Ids.PLAYSTATION }.maxByOrNull { it.complete }
            SettingRow(
                title = stringResource(R.string.bios_psx_header),
                description = status?.uri?.let(::readablePath) ?: stringResource(R.string.bios_psx_missing_desc),
                value = stringResource(
                    when {
                        status == null -> R.string.bios_missing
                        status.complete -> R.string.bios_ok
                        else -> R.string.bios_incomplete
                    },
                ),
                tag = "bios_psx",
            ) { vm.scanNow() }
        }
        item(key = "bios_import") {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBios) }
            SettingRow(stringResource(R.string.bios_import), stringResource(R.string.bios_import_desc), null, "bios_import") {
                runCatching { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                    .onFailure { vm.message(R.string.import_failed) }
            }
        }
        noteRes(R.string.bios_modes_note, "bios_modes")
    }

    private fun LazyListScope.library() {
        item(key = "folders") {
            val count by vm.folderCount.collectAsStateWithLifecycle()
            SettingRow(stringResource(R.string.set_folders), stringResource(R.string.set_folders_desc), pluralStringResource(R.plurals.folders_count, count, count), "lib_folders", onClick = onOpenTransfer)
        }
        item(key = "scan") {
            SettingRow(stringResource(R.string.set_scan_now), stringResource(R.string.set_scan_now_desc), null, "lib_scan") {
                vm.scanNow(); vm.message(R.string.scan_started)
            }
        }
        bool(AppSettings.SCAN_ON_START, R.string.set_scan_start, R.string.set_scan_start_desc)
        bool(AppSettings.HIDE_UNIDENTIFIED, R.string.set_hide_unident, R.string.set_hide_unident_desc)
        item(key = "dups") {
            val dups by vm.duplicates.collectAsStateWithLifecycle()
            SettingRow(
                stringResource(R.string.set_duplicates), stringResource(R.string.set_duplicates_desc),
                if (dups.isEmpty()) stringResource(R.string.duplicates_none) else pluralStringResource(R.plurals.duplicates_count, dups.size, dups.size), "lib_dups",
            ) { openDialog { DuplicatesHost() } }
        }
        item(key = "rebuild") {
            val title = stringResource(R.string.set_rebuild)
            val message = stringResource(R.string.rebuild_confirm)
            SettingRow(title, stringResource(R.string.set_rebuild_desc), null, "lib_rebuild") {
                openDialog { ConfirmDialog(title, message, onConfirm = { vm.rebuildLibrary(); closeDialog() }, onDismiss = closeDialog) }
            }
        }
        noteRes(R.string.library_note, "library")
    }

    private fun LazyListScope.cores() {
        choice(AppSettings.CORE_CHANNEL, R.string.set_channel, R.string.set_channel_desc)
        item(key = "check") { SettingRow(stringResource(R.string.set_check_updates), stringResource(R.string.set_check_updates_desc), null, "cores_check") { vm.checkUpdates() } }
        item(key = "all") { SettingRow(stringResource(R.string.set_download_all), stringResource(R.string.set_download_all_desc), null, "cores_all") { vm.installDefaultCores() } }
        item(key = "cores_h") { Header(stringResource(R.string.cat_cores)) }
        vm.allCores.forEach { core -> item(key = "core_${core.id}") { CoreRow(core) } }
    }

    private fun LazyListScope.ui() {
        item(key = "profiles") {
            val active by vm.activeProfile.collectAsStateWithLifecycle()
            SettingRow(stringResource(R.string.set_profiles), stringResource(R.string.set_profiles_desc), active?.name, "ui_profiles") {
                openDialog { ProfilesHost() }
            }
        }
        item(key = "edit_profile") {
            SettingRow(stringResource(R.string.profile_edit_row), stringResource(R.string.profile_edit_row_desc), null, "ui_edit_profile") {
                openDialog { ProfileEditorDialog(editActive = true) }
            }
        }
        bool(AppSettings.PROFILE_ASK, R.string.set_profile_ask, R.string.set_profile_ask_desc)
        choice(AppSettings.LANGUAGE, R.string.set_language, R.string.set_language_desc)
        choice(AppSettings.THEME, R.string.set_theme, R.string.set_theme_desc)
        choice(AppSettings.ANIMATIONS, R.string.set_animations, R.string.set_animations_desc)
        bool(AppSettings.HERO_ANIMATED, R.string.set_hero, R.string.set_hero_desc)
        bool(AppSettings.NAV_SOUND, R.string.set_nav_sound, R.string.set_nav_sound_desc)
        choice(AppSettings.CARD_SIZE, R.string.set_card_size, R.string.set_card_size_desc)
        item(key = "rows") {
            val order = parseRowOrder(snap.value(AppSettings.ROW_ORDER))
            val hidden = parseHidden(snap.value(AppSettings.HIDDEN_ROWS))
            SettingRow(
                stringResource(R.string.set_home_rows), stringResource(R.string.set_home_rows_desc),
                stringResource(R.string.home_rows_value, order.count { it !in hidden }, order.size), "ui_rows",
            ) {
                openDialog {
                    HomeRowsDialog(order, hidden, onChange = { o, h ->
                        vm.setAt(AppSettings.ROW_ORDER, ScopeRef.Global, o.joinToString(","))
                        vm.setAt(AppSettings.HIDDEN_ROWS, ScopeRef.Global, h.sortedBy { HomeRowGroups.DEFAULT_ORDER.indexOf(it) }.joinToString(","))
                    }, onDismiss = closeDialog)
                }
            }
        }
    }

    private fun LazyListScope.network() {
        choice(AppSettings.TRANSFER_PORT, R.string.set_port, R.string.set_port_desc)
        item(key = "fixed_pin") {
            val pin = snap.value(AppSettings.TRANSFER_FIXED_PIN)
            val title = stringResource(R.string.set_fixed_pin)
            val random = stringResource(R.string.pin_use_random)
            SettingRow(title, stringResource(R.string.set_fixed_pin_desc), pin.ifEmpty { stringResource(R.string.pin_random) }, "net_pin") {
                openDialog {
                    PinDialog(
                        title,
                        onSubmit = { vm.setAt(AppSettings.TRANSFER_FIXED_PIN, ScopeRef.Global, it); closeDialog(); true },
                        onDismiss = closeDialog,
                        extraAction = random to { vm.setAt(AppSettings.TRANSFER_FIXED_PIN, ScopeRef.Global, null); closeDialog() },
                    )
                }
            }
        }
        item(key = "smb") {
            SettingRow(stringResource(R.string.set_smb), stringResource(R.string.set_smb_desc), null, "net_smb") {
                openDialog { SmbDialog(onSubmit = { form -> vm.addSmbFolder(form) { ok -> if (ok) closeDialog() } }, onDismiss = closeDialog) }
            }
        }
        bool(AppSettings.COVERS_DOWNLOAD, R.string.set_covers, R.string.set_covers_desc)
        bool(AppSettings.COVERS_WIFI_ONLY, R.string.set_covers_wifi, R.string.set_covers_wifi_desc)
    }

    private fun LazyListScope.storage() {
        item(key = "usage") {
            val context = LocalContext.current
            val report by vm.storage.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshStorage() }
            val free by produceState(-1L) { value = withContext(Dispatchers.IO) { context.filesDir.usableSpace } }
            fun size(b: Long?) = if (b == null || b < 0) "…" else Formatter.formatShortFileSize(context, b)
            val desc = stringResource(R.string.storage_desc)
            androidx.compose.foundation.layout.Column {
                listOf(
                    R.string.storage_games to report?.games, R.string.storage_saves to report?.saves,
                    R.string.storage_covers to report?.covers, R.string.storage_cores to report?.cores,
                    R.string.storage_cache to report?.cache, R.string.storage_free to free,
                ).forEachIndexed { i, (label, bytes) ->
                    SettingRow(stringResource(label), if (i == 0) desc else null, size(bytes), "storage_$i") {}
                }
            }
        }
        item(key = "clear") { SettingRow(stringResource(R.string.set_clear_cache), stringResource(R.string.set_clear_cache_desc), null, "storage_clear") { vm.clearCache() } }
        item(key = "backup_h") { Header(stringResource(R.string.backup_header)) }
        item(key = "backup") {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::exportBackup) }
            SettingRow(stringResource(R.string.set_backup), stringResource(R.string.set_backup_desc), null, "backup_export") {
                runCatching { launcher.launch("retroplay-backup-${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}.zip") }
                    .onFailure { vm.message(R.string.backup_failed) }
            }
        }
        item(key = "restore") {
            val title = stringResource(R.string.set_restore)
            val confirm = stringResource(R.string.restore_confirm)
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restoreBackup) }
            SettingRow(title, stringResource(R.string.set_restore_desc), null, "backup_restore") {
                openDialog {
                    ConfirmDialog(title, confirm, onConfirm = {
                        closeDialog()
                        runCatching { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }.onFailure { vm.message(R.string.backup_failed) }
                    }, onDismiss = closeDialog)
                }
            }
        }
        item(key = "export_settings") {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportSettings) }
            SettingRow(stringResource(R.string.set_export_settings), stringResource(R.string.set_export_settings_desc), null, "settings_export") {
                runCatching { launcher.launch("retroplay-configuracoes.json") }.onFailure { vm.message(R.string.backup_failed) }
            }
        }
        item(key = "import_settings") {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importSettings) }
            SettingRow(stringResource(R.string.set_import_settings), stringResource(R.string.set_import_settings_desc), null, "settings_import") {
                runCatching { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.onFailure { vm.message(R.string.backup_failed) }
            }
        }
    }

    private fun LazyListScope.parental() {
        item(key = "pin") {
            val pin = snap.value(AppSettings.PARENTAL_PIN)
            val enter = stringResource(R.string.pin_enter)
            val newPin = stringResource(R.string.pin_new)
            val remove = stringResource(R.string.pin_remove)
            fun askNew() = openDialog {
                PinDialog(
                    newPin,
                    onSubmit = { vm.setAt(AppSettings.PARENTAL_PIN, ScopeRef.Global, it); vm.message(R.string.pin_saved); closeDialog(); true },
                    onDismiss = closeDialog,
                    extraAction = if (pin.isNotEmpty()) remove to {
                        vm.setAt(AppSettings.PARENTAL_PIN, ScopeRef.Global, null)
                        vm.setAt(AppSettings.PARENTAL_SETTINGS, ScopeRef.Global, null)
                        vm.setAt(AppSettings.PARENTAL_DELETE, ScopeRef.Global, null)
                        vm.message(R.string.pin_removed); closeDialog()
                    } else null,
                )
            }
            SettingRow(stringResource(R.string.set_pin), stringResource(R.string.set_pin_desc), stringResource(if (pin.isEmpty()) R.string.pin_not_set else R.string.pin_is_set), "parental_pin") {
                // Trocar um PIN existente pede o PIN atual primeiro.
                if (pin.isEmpty()) askNew() else openDialog { PinDialog(enter, onSubmit = { ok -> (ok == pin).also { if (it) askNew() } }, onDismiss = closeDialog) }
            }
        }
        listOf(
            Triple(AppSettings.PARENTAL_SETTINGS, R.string.set_lock_settings, R.string.set_lock_settings_desc),
            Triple(AppSettings.PARENTAL_DELETE, R.string.set_lock_delete, R.string.set_lock_delete_desc),
        ).forEach { (def, title, desc) ->
            item(key = def.key) {
                val on = snap.value(def)
                SettingRow(stringResource(title), stringResource(desc), onOff(on), "set_${def.key}") {
                    if (snap.value(AppSettings.PARENTAL_PIN).isEmpty()) vm.message(R.string.pin_required_first)
                    else vm.setAt(def, ScopeRef.Global, !on)
                }
            }
        }
    }

    private fun LazyListScope.about() {
        item(key = "info") {
            val context = LocalContext.current
            val version = remember { versionName(context) }
            val abi = remember { AbiDetector.detect()?.abiName }
            val ram = rememberTotalRam()
            androidx.compose.foundation.layout.Column {
                val first = rememberInitialFocus(false)
                SettingRow(stringResource(R.string.about_version), null, version, "about_version", Modifier.focusRequester(first)) {}
                SettingRow(stringResource(R.string.about_abi), null, abi ?: stringResource(R.string.abi_unknown), "about_abi") {}
                SettingRow(stringResource(R.string.about_device), null, "${Build.MANUFACTURER} ${Build.MODEL}", "about_device") {}
                SettingRow(stringResource(R.string.about_android), null, "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "about_android") {}
                SettingRow(stringResource(R.string.about_ram), null, ram, "about_ram") {}
            }
        }
        item(key = "log") {
            val context = LocalContext.current
            val logFile = remember { ErrorLog.file(context.filesDir) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        out.write(ErrorLog.header(versionName(context), AbiDetector.detect()?.abiName).toByteArray())
                        if (logFile.isFile) logFile.inputStream().use { it.copyTo(out) }
                    }
                }.isSuccess
                vm.message(if (ok) R.string.log_exported else R.string.import_failed)
            }
            val size = if (logFile.isFile) stringResource(R.string.log_entries, ((logFile.length() + 1023) / 1024).toInt()) else stringResource(R.string.log_empty)
            SettingRow(stringResource(R.string.about_log), stringResource(R.string.about_log_desc), size, "about_log") {
                runCatching { launcher.launch("retroplay-erros.txt") }.onFailure { vm.message(R.string.import_failed) }
            }
        }
        item(key = "licenses") {
            val title = stringResource(R.string.about_licenses)
            SettingRow(title, stringResource(R.string.about_licenses_desc), null, "about_licenses") {
                openDialog { TextDialog(title, OPEN_SOURCE_LICENSES, closeDialog) }
            }
        }
        item(key = "legal") {
            val title = stringResource(R.string.about_legal)
            val text = stringResource(R.string.legal_text)
            SettingRow(title, stringResource(R.string.about_legal_desc), null, "about_legal") { openDialog { TextDialog(title, text, closeDialog) } }
        }
    }

    // ------------------------------------------------------------------ linhas genéricas

    private fun LazyListScope.scopeNote() {
        when (scope.scope) {
            Scope.GLOBAL -> Unit
            Scope.SYSTEM -> noteRes(R.string.only_global_note, "scope")
            Scope.GAME -> noteRes(R.string.game_settings_note, "scope")
        }
    }

    private fun LazyListScope.noteRes(res: Int, key: String) {
        item(key = "n_$key") { NoteText(stringResource(res), key) }
    }

    /** Opções sem nível (não scoped) só aparecem no Global. */
    private fun visible(def: SettingDef<*>) = def.scoped || inGlobal

    private fun LazyListScope.choice(def: SettingDef.Choice, title: Int, desc: Int) {
        if (!visible(def)) return
        item(key = def.key) { ChoiceRow(def, title, stringResource(desc)) }
    }

    private fun LazyListScope.bool(def: SettingDef.Bool, title: Int, desc: Int) {
        if (!visible(def)) return
        item(key = def.key) { BoolRow(def, title, desc) }
    }

    /** Valor que vale se este nível não tiver personalização (null no Global). */
    private fun <T : Any> inherited(def: SettingDef<T>): T? = when {
        !def.scoped || inGlobal -> null
        scope.scope == Scope.SYSTEM -> snap.value(def)
        else -> snap.value(def, system)
    }

    @Composable
    private fun ChoiceRow(def: SettingDef.Choice, titleRes: Int, desc: String, labelOverrides: Map<String, String> = emptyMap()) {
        val labels = optionLabels(def) + labelOverrides
        val resolved = snap.resolve(def, system, gameKey)
        val title = stringResource(titleRes)
        val inheritedLabel = inherited(def)?.let { labels[it] ?: it }
        SettingRow(title, desc, labels[resolved.value] ?: resolved.value, "set_${def.key}", badge = overrideBadge(scope.scope, resolved.source, def.scoped)) {
            openDialog {
                ChoiceDialog(
                    title = title,
                    options = def.options.map { it to (labels[it] ?: it) },
                    selected = if (inheritedLabel != null) snap.get(def, scope) else resolved.value,
                    inheritedLabel = inheritedLabel,
                    onPick = { vm.set(def, it); closeDialog() },
                    onDismiss = closeDialog,
                )
            }
        }
    }

    @Composable
    private fun BoolRow(def: SettingDef.Bool, titleRes: Int, descRes: Int) {
        val resolved = snap.resolve(def, system, gameKey)
        val title = stringResource(titleRes)
        val on = stringResource(R.string.value_on)
        val off = stringResource(R.string.value_off)
        val inheritedValue = inherited(def)
        SettingRow(title, stringResource(descRes), if (resolved.value) on else off, "set_${def.key}", badge = overrideBadge(scope.scope, resolved.source, def.scoped)) {
            if (inheritedValue == null) vm.set(def, !resolved.value)
            else openDialog {
                ChoiceDialog(
                    title = title,
                    options = listOf("true" to on, "false" to off),
                    selected = snap.get(def, scope)?.toString(),
                    inheritedLabel = if (inheritedValue) on else off,
                    onPick = { vm.set(def, it?.toBooleanStrict()); closeDialog() },
                    onDismiss = closeDialog,
                )
            }
        }
    }

    // ------------------------------------------------------------------ controles

    /** Botões que o assistente e o turbo oferecem (sem o direcional). */
    private fun remapButtons() = (currentSystem?.controllerLayout?.buttons ?: GENERIC_REMAP_BUTTONS)
        .filter { it.retroPad !in DIRECTIONS }

    @Composable
    private fun RemapRow() {
        val resolved = snap.resolve(AppSettings.MAPPING, system, gameKey)
        val custom = MappingCodec.decode(resolved.value)
        val title = stringResource(R.string.set_remap)
        val target = vm.game.value?.title ?: currentSystem?.displayName ?: stringResource(R.string.remap_all_systems)
        val start = stringResource(R.string.remap_start)
        val reset = stringResource(R.string.remap_reset)
        SettingRow(
            title, stringResource(R.string.set_remap_desc),
            if (custom.isEmpty()) stringResource(R.string.value_default) else stringResource(R.string.remap_custom, custom.size),
            "remap", badge = overrideBadge(scope.scope, resolved.source, true),
        ) {
            openDialog {
                val first = rememberInitialFocus()
                SettingsDialog("remap_menu", title, closeDialog) {
                    item { DialogOption(start, "remap_start", modifier = Modifier.focusRequester(first)) { openDialog { RemapWizard(target, custom) } } }
                    item { DialogOption(reset, "remap_reset") { vm.set(AppSettings.MAPPING, null); closeDialog() } }
                }
            }
        }
    }

    @Composable
    private fun RemapWizard(target: String, initial: Map<Int, RetroPadButton>) {
        RemapWizardDialog(target, remapButtons(), initial, onDone = { map ->
            vm.set(AppSettings.MAPPING, MappingCodec.encode(map))
            vm.message(R.string.remap_done)
            closeDialog()
        }, onDismiss = closeDialog)
    }

    @Composable
    private fun TurboRow() {
        val resolved = snap.resolve(AppSettings.TURBO, system, gameKey)
        val turbo = MappingCodec.decodeTurbo(resolved.value)
        val buttons = remapButtons()
        val label = if (turbo.isEmpty()) stringResource(R.string.turbo_none)
        else turbo.joinToString(", ") { b -> buttons.firstOrNull { it.retroPad == b }?.label ?: b.name }
        SettingRow(stringResource(R.string.set_turbo), stringResource(R.string.set_turbo_desc), label, "turbo", badge = overrideBadge(scope.scope, resolved.source, true)) {
            openDialog {
                TurboDialog(buttons, turbo, onDone = { vm.set(AppSettings.TURBO, MappingCodec.encodeTurbo(it)); closeDialog() }, onDismiss = closeDialog)
            }
        }
    }

    @Composable
    private fun Gamepads() {
        val pads = rememberGamepads()
        val ports = MappingCodec.decodePorts(snap.value(AppSettings.PORTS))
        androidx.compose.foundation.layout.Column {
            if (pads.isEmpty()) NoteText(stringResource(R.string.controls_none), "no_pads")
            pads.forEach { pad ->
                val port = ports[pad.descriptor]
                val portLabel = port?.let { stringResource(R.string.port_n, it + 1) } ?: stringResource(R.string.port_auto)
                val title = stringResource(R.string.set_port_title, pad.name)
                val auto = stringResource(R.string.port_auto)
                val labels = (0..3).map { stringResource(R.string.port_n, it + 1) }
                SettingRow(pad.name, stringResource(R.string.gamepad_desc, pad.profile.label, portLabel), portLabel, "pad_${pad.id}") {
                    openDialog {
                        ChoiceDialog(
                            title = title,
                            options = listOf("auto" to auto) + (0..3).map { it.toString() to labels[it] },
                            selected = port?.toString() ?: "auto",
                            inheritedLabel = null,
                            onPick = { pick ->
                                val updated = ports.toMutableMap().apply { if (pick == null || pick == "auto") remove(pad.descriptor) else put(pad.descriptor, pick.toInt()) }
                                vm.setAt(AppSettings.PORTS, ScopeRef.Global, MappingCodec.encodePorts(updated).ifEmpty { null })
                                closeDialog()
                            },
                            onDismiss = closeDialog,
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ núcleos

    @Composable
    private fun CoreChoiceRow(sys: GameSystem) {
        // No Global, a escolha é gravada no nível do console.
        val target = if (inGlobal) ScopeRef.system(sys.id) else scope
        val resolved = snap.resolve(AppSettings.CORE, sys.id, gameKey)
        val chosen = sys.cores.firstOrNull { it.id == resolved.value } ?: sys.defaultCore
        val names = sys.cores.associate { c -> c.id to if (c == sys.defaultCore) stringResource(R.string.core_default_suffix, c.displayName) else c.displayName }
        val title = stringResource(R.string.set_core, sys.displayName)
        val gameLevel = scope.scope == Scope.GAME
        val inheritedLabel = if (gameLevel) names[sys.cores.firstOrNull { it.id == snap.value(AppSettings.CORE, sys.id) }?.id ?: sys.defaultCore.id] else null
        val badge = when {
            gameLevel -> overrideBadge(Scope.GAME, resolved.source, true)
            scope.scope == Scope.SYSTEM && resolved.source == Scope.SYSTEM -> OverrideBadge.HERE
            else -> OverrideBadge.NONE
        }
        SettingRow(title, stringResource(R.string.set_core_desc), names[chosen.id], "core_choice_${sys.id.value}", badge = badge) {
            openDialog {
                ChoiceDialog(
                    title = title,
                    options = sys.cores.map { it.id to names.getValue(it.id) },
                    selected = if (gameLevel) snap.get(AppSettings.CORE, target) else chosen.id,
                    inheritedLabel = inheritedLabel,
                    onPick = { vm.setAt(AppSettings.CORE, target, it); closeDialog() },
                    onDismiss = closeDialog,
                )
            }
        }
    }

    /** Núcleos cujas opções aparecem neste nível. */
    private fun coreOptionCores(): List<CoreDescriptor> = when (scope.scope) {
        Scope.GLOBAL -> vm.allCores
        Scope.SYSTEM -> currentSystem?.cores.orEmpty()
        Scope.GAME -> listOfNotNull(currentSystem?.let { sys -> sys.cores.firstOrNull { it.id == snap.value(AppSettings.CORE, sys.id, gameKey) } ?: sys.defaultCore })
    }

    /** Níveis consultados, do mais específico ao global. */
    private fun chain(): List<ScopeRef> = when (scope.scope) {
        Scope.GLOBAL -> listOf(ScopeRef.Global)
        Scope.SYSTEM -> listOf(scope, ScopeRef.Global)
        Scope.GAME -> listOfNotNull(scope, system?.let { ScopeRef.system(it) }, ScopeRef.Global)
    }

    @Composable
    private fun CoreOptionsRow(core: CoreDescriptor) {
        val count = snap.keysIn(scope, AppSettings.coreOptionPrefix(core.id)).size
        val title = stringResource(R.string.set_core_options, core.displayName)
        SettingRow(
            title, stringResource(R.string.set_core_options_desc),
            if (count > 0) pluralStringResource(R.plurals.core_options_count, count, count) else stringResource(R.string.value_default),
            "coreopts_${core.id}", badge = if (count > 0 && !inGlobal) OverrideBadge.HERE else OverrideBadge.NONE,
        ) { openDialog { CoreOptionsHost(core, title) } }
    }

    @Composable
    private fun CoreOptionsHost(core: CoreDescriptor, title: String) {
        // Lê o snapshot ao vivo: o diálogo continua aberto enquanto os valores mudam.
        val live by vm.snapshot.collectAsStateWithLifecycle()
        val s = live ?: return
        val options = remember(core.id) { vm.coreOptions(core) }
        val chain = chain()
        val rows = options.map { opt ->
            val key = AppSettings.coreOptionKey(core.id, opt.key)
            val found = chain.firstNotNullOfOrNull { ref -> s.raw[ref.prefix + key]?.let { it to ref.scope } }
            val parent = chain.drop(1).firstNotNullOfOrNull { ref -> s.raw[ref.prefix + key] } ?: opt.default
            CoreOptionRow(
                option = opt,
                value = found?.first ?: opt.default,
                badge = overrideBadge(scope.scope, found?.second, true),
                atScope = s.raw[scope.prefix + key],
                inherited = parent,
            )
        }
        CoreOptionsDialog(
            title = title,
            rows = rows,
            allowInherit = !inGlobal,
            onSet = { opt, value -> vm.setCoreOptionAt(core, opt.key, scope, value) },
            onReset = { vm.resetCoreOptions(core) },
            onDismiss = closeDialog,
        )
    }

    @Composable
    private fun CoreRow(core: CoreDescriptor) {
        val states by vm.coreStates.collectAsStateWithLifecycle()
        val updates by vm.updates.collectAsStateWithLifecycle()
        val state = states[core.id]
        val users = vm.catalog.all.filter { sys -> sys.cores.any { it.id == core.id } }
        val usedBy = users.joinToString(", ") { it.shortName }
        // Consoles em que este núcleo está escolhido (padrão ou escolha do usuário).
        val chosenFor = users.filter { sys -> (sys.cores.firstOrNull { it.id == snap.value(AppSettings.CORE, sys.id) } ?: sys.defaultCore).id == core.id }
        val context = LocalContext.current
        val value = when (state) {
            is CoreInstallState.Installed -> if (updates[core.id] == true) stringResource(R.string.core_update_available)
            else stringResource(R.string.coremgr_installed, state.core.version.take(16), Formatter.formatShortFileSize(context, state.core.sizeBytes))
            is CoreInstallState.Downloading -> stringResource(R.string.coremgr_downloading, ((state.fraction ?: 0f) * 100).toInt())
            CoreInstallState.Queued -> stringResource(R.string.coremgr_queued)
            is CoreInstallState.Failed -> stringResource(R.string.core_failed)
            CoreInstallState.NotInstalled, null -> stringResource(R.string.core_not_installed)
        }
        val desc = when {
            chosenFor.isEmpty() -> stringResource(R.string.core_used_by, usedBy)
            users.size > 1 && chosenFor.size > 1 -> stringResource(R.string.core_shared, chosenFor.joinToString(", ") { it.shortName })
            else -> stringResource(R.string.core_in_use_by, chosenFor.joinToString(", ") { it.shortName })
        }
        SettingRow(core.displayName, desc, value, "core_${core.id}") {
            openDialog { CoreActionsDialog(core, state is CoreInstallState.Installed) }
        }
    }

    @Composable
    private fun CoreActionsDialog(core: CoreDescriptor, installed: Boolean) {
        val hasPrevious by produceState(false, core.id) { value = vm.hasPrevious(core) }
        val first = rememberInitialFocus()
        SettingsDialog("core_dialog", core.displayName, closeDialog, core.description) {
            item(key = "install") {
                DialogOption(stringResource(if (installed) R.string.core_action_update else R.string.core_action_install), "core_install", modifier = Modifier.focusRequester(first)) {
                    vm.installCore(core); closeDialog()
                }
            }
            if (hasPrevious) item(key = "rollback") { DialogOption(stringResource(R.string.core_action_rollback), "core_rollback") { vm.rollbackCore(core); closeDialog() } }
            if (installed) item(key = "remove") { DialogOption(stringResource(R.string.core_action_remove), "core_remove") { vm.removeCore(core); closeDialog() } }
        }
    }

    // ------------------------------------------------------------------ perfis

    // ------------------------------------------------------------------ controle na tela

    /** Escolhe o console cujo controle na tela será posicionado. */
    @Composable
    private fun PadConsolePicker() {
        var chosen by remember { mutableStateOf<com.retroplay.domain.system.SystemId?>(null) }
        val sys = chosen
        if (sys != null) {
            PadEditorHost(sys)
            return
        }
        ChoiceDialog(
            stringResource(R.string.set_touch_layout),
            com.retroplay.domain.system.SystemRegistry.all.map { it.id.value to it.displayName },
            selected = null, inheritedLabel = null,
            onPick = { v -> if (v != null) chosen = com.retroplay.domain.system.SystemId(v) else closeDialog() },
            onDismiss = closeDialog,
        )
    }

    /**
     * Editor em tela cheia: o controle do console aparece como no jogo e cada botão (ou o
     * direcional) pode ser arrastado com o dedo. Salva por console.
     */
    @Composable
    private fun PadEditorHost(systemId: com.retroplay.domain.system.SystemId) {
        val sys = com.retroplay.domain.system.SystemRegistry.byId(systemId) ?: return
        val saved = snap.value(AppSettings.TOUCH_LAYOUT, systemId, null)
        var offsets by remember(systemId) { mutableStateOf(com.retroplay.feature.game.touch.PadLayoutCodec.decode(saved)) }
        val style = com.retroplay.feature.game.touch.PadStyle.of(snap.value(AppSettings.TOUCH_STYLE))
        val look = com.retroplay.feature.game.touch.PadLook(
            if (style == com.retroplay.feature.game.touch.PadStyle.CLASSIC) com.retroplay.feature.game.touch.PadSkins.forSystem(systemId) else null,
            snap.value(AppSettings.TOUCH_PAD_OPACITY).toInt() / 100f,
            MaterialTheme.colorScheme.primary,
        )
        val scale = snap.value(AppSettings.TOUCH_SIZE).toInt() / 100f
        androidx.compose.ui.window.Dialog(
            onDismissRequest = closeDialog,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF1B1D24)).testTag("pad_editor_dialog")) {
                com.retroplay.feature.game.touch.VirtualPadEditor(
                    com.retroplay.feature.game.touch.VirtualPadSpecs.forSystem(sys), look, scale, offsets, onChange = { offsets = it },
                )
                androidx.compose.foundation.layout.Column(
                    Modifier.align(androidx.compose.ui.Alignment.Center),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Text(sys.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.touch_editor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        com.retroplay.ui.tv.Button(onClick = {
                            vm.setAt(AppSettings.TOUCH_LAYOUT, ScopeRef.system(systemId), com.retroplay.feature.game.touch.PadLayoutCodec.encode(offsets).ifEmpty { null })
                            closeDialog()
                        }, modifier = Modifier.testTag("touch_save")) { Text(stringResource(R.string.save)) }
                        com.retroplay.ui.tv.OutlinedButton(onClick = { offsets = emptyMap() }, modifier = Modifier.testTag("touch_reset")) {
                            Text(stringResource(R.string.touch_reset))
                        }
                        com.retroplay.ui.tv.OutlinedButton(onClick = closeDialog, modifier = Modifier.testTag("touch_cancel")) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }

    private enum class ProfileStep { LIST, NEW, EDIT, DELETE }

    /** Editor de perfil (avatar, nome e cor) em tela cheia, sobre as Configurações. */
    @Composable
    private fun ProfileEditorDialog(editActive: Boolean, onDone: () -> Unit = closeDialog) {
        val profiles by vm.profiles.collectAsStateWithLifecycle()
        val active by vm.activeProfile.collectAsStateWithLifecycle()
        val current = if (editActive) active ?: return else null
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDone,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.tv.material3.MaterialTheme.colorScheme.background)) {
                com.retroplay.feature.profiles.ProfileEditorScreen(
                    initial = current,
                    suggestedIndex = if (current == null) profiles.size else profiles.indexOf(current).coerceAtLeast(0),
                    firstRun = false,
                    canDelete = profiles.size > 1,
                    onSave = { name, avatar, accent ->
                        if (current == null) vm.createProfile(name, avatar, accent) else vm.updateActiveProfile(name, avatar, accent)
                        onDone()
                    },
                    onCancel = onDone,
                    onDelete = { vm.deleteActiveProfile(); onDone() },
                )
            }
        }
    }

    @Composable
    private fun ProfilesHost() {
        val profiles by vm.profiles.collectAsStateWithLifecycle()
        val active by vm.activeProfile.collectAsStateWithLifecycle()
        var step by remember { mutableStateOf(ProfileStep.LIST) }
        val current = active ?: return
        when (step) {
            ProfileStep.NEW -> ProfileEditorDialog(editActive = false) { step = ProfileStep.LIST }
            ProfileStep.EDIT -> ProfileEditorDialog(editActive = true) { step = ProfileStep.LIST }
            ProfileStep.DELETE -> ConfirmDialog(
                stringResource(R.string.profile_delete), stringResource(R.string.profile_delete_confirm, current.name),
                onConfirm = { vm.deleteActiveProfile(); step = ProfileStep.LIST }, onDismiss = { step = ProfileStep.LIST },
            )
            ProfileStep.LIST -> {
                val first = rememberInitialFocus()
                SettingsDialog("profiles_dialog", stringResource(R.string.set_profiles), closeDialog, stringResource(R.string.set_profiles_desc)) {
                    profiles.forEach { p ->
                        item(key = "p${p.id}") {
                            DialogOption(p.name, "profile_${p.id}", selected = p.id == current.id, modifier = if (p.id == current.id) Modifier.focusRequester(first) else Modifier) {
                                vm.switchProfile(p.id)
                            }
                        }
                    }
                    item(key = "new") { DialogOption(stringResource(R.string.profile_new), "profile_new") { step = ProfileStep.NEW } }
                    item(key = "edit") { DialogOption(stringResource(R.string.profile_edit_row), "profile_edit") { step = ProfileStep.EDIT } }
                    if (profiles.size > 1) item(key = "delete") { DialogOption(stringResource(R.string.profile_delete), "profile_delete") { step = ProfileStep.DELETE } }
                }
            }
        }
    }

    // ------------------------------------------------------------------ biblioteca

    @Composable
    private fun DuplicatesHost() {
        val dups by vm.duplicates.collectAsStateWithLifecycle()
        var pendingDelete by remember { mutableStateOf<Game?>(null) }
        val pin = snap.value(AppSettings.PARENTAL_PIN)
        val needsPin = pin.isNotEmpty() && snap.value(AppSettings.PARENTAL_DELETE)
        pendingDelete?.let { game ->
            PinDialog(stringResource(R.string.pin_enter), onSubmit = { ok ->
                (ok == pin).also { if (it) { vm.deleteGame(game); pendingDelete = null } }
            }, onDismiss = { pendingDelete = null })
            return
        }
        val first = rememberInitialFocus()
        SettingsDialog("dups_dialog", stringResource(R.string.set_duplicates), closeDialog, if (dups.isEmpty()) stringResource(R.string.duplicates_none) else stringResource(R.string.duplicates_hint)) {
            dups.forEachIndexed { i, g ->
                item(key = g.id) {
                    SettingRow(g.title, readablePath(g.uri), null, "dup_${g.id}", modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        if (needsPin) pendingDelete = g else vm.deleteGame(g)
                    }
                }
            }
            item(key = "ok") { DialogOption(stringResource(R.string.done), "dups_done", modifier = if (dups.isEmpty()) Modifier.focusRequester(first) else Modifier, onClick = closeDialog) }
        }
    }

    companion object {
        private val DIRECTIONS = setOf(RetroPadButton.UP, RetroPadButton.DOWN, RetroPadButton.LEFT, RetroPadButton.RIGHT)
    }
}

// ---------------------------------------------------------------------- auxiliares

@Composable
private fun Header(text: String) {
    androidx.tv.material3.Text(
        text,
        style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
        color = androidx.tv.material3.MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
fun NoteText(text: String, key: String) {
    androidx.tv.material3.Text(
        text,
        style = androidx.tv.material3.MaterialTheme.typography.bodySmall,
        color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("note_$key"),
    )
}

@Composable
private fun onOff(on: Boolean) = stringResource(if (on) R.string.value_on else R.string.value_off)

/** Caminho legível de um arquivo ou URI SAF. */
fun readablePath(uri: String): String {
    if (uri.startsWith("/")) return uri
    val decoded = android.net.Uri.decode(uri)
    return decoded.substringAfterLast("/document/", decoded.substringAfter("/tree/", decoded)).substringAfter(':')
}

fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
fun rememberTotalRam(): String {
    val context = LocalContext.current
    return remember {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        Formatter.formatShortFileSize(context, info.totalMem)
    }
}

data class GamepadInfo(val id: Int, val name: String, val descriptor: String, val profile: GamepadProfile)

/** Controles físicos conectados, atualizados quando um é ligado ou desligado. */
@Composable
fun rememberGamepads(): List<GamepadInfo> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(InputManager::class.java) }
    var pads by remember { mutableStateOf(listGamepads()) }
    DisposableEffect(manager) {
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { pads = listGamepads() }
            override fun onInputDeviceRemoved(deviceId: Int) { pads = listGamepads() }
            override fun onInputDeviceChanged(deviceId: Int) { pads = listGamepads() }
        }
        manager?.registerInputDeviceListener(listener, null)
        onDispose { manager?.unregisterInputDeviceListener(listener) }
    }
    return pads
}

private fun listGamepads(): List<GamepadInfo> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
    .filter { d ->
        !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }
    .map { GamepadInfo(it.id, it.name, it.descriptor, GamepadProfile.fromVendor(it.vendorId)) }

/** Rótulos dos valores de cada escolha. */
@Composable
fun optionLabels(def: SettingDef.Choice): Map<String, String> {
    val res: (String) -> Int? = when (def) {
        AppSettings.SHADER -> { v -> mapOf("none" to R.string.shader_none, "crt" to R.string.shader_crt, "lcd" to R.string.shader_lcd, "sharp" to R.string.shader_sharp, "smooth" to R.string.shader_smooth)[v] }
        AppSettings.ASPECT -> { v -> if (v == "pixel") R.string.aspect_pixel else R.string.aspect_original }
        AppSettings.AUDIO_LATENCY -> { v -> if (v == "low") R.string.latency_low else R.string.latency_normal }
        AppSettings.HOTKEY_MODIFIER -> { v -> if (v == "l3") R.string.mod_l3 else R.string.mod_select }
        AppSettings.MENU_COMBO -> { v -> mapOf("start_select" to R.string.combo_start_select, "l3_r3" to R.string.combo_l3_r3, "l1_r1_start" to R.string.combo_l1_r1_start, "off" to R.string.combo_off)[v] }
        AppSettings.FF_MODE -> { v -> if (v == "hold") R.string.ff_hold else R.string.ff_toggle }
        AppSettings.CORE_CHANNEL -> { v -> if (v == "nightly") R.string.channel_nightly else R.string.channel_stable }
        AppSettings.THEME -> { v -> if (v == "oled") R.string.theme_oled else R.string.theme_dark }
        AppSettings.CARD_SIZE -> { v -> mapOf("small" to R.string.size_small, "medium" to R.string.size_medium, "large" to R.string.size_large)[v] }
        AppSettings.ACCENT -> { v -> mapOf("LARANJA" to R.string.accent_LARANJA, "VIOLETA" to R.string.accent_VIOLETA, "CIANO" to R.string.accent_CIANO, "AMBAR" to R.string.accent_AMBAR, "VERDE" to R.string.accent_VERDE, "CORAL" to R.string.accent_CORAL)[v] }
        AppSettings.ANIMATIONS -> { v -> mapOf("COMPLETAS" to R.string.anim_COMPLETAS, "REDUZIDAS" to R.string.anim_REDUZIDAS, "DESLIGADAS" to R.string.anim_DESLIGADAS)[v] }
        AppSettings.LANGUAGE -> { v -> if (v == "system") R.string.lang_system else null }
        ConsoleSettings.NEOGEO_MODE -> { v -> mapOf("mvs" to R.string.neogeo_mode_mvs, "aes" to R.string.neogeo_mode_aes, "unibios" to R.string.neogeo_mode_unibios)[v] }
        ConsoleSettings.MD_PAD -> { v -> if (v == "3") R.string.md_pad_3 else R.string.md_pad_6 }
        AppSettings.TOUCH_STYLE -> { v -> if (v == "minimal") R.string.touch_style_minimal else R.string.touch_style_classic }
        AppSettings.TOUCH_PAD -> { v -> mapOf("auto" to R.string.touch_auto, "always" to R.string.touch_always, "never" to R.string.touch_never)[v] }
        ConsoleSettings.MD_REGION -> { v -> mapOf("auto" to R.string.region_auto, "ntsc-u" to R.string.region_ntsc_u, "ntsc-j" to R.string.region_ntsc_j, "pal" to R.string.region_pal)[v] }
        else -> { _ -> null }
    }
    return def.options.associateWith { v ->
        res(v)?.let { stringResource(it) } ?: when (def) {
            AppSettings.FF_SPEED -> "${v}x"
            AppSettings.REWIND_MB -> "$v MB"
            AppSettings.DEADZONE, AppSettings.TOUCH_PAD_OPACITY, AppSettings.TOUCH_SIZE -> "$v%"
            AppSettings.LANGUAGE -> LANGUAGE_NAMES[v] ?: v
            else -> v
        }
    }
}

/** Nomes dos idiomas sempre na própria língua. */
val LANGUAGE_NAMES = mapOf("pt-BR" to "Português (Brasil)", "en" to "English", "es" to "Español")

const val OPEN_SOURCE_LICENSES = """Onzetec Games — GPL-3.0

LibretroDroid (Filippo Scognamiglio) — GPL-3.0
Núcleos libretro (baixados sob demanda):
• Snes9x 2010 — licença não comercial do Snes9x
• Snes9x — licença não comercial do Snes9x
• Genesis Plus GX — licença não comercial
• PicoDrive — licença não comercial / MAME
• FinalBurn Neo — licença não comercial
• MAME 2000 (MAME 0.37b5) e MAME 2003-Plus — licença MAME (não comercial)

AndroidX, Jetpack Compose, Compose for TV, Room, DataStore, WorkManager, Navigation, Hilt/Dagger — Apache 2.0
Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache 2.0
Coil — Apache 2.0
OkHttp, Okio — Apache 2.0
Apache Commons Compress — Apache 2.0
XZ for Java — domínio público (0BSD)
NanoHTTPD — BSD 3-Clause
ZXing — Apache 2.0
smbj, asn-one (pasta da rede) — Apache 2.0
Bouncy Castle — MIT
MBassador — MIT
SLF4J — MIT

Bancos de dados: No-Intro e libretro-database (metadados), DAT do MAME 0.37b5, DAT do FBNeo.
Capas: projeto libretro-thumbnails."""
