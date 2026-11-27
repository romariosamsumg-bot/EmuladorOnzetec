package com.retroplay.mobile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.retroplay.R
import com.retroplay.data.library.RoomLibraryRepository
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.feature.console.LocalRomSource
import com.retroplay.feature.transfer.AllFilesAccessGuide
import com.retroplay.feature.transfer.TransferViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Copia arquivos escolhidos no celular para a pasta do app (o console vem da extensão ou do conteúdo). */
@HiltViewModel
class MobileImportViewModel @Inject constructor(private val roms: LocalRomSource) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _results = Channel<LocalRomSource.ImportSummary>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val r = withContext(Dispatchers.IO) { roms.importMany(uris) }
            _busy.value = false
            _results.send(r)
        }
    }
}

/**
 * "Adicionar jogos" no celular: escolher arquivos do aparelho (downloads, Drive, cartão), a pasta
 * Jogos, outras pastas e os arquivos que precisam de um console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLibrary(
    nav: MobileNav,
    viewModel: TransferViewModel = hiltViewModel(),
    importer: MobileImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val needsSystem by viewModel.needsSystem.collectAsStateWithLifecycle()
    val count by viewModel.count.collectAsStateWithLifecycle()
    val importing by importer.busy.collectAsStateWithLifecycle()
    var guide by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf<Game?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(importer) {
        importer.results.collect { r ->
            val msg = buildList {
                if (r.copied > 0) add(resources.getQuantityString(R.plurals.mobile_import_done, r.copied, r.copied))
                if (r.unsupported > 0) add(resources.getQuantityString(R.plurals.mobile_import_unsupported, r.unsupported, r.unsupported))
                if (r.error == LocalRomSource.ImportResult.NoSpace) add(resources.getString(R.string.error_no_space))
                else if (r.error != null) add(resources.getString(R.string.import_failed))
            }.joinToString("\n")
            if (r.copied > 0 && r.error == null) com.retroplay.ui.feel.UiFeel.success() else com.retroplay.ui.feel.UiFeel.error()
            if (msg.isNotEmpty()) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importer.import(it) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(viewModel::addTree) }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val ready = defaults?.gamesFolderReady == true

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.mobile_add_games)) }, navigationIcon = { BackButton(nav) }) },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("screen_library"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Block(stringResource(R.string.mobile_import_title), stringResource(R.string.mobile_import_body)) {
                    Button(
                        onClick = {
                            try { filePicker.launch(arrayOf("*/*")) } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.no_picker, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth().testTag("import_files"),
                    ) { Text(stringResource(R.string.mobile_import_button)) }
                    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("import_progress"))
                }
            }
            item {
                Block(
                    stringResource(R.string.games_folder_title),
                    if (ready) stringResource(R.string.mobile_folder_ready, defaults!!.gamesPath)
                    else stringResource(R.string.mobile_folder_needs_access, defaults?.gamesPath ?: "Jogos"),
                ) {
                    if (ready) {
                        Caption(stringResource(R.string.mobile_folder_layout, SystemRegistry.all.joinToString(", ") { it.displayName }))
                        OutlinedButton(onClick = viewModel::scanNow, modifier = Modifier.testTag("scan_now")) { Text(stringResource(R.string.scan_now)) }
                    } else {
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    if (!openAllFilesAccess(context)) guide = true
                                } else {
                                    legacy.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
                                }
                            },
                            modifier = Modifier.testTag("grant_access"),
                        ) { Text(stringResource(R.string.grant_all_files_access)) }
                    }
                }
            }
            item {
                val text = when (val s = scan) {
                    is ScanState.Scanning -> if (s.total > 0) stringResource(R.string.library_scanning, s.processed, s.total, s.currentFile.orEmpty())
                    else stringResource(R.string.library_scanning_short)
                    is ScanState.Idle -> pluralStringResource(
                        R.plurals.library_idle, count, count,
                        s.lastFinishedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "—",
                    )
                    ScanState.Failed -> stringResource(R.string.library_scan_failed)
                }
                Block(stringResource(R.string.library_title), text) {
                    val s = scan
                    if (s is ScanState.Scanning) {
                        if (s.total > 0) LinearProgressIndicator(progress = { s.processed.toFloat() / s.total }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (needsSystem.isNotEmpty()) {
                item { Block(stringResource(R.string.needs_system_title, needsSystem.size), stringResource(R.string.needs_system_body)) {} }
                items(needsSystem, key = { "ns${it.id}" }) { game ->
                    ListItem(
                        headlineContent = { Text(game.fileName, maxLines = 1) },
                        supportingContent = { Text(stringResource(R.string.choose_system_title)) },
                        modifier = Modifier.testTag("needs_system_${game.id}").clickableRow { choosing = game },
                    )
                }
            }
            item {
                Block(stringResource(R.string.folders_title), stringResource(R.string.mobile_folders_body)) {
                    OutlinedButton(
                        onClick = {
                            try { treePicker.launch(null) } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.no_folder_picker, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.testTag("add_folder"),
                    ) { Text(stringResource(R.string.add_folder)) }
                }
            }
            items(folders, key = { "f${it.id}" }) { folder -> FolderItem(folder) { viewModel.removeFolder(folder) } }
        }
    }
    if (guide && !ready) AllFilesAccessGuide(onDismiss = { guide = false })
    choosing?.let { game ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.choose_system_title)) },
            text = {
                LazyColumn {
                    items(SystemRegistry.all, key = { it.id.value }) { system ->
                        ListItem(
                            headlineContent = { Text(system.displayName) },
                            modifier = Modifier.testTag("choose_${system.id.value}").clickableRow {
                                viewModel.assignSystem(game, system.id)
                                choosing = null
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Abre direto a permissão "Acesso a todos os arquivos" do app (celulares têm essa tela). */
private fun openAllFilesAccess(context: android.content.Context): Boolean {
    val pkg = Uri.parse("package:${context.packageName}")
    return listOf(
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
    ).any { runCatching { context.startActivity(it) }.isSuccess }
}

@Composable
private fun Block(title: String, body: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("block_body"))
            content()
        }
    }
}

@Composable
private fun FolderItem(folder: GameFolder, onRemove: () -> Unit) {
    val name = if (folder.displayName == RoomLibraryRepository.APP_FOLDER_LABEL) stringResource(R.string.folder_app) else folder.displayName
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Caption(
                pluralStringResource(
                    when (folder.source) {
                        GameSource.DOCUMENT -> R.plurals.folder_info_document
                        GameSource.SMB -> R.plurals.folder_info_smb
                        GameSource.FILE -> R.plurals.folder_info_file
                    },
                    folder.gameCount, folder.gameCount,
                ),
            )
        }
        if (!folder.isDefault) TextButton(onClick = onRemove, modifier = Modifier.testTag("remove_folder_${folder.id}")) { Text(stringResource(R.string.remove)) }
    }
}
