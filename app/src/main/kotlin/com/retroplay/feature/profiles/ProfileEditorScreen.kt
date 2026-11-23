package com.retroplay.feature.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.AccentColor
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.profile.Avatars
import com.retroplay.domain.profile.Profile
import com.retroplay.feature.settings.ConfirmDialog
import com.retroplay.feature.settings.TextInputDialog
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.ui.tv.Button
import com.retroplay.ui.tv.Card
import com.retroplay.ui.tv.OutlinedButton

/**
 * Criação/edição de perfil: avatar, nome e cor do tema. A tela já aparece na cor escolhida
 * (prévia do tema). [firstRun] = primeira abertura do app (sem "Cancelar").
 */
@Composable
fun ProfileEditorScreen(
    initial: Profile?,
    suggestedIndex: Int,
    firstRun: Boolean,
    canDelete: Boolean,
    onSave: (name: String, avatar: String, accent: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit = {},
) {
    val defaultName = stringResource(R.string.profile_default_name, suggestedIndex + 1)
    var name by rememberSaveable { mutableStateOf(initial?.name ?: defaultName) }
    var avatar by rememberSaveable { mutableStateOf(initial?.avatar ?: Avatars.suggestion(suggestedIndex)) }
    var accent by rememberSaveable { mutableStateOf(initial?.accent ?: AccentColor.entries[suggestedIndex % AccentColor.entries.size].name) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Prévia: a tela inteira já na cor escolhida.
    RetroPlayTheme(prefs = LocalRetroUiPrefs.current.copy(accent = accentOf(accent))) {
        val color = MaterialTheme.colorScheme.primary
        Row(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), MaterialTheme.colorScheme.background), radius = 900f))
                .padding(horizontal = 56.dp, vertical = 36.dp)
                .testTag("screen_profile_editor"),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ------------------------------------------------ prévia
            Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarBadge(avatar, accent, name.take(1).uppercase(), 180.dp, Modifier.testTag("profile_preview"))
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // ------------------------------------------------ escolhas
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (initial == null || firstRun) R.string.profile_create_title else R.string.profile_edit_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (firstRun) {
                    Text(stringResource(R.string.profile_first_run_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Nome primeiro: é o que o usuário quer mudar logo.
                val nameFocus = rememberInitialFocus()
                Label(R.string.profile_name)
                Button(onClick = { editingName = true }, modifier = Modifier.focusRequester(nameFocus).testTag("profile_name")) {
                    Text("$name  ✎", maxLines = 1)
                }
                Label(R.string.profile_avatar)
                Avatars.ALL.chunked(7).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Choice(selected = key == avatar, tag = "avatar_$key", onClick = { avatar = key }) {
                                AvatarBadge(key, accent, "", 48.dp)
                            }
                        }
                    }
                }
                Label(R.string.profile_color)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AccentColor.entries.forEach { a ->
                        Choice(selected = a.name == accent, tag = "color_${a.name}", onClick = { accent = a.name }) {
                            Box(Modifier.size(40.dp).background(a.color, CircleShape))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSave(name, avatar, accent) }, modifier = Modifier.testTag("profile_save")) {
                        Text(stringResource(if (firstRun || initial == null) R.string.profile_start else R.string.save))
                    }
                    if (!firstRun) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.testTag("profile_cancel")) { Text(stringResource(R.string.cancel)) }
                    }
                    if (canDelete && initial != null && !firstRun) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("profile_delete_this")) {
                            Text(stringResource(R.string.profile_delete_this))
                        }
                    }
                }
            }
        }
        if (editingName) {
            TextInputDialog(
                // Perfil novo com o nome sugerido: a caixa começa vazia (é só digitar).
                stringResource(R.string.profile_name_title), if (name == defaultName && (initial == null || !initial.isSetUp)) "" else name,
                onSave = { v -> v.trim().takeIf { it.isNotEmpty() }?.let { name = it.take(Profile.MAX_NAME) }; editingName = false },
                onDismiss = { editingName = false },
                maxLength = Profile.MAX_NAME,
            )
        }
        if (confirmDelete && initial != null) {
            ConfirmDialog(
                stringResource(R.string.profile_delete_this), stringResource(R.string.profile_delete_confirm, initial.name),
                onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

/** Opção redonda (avatar ou cor): anel na selecionada; cresce com o foco. */
@Composable
private fun Choice(selected: Boolean, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { this.selected = selected },
        shape = CardDefaults.shape(CircleShape),
        scale = CardDefaults.scale(focusedScale = 1.15f),
        // Foco: borda na cor do tema; escolhido: anel branco.
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = CircleShape)),
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
    ) {
        Box(
            Modifier.padding(3.dp).then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier).padding(3.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}
