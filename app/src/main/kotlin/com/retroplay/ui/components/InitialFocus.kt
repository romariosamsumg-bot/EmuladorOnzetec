package com.retroplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Cria um [FocusRequester] que recebe o foco quando a tela entra na composição,
 * garantindo que nunca exista "foco perdido" ao abrir uma tela.
 */
@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) {
        // requestFocus pode falhar se o nó ainda não foi anexado; tenta no próximo frame.
        runCatching { requester.requestFocus() }
    }
    return requester
}
