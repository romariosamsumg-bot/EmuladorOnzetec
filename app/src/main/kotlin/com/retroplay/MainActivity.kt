package com.retroplay

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.retroplay.core.ui.theme.AccentColor
import com.retroplay.core.ui.theme.AnimationLevel
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.core.ui.theme.RetroUiPrefs
import com.retroplay.data.settings.SettingsCache
import com.retroplay.diagnostics.AppLocale
import com.retroplay.diagnostics.ErrorLog
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.feature.cores.CoreAutoUpdater
import com.retroplay.ui.shell.RetroPlayShell
import com.retroplay.domain.profile.ProfileRepository
import com.retroplay.feature.profiles.ProfilePickerScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var settingsCache: SettingsCache
    @Inject lateinit var coreUpdater: CoreAutoUpdater
    @Inject lateinit var profiles: ProfileRepository

    /**
     * "Quem vai jogar?": com mais de um perfil e a opção ligada, pergunta uma vez ao abrir o app.
     */
    @Composable
    private fun ProfileGate(snap: SettingsSnapshot, content: @Composable () -> Unit) {
        // Decidido uma única vez, ao abrir: criar um perfil depois não mostra a pergunta no meio do uso.
        var ask by rememberSaveable { mutableStateOf<Boolean?>(null) }
        val list by profiles.profiles.collectAsStateWithLifecycle(null)
        val all = list
        if (ask == null && ready && all != null) ask = snap.value(AppSettings.PROFILE_ASK) && all.size > 1
        if (ask == true && all != null) {
            val activeId = snap.value(AppSettings.ACTIVE_PROFILE).toLongOrNull() ?: 1L
            ProfilePickerScreen(all, activeId) { p ->
                lifecycleScope.launch { profiles.switchTo(p.id) }
                ask = false
            }
        } else {
            // Enquanto decide (fração de segundo, coberta pela splash), a interface já aparece.
            content()
        }
    }

    @Volatile private var settingsReady = false

    /** Mesmo sinal, observável pela composição. */
    private var ready by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A splash do sistema some assim que o primeiro frame é desenhado (< 1 s).
        // Mantida até as configurações carregarem, para o tema/cor escolhidos aparecerem já no 1º quadro.
        installSplashScreen().setKeepOnScreenCondition { !settingsReady }
        super.onCreate(savedInstanceState)
        setContent {
            val snap by settings.snapshots.collectAsStateWithLifecycle(SettingsSnapshot())
            RetroPlayTheme(prefs = uiPrefs(snap)) {
                // Surface raiz: define fundo e cor de conteúdo padrão (texto/ícones claros).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    ProfileGate(snap) { RetroPlayShell() }
                }
            }
        }
        lifecycleScope.launch {
            val snap = settings.current()
            settingsReady = true
            ready = true
            // Idioma: aplica na hora se o valor gravado e o espelho síncrono divergirem.
            if (AppLocale.store(this@MainActivity, snap.value(AppSettings.LANGUAGE))) recreate()
            library.ensureDefaultFolders()
            // Varredura incremental ao abrir: só arquivos novos/alterados são lidos.
            if (snap.value(AppSettings.SCAN_ON_START)) library.scanNow()
            runCatching { coreUpdater.runIfDue() }.onFailure { ErrorLog.record(filesDir, "Atualização automática de núcleos falhou", it) }
        }
        lifecycleScope.launch {
            settings.snapshots.map { it.value(AppSettings.LANGUAGE) }.distinctUntilChanged().drop(1).collect { tag ->
                if (AppLocale.store(this@MainActivity, tag)) recreate()
            }
        }
    }

    /** Som de navegação (Interface): clique ao mover o foco com o direcional. */
    @SuppressLint("RestrictedApi") // falso positivo: Activity.dispatchKeyEvent é API pública
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && settingsCache.latest.value.value(AppSettings.NAV_SOUND)) {
            val effect = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEY_CLICK
                else -> null
            }
            if (effect != null) getSystemService(AudioManager::class.java)?.playSoundEffect(effect)
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        fun uiPrefs(snap: SettingsSnapshot) = RetroUiPrefs(
            accent = runCatching { AccentColor.valueOf(snap.value(AppSettings.ACCENT)) }.getOrDefault(AccentColor.VIOLETA),
            oledBlack = snap.value(AppSettings.THEME) == "oled",
            animations = runCatching { AnimationLevel.valueOf(snap.value(AppSettings.ANIMATIONS)) }.getOrDefault(AnimationLevel.COMPLETAS),
            heroAnimated = snap.value(AppSettings.HERO_ANIMATED),
            cardScale = when (snap.value(AppSettings.CARD_SIZE)) { "small" -> 0.85f; "large" -> 1.2f; else -> 1f },
        )
    }
}
