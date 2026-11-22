package com.retroplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Cria um [FocusRequester] que recebe o foco quando a tela fica pronta, garantindo
 * que nunca exista "foco perdido" ao abrir uma tela.
 *
 * @param ready passe false enquanto o conteúdo ainda carrega (ex.: lista assíncrona);
 *  o foco é pedido assim que virar true.
 */
@Composable
fun rememberInitialFocus(ready: Boolean = true): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester, ready) {
        if (ready) runCatching { requester.requestFocus() }
    }
    return requester
}
