package com.retroplay.feature.transfer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransferState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroplay.ui.tv.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import com.retroplay.ui.tv.OutlinedButton
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.data.library.GamesFolderManager
import com.retroplay.data.library.RoomLibraryRepository
import com.retroplay.domain.library.CopyState
import com.retroplay.domain.library.DefaultFolders
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemId
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.rememberInitialFocus

@Composable
fun TransferScreen(viewModel: TransferViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val needsSystem by viewModel.needsSystem.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val copy by viewModel.copy.collectAsStateWithLifecycle()
    val phoneState by viewModel.phone.state.collectAsStateWithLifecycle()
    val incoming by viewModel.phone.incoming.collectAsStateWithLifecycle()
    // Servidor de transferência ativo só enquanto esta tela está visível.
    LifecycleStartEffect(viewModel.phone) {
        viewModel.phone.acquire()
        onStopOrDispose { viewModel.phone.release() }
    }
    var showAdbHelp by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Game?>(null) }

    // Ao voltar da tela de permissão do sistema, confere de novo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addTree)
    }
    val legacyPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }

    TransferContent(
        defaults = defaults,
        folders = folders,
        scan = scan,
        count = count,
        needsSystem = needsSystem,
        copy = copy,
        phoneState = phoneState,
        incoming = incoming,
        showAdbHelp = showAdbHelp,
        onGrantAccess = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Cada TV leva a permissão para uma tela diferente: guia com o caminho e as opções.
                showAdbHelp = true
            } else {
                legacyPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        },
        onAddFolder = {
            try {
                treePicker.launch(null)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_folder_picker, Toast.LENGTH_LONG).show()
            }
        },
        onRemoveFolder = viewModel::removeFolder,
        onCopyFolder = viewModel::copyToDevice,
        onScan = viewModel::scanNow,
        onChooseSystem = { choosing = it },
    )

    // Guia da permissão; fecha sozinho quando a pasta Jogos fica pronta (ao voltar das Configurações).
    if (showAdbHelp && defaults?.gamesFolderReady != true) AllFilesAccessGuide(onDismiss = { showAdbHelp = false })

    choosing?.let { game ->
        ChooseSystemDialog(
            game = game,
            onChoose = { system ->
                viewModel.assignSystem(game, system)
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
fun TransferContent(
    defaults: DefaultFolders?,
    folders: List<GameFolder>,
    scan: ScanState,
    count: Int,
    needsSystem: List<Game>,
    copy: CopyState,
    phoneState: PhoneTransferState = PhoneTransferState.Stopped,
    incoming: List<IncomingFile> = emptyList(),
    showAdbHelp: Boolean,
    onGrantAccess: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (GameFolder) -> Unit,
    onCopyFolder: (GameFolder) -> Unit,
    onScan: () -> Unit,
    onChooseSystem: (Game) -> Unit,
) {
    val firstFocus = rememberInitialFocus(ready = defaults != null)
    val ready = defaults?.gamesFolderReady == true
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_transfer"),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.displaySmall) }

        // ---------------------------------------------------------- celular (principal)
        item {
            Section(stringResource(R.string.phone_transfer_title)) {
                PhoneTransferPanel(phoneState, incoming)
            }
        }

        // ---------------------------------------------------------- pasta Jogos
        item {
            Section(stringResource(R.string.games_folder_title)) {
                if (ready) {
                    Text(stringResource(R.string.games_folder_ready, defaults!!.gamesPath), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            R.string.games_folder_layout,
                            SystemRegistry.all.joinToString(", ") { it.displayName },
                            GamesFolderManager.BIOS_FOLDER,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onScan, modifier = Modifier.focusRequester(firstFocus).testTag("scan_now")) {
                        Text(stringResource(R.string.scan_now))
                    }
                } else {
                    Text(stringResource(R.string.games_folder_needs_access, defaults?.gamesPath ?: "Jogos"), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onGrantAccess, modifier = Modifier.focusRequester(firstFocus).testTag("grant_access")) {
                        Text(stringResource(R.string.grant_all_files_access))
                    }
                }
            }
        }

        // ---------------------------------------------------------- biblioteca / varredura
        item {
            Section(stringResource(R.string.library_title)) {
                val text = when (scan) {
                    is ScanState.Scanning -> if (scan.total > 0) {
                        stringResource(R.string.library_scanning, scan.processed, scan.total, scan.currentFile.orEmpty())
                    } else stringResource(R.string.library_scanning_short)
                    is ScanState.Idle -> pluralStringResource(
                        R.plurals.library_idle,
                        count,
                        count,
                        scan.lastFinishedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "—",
                    )
                    ScanState.Failed -> stringResource(R.string.library_scan_failed)
                }
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("scan_status"))
                if (scan is ScanState.Scanning && scan.total > 0) {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.background)) {
                        Box(
                            Modifier.fillMaxWidth(scan.processed.toFloat() / scan.total).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                if (!ready) {
                    OutlinedButton(onClick = onScan, modifier = Modifier.testTag("scan_now")) { Text(stringResource(R.string.scan_now)) }
                }
            }
        }

        // ---------------------------------------------------------- arquivos ambíguos
        if (needsSystem.isNotEmpty()) {
            item {
                Section(stringResource(R.string.needs_system_title, needsSystem.size)) {
                    Text(
                        stringResource(R.string.needs_system_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(needsSystem, key = { "ns${it.id}" }) { game ->
                OutlinedButton(onClick = { onChooseSystem(game) }, modifier = Modifier.testTag("needs_system_${game.id}")) {
                    Text(game.fileName, maxLines = 1)
                }
            }
        }

        // ---------------------------------------------------------- pastas
        item {
            Section(stringResource(R.string.folders_title)) {
                Text(
                    stringResource(R.string.folders_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onAddFolder, modifier = Modifier.testTag("add_folder")) { Text(stringResource(R.string.add_folder)) }
                val copyText = when (copy) {
                    is CopyState.Copying -> if (copy.total > 0) stringResource(R.string.copy_progress, copy.done, copy.total, copy.currentFile.orEmpty())
                    else stringResource(R.string.copy_starting)
                    is CopyState.Finished -> pluralStringResource(R.plurals.copy_done, copy.copied, copy.copied)
                    is CopyState.Failed -> pluralStringResource(
                        if (copy.noSpace) R.plurals.copy_failed_space else R.plurals.copy_failed, copy.copied, copy.copied,
                    )
                    CopyState.Idle -> null
                }
                if (copyText != null) {
                    Text(copyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("copy_status"))
                }
            }
        }
        items(folders, key = { "f${it.id}" }) { folder -> FolderRow(folder, onRemoveFolder, onCopyFolder) }

    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun FolderRow(folder: GameFolder, onRemove: (GameFolder) -> Unit, onCopy: (GameFolder) -> Unit) {
    val name = when (folder.displayName) {
        RoomLibraryRepository.APP_FOLDER_LABEL -> stringResource(R.string.folder_app)
        else -> folder.displayName
    }
    Row(
        Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(
                    when (folder.source) {
                        GameSource.DOCUMENT -> R.plurals.folder_info_document
                        GameSource.SMB -> R.plurals.folder_info_smb
                        GameSource.FILE -> R.plurals.folder_info_file
                    },
                    folder.gameCount,
                    folder.gameCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (folder.source != GameSource.FILE && folder.gameCount > 0) {
            OutlinedButton(onClick = { onCopy(folder) }, modifier = Modifier.testTag("copy_folder_${folder.id}")) {
                Text(stringResource(R.string.copy_to_tv))
            }
        }
        if (!folder.isDefault) {
            OutlinedButton(onClick = { onRemove(folder) }, modifier = Modifier.testTag("remove_folder_${folder.id}")) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}

@Composable
private fun ChooseSystemDialog(game: Game, onChoose: (SystemId) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val focus = rememberInitialFocus()
        Column(
            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(28.dp).testTag("choose_system"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.choose_system_title), style = MaterialTheme.typography.headlineSmall)
            Text(game.fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SystemRegistry.all.forEachIndexed { i, system ->
                Button(
                    onClick = { onChoose(system.id) },
                    modifier = (if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth().testTag("choose_${system.id.value}"),
                    colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) { Text(system.displayName) }
            }
        }
    }
}
