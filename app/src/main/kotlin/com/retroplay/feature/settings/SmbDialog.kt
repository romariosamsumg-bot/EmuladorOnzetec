package com.retroplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.retroplay.R
import com.retroplay.ui.components.rememberInitialFocus

/** Dados digitados para a pasta da rede. */
data class SmbForm(val host: String = "", val share: String = "", val path: String = "", val user: String = "", val password: String = "")

private enum class SmbField { HOST, SHARE, PATH, USER, PASSWORD }

/** Formulário da pasta da rede: cada campo abre o teclado completo; "Testar e adicionar" conecta. */
@Composable
fun SmbDialog(onSubmit: (SmbForm) -> Unit, onDismiss: () -> Unit) {
    var form by remember { mutableStateOf(SmbForm()) }
    var editing by remember { mutableStateOf<SmbField?>(null) }
    val empty = stringResource(R.string.smb_empty)
    editing?.let { field ->
        val (title, value, hint) = when (field) {
            SmbField.HOST -> Triple(R.string.smb_host, form.host, stringResource(R.string.smb_host_hint))
            SmbField.SHARE -> Triple(R.string.smb_share, form.share, stringResource(R.string.smb_share_hint))
            SmbField.PATH -> Triple(R.string.smb_path, form.path, null)
            SmbField.USER -> Triple(R.string.smb_user, form.user, stringResource(R.string.smb_user_hint))
            SmbField.PASSWORD -> Triple(R.string.smb_password, form.password, null)
        }
        TextInputDialog(
            title = stringResource(title), initial = value, raw = true, secret = field == SmbField.PASSWORD, hint = hint, maxLength = 80,
            onSave = { v ->
                form = when (field) {
                    SmbField.HOST -> form.copy(host = v)
                    SmbField.SHARE -> form.copy(share = v)
                    SmbField.PATH -> form.copy(path = v)
                    SmbField.USER -> form.copy(user = v)
                    SmbField.PASSWORD -> form.copy(password = v)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        return
    }
    val first = rememberInitialFocus()
    SettingsDialog("smb_dialog", stringResource(R.string.smb_title), onDismiss, stringResource(R.string.smb_note)) {
        listOf(
            Triple(SmbField.HOST, R.string.smb_host, form.host),
            Triple(SmbField.SHARE, R.string.smb_share, form.share),
            Triple(SmbField.PATH, R.string.smb_path, form.path),
            Triple(SmbField.USER, R.string.smb_user, form.user),
            Triple(SmbField.PASSWORD, R.string.smb_password, if (form.password.isEmpty()) "" else "•".repeat(form.password.length)),
        ).forEachIndexed { i, (field, label, value) ->
            item(key = field.name) {
                SettingRow(
                    stringResource(label), null, value.ifEmpty { empty }, "smb_${field.name.lowercase()}",
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { editing = field }
            }
        }
        item(key = "add") { DialogOption(stringResource(R.string.smb_add), "smb_add") { onSubmit(form) } }
    }
}
