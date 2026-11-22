package com.retroplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/**
 * Pede foco e, se o item ainda não existe (listas preguiçosas compõem os itens só no layout),
 * tenta de novo nos próximos quadros. Retorna true quando o foco foi aceito.
 */
suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 30): Boolean {
    repeat(maxFrames) {
        withFrameNanos { }
        if (runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return true
    }
    return false
}

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
        if (ready) requester.requestFocusWhenReady()
    }
    return requester
}
