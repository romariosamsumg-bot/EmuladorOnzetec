package com.retroplay.feature.consoles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.BiosStatus
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConsolesViewModel @Inject constructor(catalog: SystemCatalog, library: LibraryRepository) : ViewModel() {
    val systems: List<GameSystem> = catalog.all

    /** BIOS encontradas, para mostrar a situação no cartão do console. */
    val bios: StateFlow<List<BiosStatus>> = library.observeBios().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Situação da BIOS de um console no cartão. */
enum class BiosBadge { NONE, OK, INCOMPLETE, MISSING_REQUIRED, MISSING_OPTIONAL }

fun biosBadge(system: GameSystem, found: List<BiosStatus>): BiosBadge {
    val req = system.bios.firstOrNull() ?: return BiosBadge.NONE
    val status = found.filter { it.system == system.id && it.setName == req.fileName.substringBeforeLast('.') }.maxByOrNull { it.complete }
    return when {
        status == null -> if (req.required) BiosBadge.MISSING_REQUIRED else BiosBadge.MISSING_OPTIONAL
        status.complete -> BiosBadge.OK
        else -> BiosBadge.INCOMPLETE
    }
}
