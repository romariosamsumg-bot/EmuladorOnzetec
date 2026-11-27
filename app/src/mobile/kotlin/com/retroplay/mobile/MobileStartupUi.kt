package com.retroplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.R
import com.retroplay.core.ui.theme.AccentColor
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.profile.Avatars
import com.retroplay.domain.profile.Profile
import com.retroplay.feature.profiles.AvatarBadge
import com.retroplay.feature.profiles.ProfileAvatar
import com.retroplay.feature.profiles.StartupUi
import com.retroplay.feature.profiles.accentOf

/** "Quem vai jogar?" e o editor de perfil em versão de toque, em pé. */
object MobileStartupUi : StartupUi {
    @Composable
    override fun Picker(profiles: List<Profile>, activeId: Long, onPick: (Profile) -> Unit, onAdd: () -> Unit, onEdit: (Profile) -> Unit) =
        MobileProfilePicker(profiles, onPick, onAdd, onEdit)

    @Composable
    override fun Editor(
        initial: Profile?,
        suggestedIndex: Int,
        firstRun: Boolean,
        canDelete: Boolean,
        onSave: (name: String, avatar: String, accent: String) -> Unit,
        onCancel: () -> Unit,
        onDelete: () -> Unit,
    ) = MobileProfileEditor(initial, suggestedIndex, firstRun, canDelete, onSave, onCancel, onDelete)
}

@Composable
fun MobileProfilePicker(profiles: List<Profile>, onPick: (Profile) -> Unit, onAdd: () -> Unit, onEdit: (Profile) -> Unit) {
    var managing by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).testTag("screen_profiles"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(if (managing) R.string.profile_manage_title else R.string.who_plays),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { if (managing) onEdit(p) else onPick(p) }.padding(8.dp).testTag("profile_${p.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        ProfileAvatar(p, 96.dp)
                        if (managing) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) { Text("✎", fontSize = 16.sp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!managing && profiles.size < Profile.MAX_PROFILES) {
                item(key = "add") {
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onAdd).padding(8.dp).testTag("profile_add"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 44.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.profile_add), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
            }
        }
        OutlinedButton(onClick = { managing = !managing }, modifier = Modifier.padding(vertical = 16.dp).testTag("profile_manage")) {
            Text(stringResource(if (managing) R.string.profile_manage_done else R.string.profile_manage))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MobileProfileEditor(
    initial: Profile?,
    suggestedIndex: Int,
    firstRun: Boolean,
    canDelete: Boolean,
    onSave: (name: String, avatar: String, accent: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val defaultName = stringResource(R.string.profile_default_name, suggestedIndex + 1)
    // Perfil novo com o nome sugerido: a caixa começa vazia (o nome sugerido fica de dica).
    val startEmpty = initial == null || !initial.isSetUp
    var name by rememberSaveable { mutableStateOf(if (startEmpty) "" else initial!!.name) }
    var avatar by rememberSaveable { mutableStateOf(initial?.avatar ?: Avatars.suggestion(suggestedIndex)) }
    var accent by rememberSaveable { mutableStateOf(initial?.accent ?: AccentColor.entries[suggestedIndex % AccentColor.entries.size].name) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val finalName = name.trim().ifEmpty { defaultName }.take(Profile.MAX_NAME)

    // Prévia: a tela inteira já na cor escolhida.
    RetroPlayTheme(prefs = LocalRetroUiPrefs.current.copy(accent = accentOf(accent))) {
        MobileTheme {
            val color = MaterialTheme.colorScheme.primary
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), MaterialTheme.colorScheme.background)))
                    .statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("screen_profile_editor"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(if (initial == null || firstRun) R.string.profile_create_title else R.string.profile_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (firstRun) {
                    Text(stringResource(R.string.profile_first_run_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                AvatarBadge(avatar, accent, finalName.take(1).uppercase(), 120.dp, Modifier.testTag("profile_preview"))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Profile.MAX_NAME) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("profile_name"),
                )
                Label(R.string.profile_avatar)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatars.ALL.forEach { key ->
                        Choice(key == avatar, "avatar_$key", { avatar = key }) { AvatarBadge(key, accent, "", 48.dp) }
                    }
                }
                Label(R.string.profile_color)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                    AccentColor.entries.forEach { a ->
                        Choice(a.name == accent, "color_${a.name}", { accent = a.name }) {
                            Box(Modifier.size(40.dp).background(a.color, CircleShape))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = { onSave(finalName, avatar, accent) }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("profile_save")) {
                    Text(stringResource(if (firstRun || initial == null) R.string.profile_start else R.string.save))
                }
                if (!firstRun) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = onCancel, modifier = Modifier.testTag("profile_cancel")) { Text(stringResource(R.string.cancel)) }
                        if (canDelete && initial != null) {
                            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("profile_delete_this")) {
                                Text(stringResource(R.string.profile_delete_this), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            if (confirmDelete && initial != null) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.profile_delete_this)) },
                    text = { Text(stringResource(R.string.profile_delete_confirm, initial.name)) },
                    confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag("confirm_yes")) { Text(stringResource(R.string.delete)) } },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

@Composable
private fun Label(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Choice(selected: Boolean, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.clip(CircleShape).clickable(onClick = onClick).testTag(tag).semantics { this.selected = selected }
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
