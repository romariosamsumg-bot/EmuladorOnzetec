package com.retroplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.retroplay.R
import com.retroplay.core.common.AbiDetector
import com.retroplay.ui.components.PlaceholderScreen
import com.retroplay.core.ui.R as UiR

/** Fase 7: categorias completas com hierarquia global → console → jogo. */
@Composable
fun SettingsScreen() {
    val abi = remember { AbiDetector.detect()?.abiName }
    PlaceholderScreen(
        title = stringResource(R.string.settings_title),
        body = stringResource(R.string.settings_body),
        iconRes = UiR.drawable.ic_nav_settings,
        testTag = "screen_settings",
        extraInfo = stringResource(R.string.settings_device_abi, abi ?: stringResource(R.string.abi_unknown)),
    )
}
