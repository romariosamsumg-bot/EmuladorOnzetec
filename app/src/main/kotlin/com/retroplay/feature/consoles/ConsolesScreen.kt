package com.retroplay.feature.consoles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.system.GameSystem
import com.retroplay.ui.components.SystemCard
import com.retroplay.ui.components.rememberInitialFocus

@Composable
fun ConsolesEntry(
    onOpenConsole: (GameSystem) -> Unit,
    viewModel: ConsolesViewModel = hiltViewModel(),
) {
    val bios by viewModel.bios.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    ConsolesScreen(systems = viewModel.systems, onOpenConsole = onOpenConsole, badges = viewModel.systems.associate { it.id to biosBadge(it, bios) }, counts = counts)
}

@Composable
fun ConsolesScreen(
    systems: List<GameSystem>,
    onOpenConsole: (GameSystem) -> Unit,
    badges: Map<com.retroplay.domain.system.SystemId, BiosBadge> = emptyMap(),
    counts: Map<com.retroplay.domain.system.SystemId, Int>? = null,
) {
    val focus = rememberInitialFocus()
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 40.dp).testTag("screen_consoles"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.consoles_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 48.dp))
        Text(
            stringResource(R.string.consoles_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(systems, key = { _, s -> s.id.value }) { index, system ->
                // Folga em volta: o cartão cresce 10% com o foco e não pode ser cortado pela borda da lista.
                Box(Modifier.padding(vertical = 16.dp)) {
                    SystemCard(
                        system = system,
                        onClick = { onOpenConsole(system) },
                        modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                        biosBadge = badges[system.id] ?: BiosBadge.NONE,
                        gameCount = counts?.let { it[system.id] ?: 0 },
                    )
                }
            }
        }
    }
}
