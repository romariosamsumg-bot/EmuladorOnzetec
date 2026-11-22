package com.retroplay.feature.consoles

import androidx.lifecycle.ViewModel
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConsolesViewModel @Inject constructor(catalog: SystemCatalog) : ViewModel() {
    val systems: List<GameSystem> = catalog.all
}
