package com.retroplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.ui.tv.Button
import com.retroplay.ui.tv.OutlinedButton

/** "Sair do Onzetec Games?" (TV). O foco começa em Cancelar, para não fechar sem querer. */
@Composable
fun ExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CenteredDialog(onDismissRequest = onDismiss) {
        val cancel = rememberInitialFocus()
        Column(
            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(28.dp).testTag("exit_dialog"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.exit_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.exit_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel).testTag("exit_cancel")) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm, modifier = Modifier.testTag("exit_confirm")) { Text(stringResource(R.string.exit_confirm)) }
            }
        }
    }
}
