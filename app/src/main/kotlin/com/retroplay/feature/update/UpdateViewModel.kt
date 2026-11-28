package com.retroplay.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateManager,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<UpdateState> = updates.state

    fun update(info: UpdateInfo) {
        viewModelScope.launch { updates.downloadAndInstall(info) }
    }

    fun check() {
        viewModelScope.launch { updates.check(force = true) }
    }

    /** "Instalar apps desconhecidos" para o Onzetec Games (Android 8+). */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Ao voltar das Configurações do Android: se a permissão foi dada, continua sozinho. */
    fun resumeAfterPermission(context: Context) {
        val info = updates.retryAfterPermission() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) update(info)
    }

    suspend fun notificationAsked(): Boolean = settings.current().value(AppSettings.NOTIF_ASKED)

    fun markNotificationAsked() {
        viewModelScope.launch { settings.set(AppSettings.NOTIF_ASKED, ScopeRef.Global, true) }
    }
}
