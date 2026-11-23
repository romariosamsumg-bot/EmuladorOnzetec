package com.retroplay.feature.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.retroplay.ui.tv.Surface
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.ui.components.GameCard
import com.retroplay.ui.components.RestoreOrInitialFocus
import com.retroplay.ui.components.rememberFocus
import com.retroplay.ui.components.rememberFocusMemory
import com.retroplay.core.ui.R as UiR

private val KEYS = ("abcdefghijklmnopqrstuvwxyz1234567890").map { it.toString() }
private const val COLUMNS = 6

@Composable
fun SearchScreen(onOpenGame: (Game) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val voicePrompt = stringResource(R.string.search_voice_prompt)
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::set)
        }
    }
    SearchContent(
        query = query,
        results = state.results,
        hasLibrary = state.hasLibrary,
        onKey = viewModel::type,
        onBackspace = viewModel::backspace,
        onClear = viewModel::clear,
        onVoice = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
            try {
                voice.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.search_no_voice, Toast.LENGTH_LONG).show()
            }
        },
        onOpenGame = onOpenGame,
    )
}

@Composable
fun SearchContent(
    query: String,
    results: List<Game>,
    hasLibrary: Boolean,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onVoice: () -> Unit,
    onOpenGame: (Game) -> Unit,
) {
    val memory = rememberFocusMemory()
    val firstKey = remember { FocusRequester() }
    RestoreOrInitialFocus(memory, firstKey)
    Row(Modifier.fillMaxSize().padding(start = 48.dp, top = 40.dp, end = 24.dp).testTag("screen_search"), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        // Teclado em grade (D-pad) + campo
        Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.search_title), style = MaterialTheme.typography.displaySmall)
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    query.ifEmpty { stringResource(R.string.search_hint) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("search_query"),
                )
            }
            Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KEYS.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { i, key ->
                            val mod = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstKey) else Modifier
                            Key(key.uppercase(), mod.rememberFocus(memory, "key_$key").testTag("key_$key")) { onKey(key) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key(stringResource(R.string.search_space), Modifier.rememberFocus(memory, "key_space").testTag("key_space"), wide = 2) { onKey(" ") }
                    Key("⌫", Modifier.rememberFocus(memory, "key_back").testTag("key_backspace")) { onBackspace() }
                    Key(stringResource(R.string.search_clear), Modifier.rememberFocus(memory, "key_clear").testTag("key_clear"), wide = 2) { onClear() }
                    Key(null, Modifier.rememberFocus(memory, "key_voice").testTag("key_voice"), icon = UiR.drawable.ic_mic) { onVoice() }
                }
            }
        }
        // Resultados em tempo real
        Column(Modifier.fillMaxHeight()) {
            val message = when {
                !hasLibrary -> stringResource(R.string.search_empty_library)
                query.isBlank() -> stringResource(R.string.search_start)
                results.isEmpty() -> stringResource(R.string.search_no_results, query)
                else -> null
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 64.dp).testTag("search_message"))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp * LocalRetroUiPrefs.current.cardScale),
                modifier = Modifier.focusRestorer().testTag("search_results"),
                contentPadding = PaddingValues(top = 56.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(results, key = { it.id }) { game ->
                    val system = game.systemId?.let { SystemRegistry.byId(it) } ?: return@items
                    GameCard(
                        system = system, game = game, title = game.title, width = 140.dp, mixedGrid = true,
                        onClick = { onOpenGame(game) },
                        cardModifier = Modifier.rememberFocus(memory, "result_${game.id}"),
                        testTag = "result_${game.id}",
                    )
                }
            }
        }
    }
}

@Composable
private fun Key(label: String?, modifier: Modifier, wide: Int = 1, icon: Int? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = (54 * wide + 6 * (wide - 1)).dp, height = 48.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(painterResource(icon), contentDescription = stringResource(R.string.search_voice), modifier = Modifier.size(22.dp))
            else Text(label.orEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
