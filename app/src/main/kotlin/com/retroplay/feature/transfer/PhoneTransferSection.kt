package com.retroplay.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransferState
import com.retroplay.domain.transfer.TransferError
import com.retroplay.ui.components.QrCode

/**
 * Versão em coluna para a tela Transferir: o QR ocupa o espaço que sobrar (até 240 dp), então
 * nunca é cortado, qualquer que seja o tamanho de fonte da TV.
 */
@Composable
fun ColumnScope.PhoneTransferColumn(state: PhoneTransferState, incoming: List<IncomingFile>) {
    when (state) {
        is PhoneTransferState.Running -> {
            Text(stringResource(R.string.phone_steps_short), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                QrCode(state.qrUrl, min(min(maxWidth, maxHeight), 240.dp), Modifier.testTag("transfer_qr"))
            }
            Text(state.url, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.testTag("transfer_url"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.phone_pin), style = MaterialTheme.typography.titleMedium)
                Text(
                    state.pin,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("transfer_pin"),
                )
            }
            incoming.take(3).forEach { IncomingRow(it) }
        }
        else -> PhoneTransferPanel(state, incoming)
    }
}

/** QR code + endereço + PIN, e os arquivos chegando ao vivo. */
@Composable
fun PhoneTransferPanel(state: PhoneTransferState, incoming: List<IncomingFile>, compact: Boolean = false) {
    when (state) {
        is PhoneTransferState.Running -> Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp)) {
            QrCode(state.qrUrl, if (compact) 170.dp else 220.dp, Modifier.testTag("transfer_qr"))
            Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                if (compact) {
                    Text(stringResource(R.string.phone_compact_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.phone_step_wifi), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.phone_step_qr), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    if (compact) state.url else stringResource(R.string.phone_step_url, state.url),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("transfer_url"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.phone_pin), style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.pin,
                        fontSize = if (compact) 26.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("transfer_pin"),
                    )
                }
                if (!compact) {
                    Text(
                        stringResource(R.string.phone_only_while_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                incoming.take(if (compact) 2 else 5).forEach { IncomingRow(it) }
            }
        }
        is PhoneTransferState.Failed -> Text(
            stringResource(
                if (state.reason == PhoneTransferState.Failed.Reason.NO_NETWORK) R.string.phone_no_network else R.string.phone_port_busy,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("transfer_error"),
        )
        PhoneTransferState.Stopped -> Text(stringResource(R.string.phone_starting), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun IncomingRow(file: IncomingFile) {
    val text = when (file.status) {
        IncomingFile.Status.RECEIVING -> stringResource(R.string.phone_receiving, file.name, (file.fraction * 100).toInt())
        IncomingFile.Status.DONE -> when {
            file.isBios -> stringResource(R.string.phone_received_bios, file.name)
            file.system != null -> stringResource(R.string.phone_received, file.name, SystemRegistry.byId(file.system!!)?.displayName ?: "")
            else -> stringResource(R.string.phone_received_unknown, file.name)
        }
        IncomingFile.Status.FAILED -> stringResource(
            when (file.error) {
                TransferError.NOT_A_GAME -> R.string.phone_failed_not_game
                TransferError.TOO_BIG -> R.string.phone_failed_too_big
                TransferError.NO_SPACE -> R.string.error_no_space
                else -> R.string.phone_failed_interrupted
            },
            file.name,
        )
    }
    Column(Modifier.testTag("incoming_${file.id}")) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = when (file.status) {
                IncomingFile.Status.FAILED -> MaterialTheme.colorScheme.error
                IncomingFile.Status.DONE -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
        )
        if (file.status == IncomingFile.Status.RECEIVING) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.background)) {
                Box(Modifier.fillMaxWidth(file.fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
