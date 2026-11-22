package com.retroplay.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.system.GameSystem
import com.retroplay.ui.components.SystemCard
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.core.ui.R as UiR

@Composable
fun HomeEntry(
    onOpenTransfer: () -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val count by viewModel.gameCount.collectAsStateWithLifecycle()
    HomeScreen(systems = viewModel.systems, gameCount = count, onOpenTransfer = onOpenTransfer, onOpenConsole = onOpenConsole)
}

/** Fase 1: estado vazio da biblioteca. A home "streaming" completa chega na Fase 4. */
@Composable
fun HomeScreen(
    systems: List<GameSystem>,
    gameCount: Int = 0,
    onOpenTransfer: () -> Unit,
    onOpenConsole: (GameSystem) -> Unit,
) {
    val focus = rememberInitialFocus()
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp).testTag("screen_home"),
    ) {
        Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(if (gameCount > 0) R.string.home_library_title else R.string.home_empty_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                if (gameCount > 0) pluralStringResource(R.plurals.home_library_count, gameCount, gameCount) else stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenTransfer, modifier = Modifier.focusRequester(focus).testTag("home_transfer")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(UiR.drawable.ic_nav_transfer), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.nav_transfer))
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.home_supported_systems),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
        LazyRow(
            modifier = Modifier.focusRestorer().testTag("home_systems_row"),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(systems, key = { it.id.value }) { system ->
                SystemCard(system = system, onClick = { onOpenConsole(system) })
            }
        }
    }
}
