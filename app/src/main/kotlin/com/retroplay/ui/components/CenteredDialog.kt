package com.retroplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Janela por cima da tela, sempre centralizada. A janela ocupa a tela toda e o conteúdo é
 * centralizado pelo próprio layout: com a escala da TvCanvas (tela de 540 dp), a janela padrão
 * do Android media o conteúdo numa densidade e o desenhava em outra, ficando no canto ou cortada.
 */
@Composable
fun CenteredDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { content() }
    }
}
