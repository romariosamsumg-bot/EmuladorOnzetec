package com.retroplay

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.retroplay.feature.profiles.StartupFlow
import com.retroplay.mobile.MobileShell
import com.retroplay.mobile.MobileStartupUi
import com.retroplay.mobile.MobileTheme

/** Celular: de ponta a ponta, em pé ou deitado (o jogo abre sempre deitado). */
fun Activity.applyAppWindow() {
    (this as? ComponentActivity)?.enableEdgeToEdge()
}

/** Celular: toque, menu inferior e telas em coluna. */
@Composable
fun AppContent(args: AppContentArgs) {
    MobileTheme {
        StartupFlow(
            options = args.options,
            profiles = args.profiles,
            activeId = args.activeId,
            ask = args.ask,
            actions = args.actions,
            ui = MobileStartupUi,
        ) { switchProfile ->
            MobileShell(goHomeRequests = args.goHome, profile = args.active, onSwitchProfile = switchProfile)
        }
    }
}
