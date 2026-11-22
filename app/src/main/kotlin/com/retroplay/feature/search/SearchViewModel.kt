package com.retroplay.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.library.Game
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.library.SearchMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import com.retroplay.domain.library.visible
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import javax.inject.Inject

data class SearchState(val query: String, val results: List<Game>, val hasLibrary: Boolean)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val query: StateFlow<String> = handle.getStateFlow(KEY, "")

    /** Resultados em tempo real (pequeno atraso para não recalcular a cada tecla). */
    val state: StateFlow<SearchState> = combine(library.observeAll(), query.debounce(120), settings.snapshots) { all, q, snap ->
        val games = all.visible(snap.value(AppSettings.HIDE_UNIDENTIFIED))
        SearchState(q, SearchMatcher.search(games, q), games.isNotEmpty())
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState("", emptyList(), true))

    fun type(c: String) { handle[KEY] = (query.value + c).take(40) }
    fun backspace() { handle[KEY] = query.value.dropLast(1) }
    fun clear() { handle[KEY] = "" }
    fun set(text: String) { handle[KEY] = text.take(40) }

    private companion object {
        const val KEY = "query"
    }
}
