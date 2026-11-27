package com.retroplay.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.retroplay.ui.tv.Button
import androidx.tv.material3.ButtonDefaults
import com.retroplay.ui.tv.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.settings.Scope
import com.retroplay.ui.components.rememberInitialFocus

/** Estado do indicador de herança mostrado ao lado do valor. */
enum class OverrideBadge { NONE, HERE, FROM_GLOBAL, FROM_SYSTEM }

fun overrideBadge(current: Scope, source: Scope?, scoped: Boolean): OverrideBadge = when {
    !scoped || current == Scope.GLOBAL -> OverrideBadge.NONE
    source == current -> OverrideBadge.HERE
    source == Scope.SYSTEM -> OverrideBadge.FROM_SYSTEM
    else -> OverrideBadge.FROM_GLOBAL
}

/** Largura máxima do valor à direita (o celular em pé usa menos, para o nome caber). */
val LocalSettingValueWidth = androidx.compose.runtime.staticCompositionLocalOf { 320.dp }

/** Linha de configuração: nome, descrição de uma linha e valor atual à direita. */
@Composable
fun SettingRow(
    title: String,
    description: String?,
    value: String?,
    tag: String,
    modifier: Modifier = Modifier,
    badge: OverrideBadge = OverrideBadge.NONE,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // O ListItem da TV pode guardar o primeiro onClick: sempre chama a versão mais nova.
    val latestClick by rememberUpdatedState(onClick)
    ListItem(
        selected = false,
        enabled = enabled,
        onClick = { latestClick() },
        modifier = modifier.fillMaxWidth().testTag(tag),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = description?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = {
            val maxWidth = LocalSettingValueWidth.current
            Column(Modifier.widthIn(max = maxWidth), horizontalAlignment = Alignment.End) {
                if (value != null) {
                    Text(
                        value,
                        maxLines = if (maxWidth < 320.dp) 2 else 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (badge == OverrideBadge.HERE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                BadgeText(badge)
            }
        },
    )
}

@Composable
private fun BadgeText(badge: OverrideBadge) {
    val text = when (badge) {
        OverrideBadge.NONE -> return
        OverrideBadge.HERE -> stringResource(R.string.override_here)
        OverrideBadge.FROM_GLOBAL -> stringResource(R.string.inherited_global)
        OverrideBadge.FROM_SYSTEM -> stringResource(R.string.inherited_system)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (badge == OverrideBadge.HERE) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (badge == OverrideBadge.HERE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun LazyListScope.sectionHeader(text: String, key: String) {
    item(key = "h_$key") {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

fun LazyListScope.note(text: String, key: String) {
    item(key = "n_$key") {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("note_$key"),
        )
    }
}

/** Caixa padrão dos diálogos das configurações. */
@Composable
fun SettingsDialog(tag: String, title: String, onDismiss: () -> Unit, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    com.retroplay.ui.components.CenteredDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 420.dp, max = 640.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).testTag(tag),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    }
}

/** Uma opção de diálogo (botão largo, ✓ quando selecionada). */
@Composable
fun DialogOption(text: String, tag: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val latestClick by rememberUpdatedState(onClick)
    Button(
        onClick = { latestClick() },
        modifier = modifier.fillMaxWidth().testTag(tag),
        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text((if (selected) "✓  " else "     ") + text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Escolha de um valor. [inheritedLabel] != null adiciona "Usar o valor herdado". */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    inheritedLabel: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    val first = rememberInitialFocus()
    SettingsDialog("choice_dialog", title, onDismiss, subtitle) {
        if (inheritedLabel != null) {
            item(key = "inherit") {
                DialogOption(
                    stringResource(R.string.use_inherited, inheritedLabel), "choice_inherit",
                    selected = selected == null, modifier = if (selected == null) Modifier.focusRequester(first) else Modifier,
                ) { onPick(null) }
            }
        }
        options.forEachIndexed { i, (value, label) ->
            item(key = value) {
                val focusHere = value == selected || (selected == null && inheritedLabel == null && i == 0) ||
                    (selected != null && options.none { it.first == selected } && i == 0)
                DialogOption(label, "choice_$value", selected = value == selected, modifier = if (focusHere) Modifier.focusRequester(first) else Modifier) { onPick(value) }
            }
        }
    }
}

/** Confirmação simples (ex.: reconstruir a biblioteca). */
@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = rememberInitialFocus()
    SettingsDialog("confirm_dialog", title, onDismiss, message) {
        item { DialogOption(stringResource(R.string.cancel), "confirm_cancel", modifier = Modifier.focusRequester(cancel), onClick = onDismiss) }
        item { DialogOption(stringResource(R.string.confirm), "confirm_ok", onClick = onConfirm) }
    }
}

/** Texto longo só para leitura (licenças, aviso legal). */
@Composable
fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    val ok = rememberInitialFocus()
    SettingsDialog("text_dialog", title, onDismiss) {
        item { Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
        item { DialogOption(stringResource(R.string.done), "text_ok", modifier = Modifier.focusRequester(ok), onClick = onDismiss) }
    }
}

/**
 * Teclado numérico para PIN (navegável com o direcional). [onSubmit] recebe os dígitos;
 * retornar false mostra "PIN incorreto" e limpa.
 */
@Composable
fun PinDialog(
    title: String,
    onSubmit: (String) -> Boolean,
    onDismiss: () -> Unit,
    length: Int = 4,
    extraAction: Pair<String, () -> Unit>? = null,
) {
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val first = rememberInitialFocus()
    com.retroplay.ui.components.CenteredDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(380.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).testTag("pin_dialog"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                (0 until length).joinToString("  ") { if (it < digits.length) "●" else "○" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("pin_dots"),
            )
            if (wrong) Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        Button(
                            onClick = {
                                wrong = false
                                when (key) {
                                    "⌫" -> digits = digits.dropLast(1)
                                    "OK" -> if (digits.length == length) { if (!onSubmit(digits)) { wrong = true; digits = "" } }
                                    else -> if (digits.length < length) {
                                        digits += key
                                        if (digits.length == length && !onSubmit(digits)) { wrong = true; digits = "" }
                                    }
                                }
                            },
                            modifier = Modifier.width(96.dp).testTag("pin_key_$key").let { if (key == "1") it.focusRequester(first) else it },
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                        ) { Text(if (key == "⌫") stringResource(R.string.pin_backspace) else key, maxLines = 1) }
                    }
                }
            }
            extraAction?.let { (label, action) ->
                DialogOption(label, "pin_extra", onClick = action)
            }
        }
    }
}

/** Teclas do teclado em grade (mesma ideia da Busca). */
private val TEXT_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890".map { it.toString() }
private const val TEXT_COLUMNS = 9

/** Teclado completo (servidor, usuário, senha): minúsculas, maiúsculas e símbolos. */
private val RAW_KEYS = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() } + listOf(".", "-", "_", "@", ":", "/", "\\", "#", "$", "!", "&", "*")

/** Tecla no modo completo: sem maiúscula automática (o ⇧ alterna). */
fun applyRawKey(text: String, key: String, shift: Boolean, maxLength: Int): String = when (key) {
    "⌫" -> text.dropLast(1)
    else -> if (text.length >= maxLength) text else text + if (shift) key.uppercase() else key
}

/** Aplica uma tecla ao texto: letras em maiúscula só no início de cada palavra. */
fun applyTextKey(text: String, key: String, maxLength: Int): String = when (key) {
    "⌫" -> text.dropLast(1)
    " " -> if (text.isEmpty() || text.endsWith(" ") || text.length >= maxLength) text else "$text "
    else -> if (text.length >= maxLength) text else text + if (text.isEmpty() || text.endsWith(" ")) key.uppercase() else key.lowercase()
}

/**
 * Entrada de texto pelo direcional: teclado em grade na própria tela (o teclado do sistema da
 * TV nem sempre é confortável no controle remoto).
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = 40,
    extraAction: Pair<String, () -> Unit>? = null,
    /** Teclado completo (minúsculas, símbolos, ⇧), sem maiúscula automática. */
    raw: Boolean = false,
    /** Esconde o texto (senha), mostrando só o último caractere. */
    secret: Boolean = false,
    hint: String? = null,
) {
    var text by remember { mutableStateOf(initial.take(maxLength)) }
    var shift by remember { mutableStateOf(false) }
    val first = rememberInitialFocus()
    com.retroplay.ui.components.CenteredDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).testTag("text_dialog_input"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
            ) {
                val shown = if (secret && text.isNotEmpty()) "•".repeat(text.length - 1) + text.last() else text
                Text(shown + "▏", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.testTag("text_input_value"))
            }
            val rows = if (raw) RAW_KEYS.chunked(12) + listOf(listOf("⇧", "⌫")) else TEXT_KEYS.chunked(TEXT_COLUMNS) + listOf(listOf(" ", "⌫"))
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { c, key ->
                        val label = when (key) {
                            " " -> "␣"
                            "⇧" -> (if (shift) "● " else "") + stringResource(R.string.key_shift)
                            else -> if (raw && shift) key.uppercase() else key
                        }
                        Button(
                            onClick = {
                                when {
                                    key == "⇧" -> shift = !shift
                                    raw -> text = applyRawKey(text, key, shift, maxLength)
                                    else -> text = applyTextKey(text, key, maxLength)
                                }
                            },
                            modifier = Modifier.width(if (key == " " || key == "⌫" || key == "⇧") 120.dp else if (raw) 42.dp else 56.dp)
                                .testTag("tkey_${when (key) { " " -> "space"; "⌫" -> "back"; "⇧" -> "shift"; else -> key }}")
                                .let { if (r == 0 && c == 0) it.focusRequester(first) else it },
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                }
            }
            DialogOption(stringResource(R.string.save), "text_input_save") { onSave(if (raw) text else text.trim()) }
            extraAction?.let { (label, action) -> DialogOption(label, "text_input_extra", onClick = action) }
        }
    }
}
