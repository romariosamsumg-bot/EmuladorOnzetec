package com.retroplay.feature.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.retroplay.ui.tv.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.settings.CoreOption
import com.retroplay.domain.settings.HomeRowGroups
import com.retroplay.domain.system.ConsoleButton
import com.retroplay.domain.system.RetroPadButton
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.ui.components.requestFocusWhenReady

/** Botões pedidos pelo assistente quando o nível é Global (vale para todos os consoles). */
val GENERIC_REMAP_BUTTONS = listOf(
    ConsoleButton("A", RetroPadButton.A), ConsoleButton("B", RetroPadButton.B),
    ConsoleButton("X", RetroPadButton.X), ConsoleButton("Y", RetroPadButton.Y),
    ConsoleButton("L", RetroPadButton.L), ConsoleButton("R", RetroPadButton.R),
    ConsoleButton("L2", RetroPadButton.L2), ConsoleButton("R2", RetroPadButton.R2),
    ConsoleButton("Select", RetroPadButton.SELECT), ConsoleButton("Start", RetroPadButton.START),
)

/** O que o assistente faz com uma tecla. */
sealed interface RemapKey {
    data class Assign(val keyCode: Int) : RemapKey
    data object Skip : RemapKey
    data object Ignore : RemapKey
}

/** Classifica a tecla: botões de gamepad são mapeados; OK do remoto pula; o resto é ignorado (Voltar fecha). */
fun classifyRemapKey(keyCode: Int): RemapKey = when {
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER -> RemapKey.Skip
    KeyEvent.isGamepadButton(keyCode) -> RemapKey.Assign(keyCode)
    else -> RemapKey.Ignore
}

/** Aplica uma atribuição: uma tecla física leva a um só botão do RetroPad. */
fun assignKey(current: Map<Int, RetroPadButton>, keyCode: Int, button: RetroPadButton): Map<Int, RetroPadButton> =
    current.filterValues { it != button }.filterKeys { it != keyCode } + (keyCode to button)

/**
 * Assistente "pressione o botão…": pede cada botão do console e grava a tecla física.
 * Começa do mapeamento atual, então pular mantém o que já existe.
 */
@Composable
fun RemapWizardDialog(
    target: String,
    buttons: List<ConsoleButton>,
    initial: Map<Int, RetroPadButton>,
    onDone: (Map<Int, RetroPadButton>) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var map by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    Dialog(onDismissRequest = onDismiss) {
        val button = buttons.getOrNull(index)
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(28.dp)
                .testTag("remap_dialog")
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK
                    if (event.nativeKeyEvent.repeatCount > 0 || button == null) return@onPreviewKeyEvent true
                    when (val k = classifyRemapKey(event.key.nativeKeyCode)) {
                        is RemapKey.Assign -> map = assignKey(map, k.keyCode, button.retroPad)
                        RemapKey.Skip -> Unit
                        RemapKey.Ignore -> return@onPreviewKeyEvent event.key.nativeKeyCode != KeyEvent.KEYCODE_BACK
                    }
                    if (index + 1 >= buttons.size) onDone(map) else index++
                    true
                }
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.remap_title, target), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.remap_step, index + 1, buttons.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.remap_press), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                button?.label.orEmpty(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("remap_button"),
            )
            Text(stringResource(R.string.remap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Escolha dos botões com turbo (vários ao mesmo tempo). */
@Composable
fun TurboDialog(buttons: List<ConsoleButton>, initial: Set<RetroPadButton>, onDone: (Set<RetroPadButton>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    val first = rememberInitialFocus()
    SettingsDialog("turbo_dialog", stringResource(R.string.set_turbo), onDismiss, stringResource(R.string.set_turbo_desc)) {
        buttons.forEachIndexed { i, b ->
            item(key = b.retroPad.name) {
                DialogOption(b.label, "turbo_${b.retroPad.name}", selected = b.retroPad in selected, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    selected = if (b.retroPad in selected) selected - b.retroPad else selected + b.retroPad
                }
            }
        }
        item(key = "done") { DialogOption(stringResource(R.string.done), "turbo_done") { onDone(selected) } }
    }
}

/** Valor de uma opção de núcleo resolvido nos níveis, para a lista. */
data class CoreOptionRow(val option: CoreOption, val value: String, val badge: OverrideBadge, val atScope: String?, val inherited: String)

/** Lista das opções do núcleo; OK abre a escolha de valores. */
@Composable
fun CoreOptionsDialog(
    title: String,
    rows: List<CoreOptionRow>,
    allowInherit: Boolean,
    onSet: (CoreOption, String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val row = rows.firstOrNull { it.option.key == editing }
    if (row != null) {
        ChoiceDialog(
            title = row.option.description,
            options = row.option.values.map { it to it },
            selected = if (allowInherit) row.atScope else row.value,
            inheritedLabel = if (allowInherit) row.inherited else null,
            onPick = { onSet(row.option, it); editing = null },
            onDismiss = { editing = null },
        )
        return
    }
    val first = rememberInitialFocus()
    SettingsDialog("core_options_dialog", title, onDismiss, if (rows.isEmpty()) stringResource(R.string.coreopts_empty) else null) {
        rows.forEachIndexed { i, r ->
            item(key = r.option.key) {
                SettingRow(
                    title = r.option.description,
                    description = null,
                    value = r.value,
                    badge = r.badge,
                    tag = "coreopt_${r.option.key}",
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { editing = r.option.key }
            }
        }
        if (rows.isNotEmpty()) item(key = "reset") { DialogOption(stringResource(R.string.core_options_reset), "coreopt_reset", onClick = onReset) }
        if (rows.isEmpty()) item(key = "ok") { DialogOption(stringResource(R.string.done), "coreopt_ok", modifier = Modifier.focusRequester(first), onClick = onDismiss) }
    }
}

/** Ordem atual das fileiras (inclui grupos novos que não estavam gravados). */
fun parseRowOrder(raw: String): List<String> {
    val saved = raw.split(',').map { it.trim() }.filter { it in HomeRowGroups.DEFAULT_ORDER }.distinct()
    // Grupos novos (ex.: "popular" numa ordem salva antes de existir) entram logo depois do
    // grupo que os antecede na ordem padrão.
    val out = saved.toMutableList()
    HomeRowGroups.DEFAULT_ORDER.forEachIndexed { i, g ->
        if (g in out) return@forEachIndexed
        val prev = HomeRowGroups.DEFAULT_ORDER.take(i).lastOrNull { it in out }
        if (prev == null) out.add(g) else out.add(out.indexOf(prev) + 1, g)
    }
    return out
}

fun parseHidden(raw: String): Set<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

fun moveItem(list: List<String>, index: Int, delta: Int): List<String> {
    val to = index + delta
    if (index !in list.indices || to !in list.indices) return list
    return list.toMutableList().apply { add(to, removeAt(index)) }
}

@Composable
fun rowGroupName(group: String): String = when (group) {
    HomeRowGroups.CONTINUE -> stringResource(R.string.row_continue)
    HomeRowGroups.FAVORITES -> stringResource(R.string.row_favorites)
    HomeRowGroups.RECENT -> stringResource(R.string.row_recent)
    HomeRowGroups.SYSTEMS -> stringResource(R.string.row_systems)
    HomeRowGroups.MOST_PLAYED -> stringResource(R.string.row_most_played)
    HomeRowGroups.POPULAR -> stringResource(R.string.row_popular)
    else -> stringResource(R.string.row_genres)
}

/** Mostrar/ocultar e reordenar as fileiras da Início. */
@Composable
fun HomeRowsDialog(order: List<String>, hidden: Set<String>, onChange: (List<String>, Set<String>) -> Unit, onDismiss: () -> Unit) {
    var o by remember { mutableStateOf(order) }
    var h by remember { mutableStateOf(hidden) }
    val first = rememberInitialFocus()
    SettingsDialog("rows_dialog", stringResource(R.string.set_home_rows), onDismiss, stringResource(R.string.rows_hint)) {
        o.forEachIndexed { i, group ->
            item(key = group) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val visible = group !in h
                    Button(
                        onClick = { h = if (visible) h + group else h - group; onChange(o, h) },
                        modifier = Modifier.weight(1f).testTag("row_toggle_$group").let { if (i == 0) it.focusRequester(first) else it },
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text((if (visible) "✓  " else "     ") + rowGroupName(group) + " · " + stringResource(if (visible) R.string.row_visible else R.string.row_hidden))
                    }
                    Button(
                        onClick = { o = moveItem(o, i, -1); onChange(o, h) },
                        enabled = i > 0,
                        modifier = Modifier.testTag("row_up_$group"),
                    ) { Text("▲") }
                    Button(
                        onClick = { o = moveItem(o, i, 1); onChange(o, h) },
                        enabled = i < o.lastIndex,
                        modifier = Modifier.testTag("row_down_$group"),
                    ) { Text("▼") }
                }
            }
        }
        item(key = "done") { DialogOption(stringResource(R.string.done), "rows_done", modifier = Modifier.fillMaxWidth(), onClick = onDismiss) }
    }
}
