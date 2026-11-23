package com.retroplay.feature.game.session

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import com.retroplay.ui.tv.Surface
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.settings.CoreOption
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.IdentificationStrategy
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.ui.components.requestFocusWhenReady
import androidx.compose.runtime.LaunchedEffect

enum class MenuPage { MAIN, SAVE, LOAD, CONTROLS, CORE_OPTIONS }
enum class ShaderChoice(val label: Int) { DEFAULT(R.string.shader_none), CRT(R.string.shader_crt), LCD(R.string.shader_lcd), SHARP(R.string.shader_sharp), SMOOTH(R.string.shader_smooth) }
enum class AspectChoice(val label: Int) { ORIGINAL(R.string.aspect_original), PIXEL_PERFECT(R.string.aspect_pixel) }

@Immutable
data class GameMenuState(
    val title: String,
    val system: GameSystem,
    val page: MenuPage = MenuPage.MAIN,
    val slots: List<SaveSlot> = emptyList(),
    val speed: Int = 1,
    val rewindEnabled: Boolean = false,
    val rewindAvailable: Boolean = false,
    val shader: ShaderChoice = ShaderChoice.DEFAULT,
    /** null = o núcleo não permite mudar a proporção. */
    val aspect: AspectChoice? = null,
    val showFps: Boolean = false,
    val slotCount: Int = 9,
    val coreOptions: List<CoreOption> = emptyList(),
    /** Jogos de vários discos (.m3u): quantidade e disco atual (0 = primeiro). */
    val discs: Int = 1,
    val currentDisc: Int = 0,
)

/** Ações do menu (implementadas pela GameActivity). */
interface GameMenuActions {
    fun resume()
    fun openPage(page: MenuPage)
    fun save(slot: Int)
    fun load(slot: Int)
    fun reset()
    fun insertCoin()
    fun cycleSpeed()
    fun toggleRewind()
    fun rewindSeconds()
    fun cycleShader()
    fun cycleAspect()
    fun toggleFps()
    fun screenshot()
    fun exit()
    fun setCoreOption(key: String, value: String) {}
    /** Troca para o próximo disco (como abrir a tampa e trocar o CD). */
    fun nextDisc() {}
}

/** Menu rápido sobre o jogo pausado. Voltar fecha a página atual ou o menu. */
@Composable
fun GameMenu(state: GameMenuState, actions: GameMenuActions) {
    BackHandler { if (state.page == MenuPage.MAIN) actions.resume() else actions.openPage(MenuPage.MAIN) }
    val wide = state.page == MenuPage.SAVE || state.page == MenuPage.LOAD
    // Cor de texto clara: o menu fica sobre o jogo, fora de uma Surface do tema.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).testTag("game_menu")) {
        Column(
            Modifier.fillMaxHeight().width(if (wide) 880.dp else 400.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
            when (state.page) {
                MenuPage.MAIN -> MainPage(state, actions)
                MenuPage.SAVE -> SlotsPage(state, saving = true, actions)
                MenuPage.LOAD -> SlotsPage(state, saving = false, actions)
                MenuPage.CONTROLS -> ControlsPage(state.system)
                MenuPage.CORE_OPTIONS -> CoreOptionsPage(state.coreOptions, actions)
            }
        }
    }
    }
}

@Composable
private fun MainPage(state: GameMenuState, a: GameMenuActions) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)
    data class Item(val tag: String, val label: Int, val value: String?, val enabled: Boolean = true, val action: () -> Unit)
    val items = buildList {
        // Sair fica em primeiro; o foco começa em Continuar (evita sair sem querer).
        add(Item("menu_exit", R.string.menu_exit, null) { a.exit() })
        add(Item("menu_resume", R.string.menu_resume, null) { a.resume() })
        add(Item("menu_save", R.string.menu_save, null) { a.openPage(MenuPage.SAVE) })
        add(Item("menu_load", R.string.menu_load, null, enabled = state.slots.isNotEmpty()) { a.openPage(MenuPage.LOAD) })
        add(Item("menu_rewind", R.string.menu_rewind, if (state.rewindEnabled) on else off) { a.toggleRewind() })
        if (state.rewindEnabled) add(Item("menu_rewind_back", R.string.menu_rewind_back, null, enabled = state.rewindAvailable) { a.rewindSeconds() })
        if (state.discs > 1) {
            add(Item("menu_disc", R.string.menu_disc, "${state.currentDisc + 1}/${state.discs}") { a.nextDisc() })
        }
        add(Item("menu_reset", R.string.menu_reset, null) { a.reset() })
        if (state.system.identification == IdentificationStrategy.ARCADE_ROMSET) {
            add(Item("menu_coin", R.string.menu_coin, null) { a.insertCoin() })
        }
        add(Item("menu_speed", R.string.menu_speed, if (state.speed == 1) off else "${state.speed}x") { a.cycleSpeed() })
        add(Item("menu_shader", R.string.menu_shader, null) { a.cycleShader() })
        add(Item("menu_aspect", R.string.menu_aspect, null, enabled = state.aspect != null) { a.cycleAspect() })
        add(Item("menu_controls", R.string.menu_controls, null) { a.openPage(MenuPage.CONTROLS) })
        add(Item("menu_core", R.string.menu_core_options, null) { a.openPage(MenuPage.CORE_OPTIONS) })
        add(Item("menu_fps", R.string.menu_fps, if (state.showFps) on else off) { a.toggleFps() })
        add(Item("menu_screenshot", R.string.menu_screenshot, null) { a.screenshot() })
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(items, key = { it.tag }) { item ->
            val value = when (item.tag) {
                "menu_shader" -> stringResource(state.shader.label)
                "menu_aspect" -> state.aspect?.let { stringResource(it.label) } ?: stringResource(R.string.aspect_unavailable)
                else -> item.value
            }
            MenuRow(
                label = stringResource(item.label),
                value = value,
                enabled = item.enabled,
                onClick = item.action,
                modifier = (if (item.tag == "menu_resume") Modifier.focusRequester(first) else Modifier).testTag(item.tag),
            )
        }
    }
}

@Composable
private fun MenuRow(label: String, value: String?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        // Itens desabilitados são pulados pelo D-pad.
        modifier = modifier.focusProperties { canFocus = enabled }.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color.Transparent,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SlotsPage(state: GameMenuState, saving: Boolean, a: GameMenuActions) {
    val first = remember { FocusRequester() }
    LaunchedEffect(saving) { first.requestFocusWhenReady() }
    val bySlot = state.slots.associateBy { it.slot }
    Text(stringResource(if (saving) R.string.menu_save_title else R.string.menu_load_title), style = MaterialTheme.typography.titleMedium)
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.testTag(if (saving) "slots_save" else "slots_load"),
    ) {
        // O automático só pode ser carregado (é gravado sozinho ao sair).
        val manual = (1..state.slotCount).toList()
        val slots = if (saving) manual else listOf(SaveSlot.AUTO) + manual
        items(slots, key = { it }) { slot ->
            val info = bySlot[slot]
            val enabled = saving || info != null
            val isFirst = if (saving) slot == 1 else slot == state.slots.minOfOrNull { it.slot }
            SlotCard(
                slot = slot,
                info = info,
                enabled = enabled,
                onClick = { if (saving) a.save(slot) else a.load(slot) },
                modifier = (if (isFirst) Modifier.focusRequester(first) else Modifier).testTag("slot_$slot"),
            )
        }
    }
}

@Composable
private fun SlotCard(slot: Int, info: SaveSlot?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.focusProperties { canFocus = enabled },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black), contentAlignment = Alignment.Center) {
                val bitmap = remember(info?.thumbnailPath, info?.savedAt) {
                    info?.thumbnailPath?.let { runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
                }
                if (bitmap != null) Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(if (info == null) stringResource(R.string.slot_empty) else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(8.dp)) {
                Text(if (slot == SaveSlot.AUTO) stringResource(R.string.slot_auto) else stringResource(R.string.slot_n, slot), style = MaterialTheme.typography.labelLarge)
                Text(
                    info?.let { DateUtils.getRelativeTimeSpanString(it.savedAt).toString() } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Mapa do controle do console → botões do gamepad (remapeamento na Fase 7). */
@Composable
private fun ControlsPage(system: GameSystem) {
    Text(stringResource(R.string.controls_title), style = MaterialTheme.typography.titleMedium)
    LazyColumn(Modifier.testTag("controls_list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(system.controllerLayout.buttons, key = { it.label }) { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    b.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (b.highlighted) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = Modifier.width(120.dp),
                )
                Text(gamepadName(b.retroPad), style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Text(stringResource(R.string.controls_hotkeys), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun gamepadName(button: RetroPadButton): String = when (button) {
    RetroPadButton.B -> stringResource(R.string.pad_bottom)
    RetroPadButton.A -> stringResource(R.string.pad_right)
    RetroPadButton.Y -> stringResource(R.string.pad_left)
    RetroPadButton.X -> stringResource(R.string.pad_top)
    RetroPadButton.L -> "L1 / LB"
    RetroPadButton.R -> "R1 / RB"
    RetroPadButton.L2 -> "L2 / LT"
    RetroPadButton.R2 -> "R2 / RT"
    RetroPadButton.START -> "Start / Menu"
    RetroPadButton.SELECT -> "Select / View"
    else -> button.name
}

/** Opções do núcleo: OK passa para o próximo valor; vale na hora e fica salvo para este jogo. */
@Composable
private fun CoreOptionsPage(options: List<CoreOption>, a: GameMenuActions) {
    val first = remember { FocusRequester() }
    LaunchedEffect(options.isEmpty()) { if (options.isNotEmpty()) first.requestFocusWhenReady() }
    Text(stringResource(R.string.core_options_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.testTag("core_options"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (options.isEmpty()) item { Text(stringResource(R.string.core_options_empty), style = MaterialTheme.typography.bodyLarge) }
        items(options, key = { it.key }) { o ->
            MenuRow(
                label = o.description,
                value = o.current,
                enabled = o.values.size > 1,
                onClick = {
                    val i = o.values.indexOf(o.current)
                    a.setCoreOption(o.key, o.values[(i + 1) % o.values.size])
                },
                modifier = (if (o == options.first()) Modifier.focusRequester(first) else Modifier).testTag("coreopt_${o.key}"),
            )
        }
    }
}

/** Opções de proporção de cada núcleo (variáveis libretro). */
object CoreAspect {
    data class Option(val key: String, val original: String, val pixelPerfect: String)

    fun forCore(coreId: String): Option? = when (coreId) {
        "snes9x2010" -> Option("snes9x_2010_aspect", "4:3", "uncorrected")
        "snes9x" -> Option("snes9x_aspect", "4:3", "uncorrected")
        "genesis_plus_gx" -> Option("genesis_plus_gx_aspect_ratio", "4:3", "Uncorrected")
        "picodrive" -> Option("picodrive_aspect", "4/3", "PAR")
        else -> null
    }
}
