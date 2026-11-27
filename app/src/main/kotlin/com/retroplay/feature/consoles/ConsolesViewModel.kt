package com.retroplay.feature.consoles

import kotlinx.coroutines.flow.map
import com.retroplay.domain.system.IdentificationStrategy
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

    /** Quantidade de jogos de cada console (mostrada no cartão). */
    val counts: StateFlow<Map<com.retroplay.domain.system.SystemId, Int>> = library.observeAll()
        .map { list -> list.mapNotNull { it.systemId }.groupingBy { it }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/** Situação da BIOS de um console no cartão. */
enum class BiosBadge { NONE, OK, INCOMPLETE, MISSING_REQUIRED, MISSING_OPTIONAL, MISSING_RECOMMENDED, WRONG_VERSION }

/** Qualquer uma das BIOS aceitas pelo console serve (ex.: PlayStation: scph5501, scph1001...). */
fun biosBadge(system: GameSystem, found: List<BiosStatus>): BiosBadge {
    val req = system.bios.firstOrNull() ?: return BiosBadge.NONE
    val names = system.bios.map { it.fileName.substringBeforeLast('.') }.toSet()
    val status = found.filter { it.system == system.id && it.setName in names }.maxByOrNull { if (it.completeForAll) 2 else if (it.complete) 1 else 0 }
    return when {
        status == null -> when {
            req.required -> BiosBadge.MISSING_REQUIRED
            system.identification == IdentificationStrategy.DISC_SERIAL -> BiosBadge.MISSING_RECOMMENDED
            else -> BiosBadge.MISSING_OPTIONAL
        }
        status.completeForAll -> BiosBadge.OK
        // Serve para um núcleo mas não para outro (ex.: neogeo.zip do MAME 0.37, que o FBNeo recusa).
        status.complete -> BiosBadge.WRONG_VERSION
        else -> BiosBadge.INCOMPLETE
    }
}
