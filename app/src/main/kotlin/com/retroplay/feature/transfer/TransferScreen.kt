package com.retroplay.feature.transfer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.retroplay.R
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.core.ui.R as UiR

/** Fase 3 (USB/SAF) e Fase 5 (servidor local + QR code + PIN). */
@Composable
fun TransferScreen() = PlaceholderScreen(
    title = stringResource(R.string.transfer_title),
    body = stringResource(R.string.transfer_body),
    iconRes = UiR.drawable.ic_nav_transfer,
    testTag = "screen_transfer",
)
