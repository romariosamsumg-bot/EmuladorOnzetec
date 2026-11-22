package com.retroplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Lembra qual item estava focado numa tela e devolve o foco a ele quando o usuário volta
 * (ex.: Home → detalhes → Voltar). O estado sobrevive à navegação (rememberSaveable).
 */
@Stable
class FocusMemory(initial: String?) {
    var lastFocused: String? by mutableStateOf(initial)
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Tenta focar o último item; false se ele não existe (ainda ou mais, ex.: jogo apagado). */
    fun restore(): Boolean {
        val key = lastFocused ?: return false
        val r = requesters[key] ?: return false
        return runCatching { r.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
    }

    companion object {
        val saver = Saver<FocusMemory, String>(save = { it.lastFocused ?: "" }, restore = { FocusMemory(it.ifEmpty { null }) })
    }
}

@Composable
fun rememberFocusMemory(): FocusMemory = rememberSaveable(saver = FocusMemory.saver) { FocusMemory(null) }

/** Registra o item na memória de foco. */
fun Modifier.rememberFocus(memory: FocusMemory, key: String): Modifier =
    focusRequester(memory.requester(key)).onFocusChanged { if (it.isFocused) memory.lastFocused = key }

/**
 * Foco ao abrir a tela: volta ao último item focado; senão usa [initial].
 * @param ready false enquanto o conteúdo carrega.
 */
@Composable
fun RestoreOrInitialFocus(memory: FocusMemory, initial: FocusRequester, ready: Boolean = true) {
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        // Itens de listas preguiçosas só existem depois do layout: tenta por alguns quadros.
        repeat(30) { frame ->
            withFrameNanos { }
            if (memory.restore()) return@LaunchedEffect
            // Dá alguns quadros para o item lembrado aparecer antes de cair no foco inicial.
            if (memory.lastFocused == null || frame >= 5) {
                if (runCatching { initial.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }
}
