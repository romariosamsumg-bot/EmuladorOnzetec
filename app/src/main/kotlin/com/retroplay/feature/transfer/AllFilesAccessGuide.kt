package com.retroplay.feature.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.feature.settings.DialogOption
import com.retroplay.feature.settings.SettingsDialog
import com.retroplay.ui.components.rememberInitialFocus

/** Uma tela das Configurações do sistema que pode dar o "acesso a todos os arquivos". */
data class AccessScreen(val tag: String, val label: Int, val intent: Intent)

/**
 * Telas candidatas, da mais direta para a mais genérica. Cada fabricante de TV implementa um
 * subconjunto; só entram as que o aparelho realmente tem.
 */
fun allFilesAccessScreens(context: Context): List<AccessScreen> {
    val pkg = Uri.parse("package:${context.packageName}")
    val candidates = buildList {
        // Primeiro a tela do app (Apps → Onzetec Games): é por ela que as TVs Google chegam em
        // Permissões → Arquivos e mídia. As telas diretas vêm depois porque algumas TVs abrem
        // uma lista vazia ou sem efeito.
        add(AccessScreen("access_details", R.string.all_files_open_details, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(AccessScreen("access_app", R.string.all_files_open_app, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)))
            add(AccessScreen("access_list", R.string.all_files_open_list, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)))
        }
        add(AccessScreen("access_settings", R.string.all_files_open_settings, Intent(Settings.ACTION_SETTINGS)))
    }
    return candidates.filter { runCatching { context.packageManager.resolveActivity(it.intent, 0) != null }.getOrDefault(false) }
        .ifEmpty { candidates.takeLast(1) }
}

/** Guia "Permitir acesso aos arquivos": caminho na TV + botões para as telas existentes. */
@Composable
fun AllFilesAccessGuide(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val screens = remember { allFilesAccessScreens(context) }
    val first = rememberInitialFocus()
    SettingsDialog("all_files_guide", stringResource(R.string.all_files_title), onDismiss, stringResource(R.string.all_files_steps)) {
        screens.forEachIndexed { i, s ->
            item(key = s.tag) {
                DialogOption(stringResource(s.label), s.tag, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    try {
                        context.startActivity(s.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        // Some na hora: a próxima opção continua disponível.
                    } catch (e: SecurityException) {
                    }
                }
            }
        }
        item(key = "adb") {
            Text(
                stringResource(R.string.all_files_adb_hint, "adb shell appops set --uid com.retroplay.tv MANAGE_EXTERNAL_STORAGE allow"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("adb_help"),
            )
        }
    }
}
