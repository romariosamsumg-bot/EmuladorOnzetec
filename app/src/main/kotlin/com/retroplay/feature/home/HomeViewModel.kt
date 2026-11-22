package com.retroplay.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(catalog: SystemCatalog, library: LibraryRepository) : ViewModel() {
    // Fase 4: hero, fileiras e estado vindo da biblioteca.
    val systems: List<GameSystem> = catalog.all
    val gameCount: StateFlow<Int> = library.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
