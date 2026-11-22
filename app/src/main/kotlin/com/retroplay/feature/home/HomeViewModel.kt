package com.retroplay.feature.home

import androidx.lifecycle.ViewModel
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(catalog: SystemCatalog) : ViewModel() {
    // Fase 4: hero, fileiras e estado vindo da biblioteca (Room).
    val systems: List<GameSystem> = catalog.all
}
