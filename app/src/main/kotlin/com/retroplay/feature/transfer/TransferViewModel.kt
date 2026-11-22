package com.retroplay.feature.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.CopyState
import com.retroplay.domain.library.DefaultFolders
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _defaults = MutableStateFlow<DefaultFolders?>(null)
    val defaults: StateFlow<DefaultFolders?> = _defaults.asStateFlow()

    val folders: StateFlow<List<GameFolder>> = library.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scan: StateFlow<ScanState> = library.observeScan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanState.Idle(null))
    val needsSystem: StateFlow<List<Game>> = library.observeNeedsSystem()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val count: StateFlow<Int> = library.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val copy: StateFlow<CopyState> = library.observeCopy()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CopyState.Idle)

    fun copyToDevice(folder: GameFolder) = library.copyFolderToDevice(folder.id)

    init {
        refresh()
    }

    /** Chamado ao abrir e ao voltar da tela de permissão do sistema. */
    fun refresh() {
        viewModelScope.launch {
            val before = _defaults.value?.gamesFolderReady
            val now = library.ensureDefaultFolders()
            _defaults.value = now
            if (now.gamesFolderReady && before == false) library.scanNow()
        }
    }

    fun scanNow() = library.scanNow()

    /** Pasta escolhida pelo seletor do sistema (pode ser um pendrive). */
    fun addTree(uri: Uri) {
        viewModelScope.launch {
            // Leitura + escrita (escrita permite "Apagar" jogos do pendrive); cai para só leitura se negado.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            library.addFolder(uri.toString(), GameSource.DOCUMENT, treeName(uri))
            library.scanNow()
        }
    }

    fun removeFolder(folder: GameFolder) {
        viewModelScope.launch {
            if (folder.source == GameSource.DOCUMENT) {
                runCatching {
                    val uri = Uri.parse(folder.uri)
                    val flags = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
                        ?.let { (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0) }
                        ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.releasePersistableUriPermission(uri, flags)
                }
            }
            library.removeFolder(folder.id)
        }
    }

    fun assignSystem(game: Game, system: SystemId) {
        viewModelScope.launch { library.assignSystem(game.id, system) }
    }

    private fun treeName(uri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return uri.toString()
        // "primary:Jogos/SNES" → "Jogos/SNES"; pendrives aparecem como "XXXX-XXXX:pasta".
        return docId.substringAfter(':').ifBlank { docId.substringBefore(':') }
    }
}
