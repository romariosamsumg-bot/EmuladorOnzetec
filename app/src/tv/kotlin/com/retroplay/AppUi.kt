package com.retroplay

import android.app.Activity
import androidx.compose.runtime.Composable
import com.retroplay.feature.profiles.StartupFlow
import com.retroplay.feature.profiles.TvStartupUi
import com.retroplay.ui.components.TvCanvas
import com.retroplay.ui.components.applyPhoneWindow
import com.retroplay.ui.shell.RetroPlayShell

/** TV: tela cheia; num celular (raro, este é o app de TV) fica deitado. */
fun Activity.applyAppWindow() = applyPhoneWindow()

/** TV, TV Box e Stick: interface 960×540 dp, menu lateral, foco pelo controle remoto. */
@Composable
fun AppContent(args: AppContentArgs) {
    TvCanvas {
        StartupFlow(
            options = args.options,
            profiles = args.profiles,
            activeId = args.activeId,
            ask = args.ask,
            actions = args.actions,
            ui = TvStartupUi,
        ) { switchProfile ->
            RetroPlayShell(goHomeRequests = args.goHome, profile = args.active, onSwitchProfile = switchProfile)
        }
    }
}
