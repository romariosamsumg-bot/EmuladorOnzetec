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
import com.retroplay.ui.feel.FeelPrefs
import com.retroplay.ui.feel.MenuMusic
import com.retroplay.ui.feel.UiFeel
import com.retroplay.data.settings.SettingsCache
import com.retroplay.diagnostics.AppLocale
import com.retroplay.diagnostics.ErrorLog
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.feature.cores.CoreAutoUpdater
import com.retroplay.domain.profile.ProfileRepository
import com.retroplay.feature.profiles.StartupActions
import com.retroplay.feature.profiles.StartupOptions
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

    @Inject lateinit var startupOptions: StartupOptions
    @Inject lateinit var appUpdates: com.retroplay.feature.update.UpdateManager

    /** Ações de perfil da trilha de inicialização. */
    private val startupActions = object : StartupActions {
        override suspend fun switchTo(id: Long) = profiles.switchTo(id)
        override suspend fun create(name: String, avatar: String, accent: String) = profiles.create(name, avatar, accent)
        override suspend fun update(id: Long, name: String, avatar: String, accent: String) = profiles.update(id, name, avatar, accent)
        override suspend fun delete(id: Long) { profiles.delete(id) }
    }

    @Volatile private var settingsReady = false

    /** Pedidos para voltar à tela Início (ex.: "Sair" no menu do jogo). */
    private val goHome = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_GO_HOME, false)) goHome.tryEmit(Unit)
    }

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
        applyAppWindow()
        UiFeel.init(this)
        setContent {
            val snap by settings.snapshots.collectAsStateWithLifecycle(SettingsSnapshot())
            val list by profiles.profiles.collectAsStateWithLifecycle(null)
            val active by profiles.active.collectAsStateWithLifecycle(null)
            // A cor do tema é a do perfil ativo.
            RetroPlayTheme(prefs = uiPrefs(snap, active?.accent)) {
                // Surface raiz: define fundo e cor de conteúdo padrão (texto/ícones claros).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    // Interface de cada app (sabores "tv" e "mobile"): mesma trilha de perfis,
                    // telas e navegação próprias.
                    AppContent(
                        AppContentArgs(
                            options = startupOptions,
                            profiles = list.takeIf { ready },
                            activeId = active?.id ?: snap.value(AppSettings.ACTIVE_PROFILE).toLongOrNull() ?: 1L,
                            ask = snap.value(AppSettings.PROFILE_ASK),
                            actions = startupActions,
                            goHome = goHome,
                            active = active,
                        ),
                    )
                }
            }
        }
        lifecycleScope.launch {
            // App 1.2: "Neo Geo" virou "Arcade" (configurações e pastas de saves).
            runCatching {
                com.retroplay.data.migration.SystemRenameMigration(
                    settings, listOf(java.io.File(filesDir, "states"), java.io.File(filesDir, "saves")), "neogeo", "arcade",
                ).run()
            }.onFailure { ErrorLog.record(filesDir, "Migração Neo Geo → Arcade falhou", it) }
            val snap = settings.current()
            settingsReady = true
            ready = true
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) MenuMusic.start(this@MainActivity)
            // Idioma: aplica na hora se o valor gravado e o espelho síncrono divergirem.
            if (AppLocale.store(this@MainActivity, snap.value(AppSettings.LANGUAGE))) recreate()
            library.ensureDefaultFolders()
            // Varredura incremental ao abrir: só arquivos novos/alterados são lidos.
            if (snap.value(AppSettings.SCAN_ON_START)) library.scanNow()
            runCatching { coreUpdater.runIfDue() }.onFailure { ErrorLog.record(filesDir, "Atualização automática de núcleos falhou", it) }
            // Versão nova do app (version.json do site): aviso na Início e checagem diária.
            runCatching { com.retroplay.feature.update.UpdateWorker.schedule(this@MainActivity) }
            runCatching { appUpdates.check() }
        }
        // Sons, vibração e música seguem as Configurações na hora.
        lifecycleScope.launch {
            settings.snapshots.map { FeelPrefs.of(it) }.distinctUntilChanged().collect { f ->
                UiFeel.soundsOn = f.sounds
                UiFeel.volume = f.volume
                UiFeel.hapticsOn = f.haptics
                MenuMusic.enabled = f.music
                MenuMusic.volume = f.musicVolume
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    MenuMusic.update(this@MainActivity, f.music, f.musicVolume)
                }
            }
        }
        lifecycleScope.launch {
            settings.snapshots.map { it.value(AppSettings.LANGUAGE) }.distinctUntilChanged().drop(1).collect { tag ->
                if (AppLocale.store(this@MainActivity, tag)) recreate()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (settingsReady) MenuMusic.start(this)
    }

    override fun onStop() {
        // Indo para um jogo (ou saindo): a música para na hora e continua do ponto ao voltar.
        MenuMusic.stop(fast = true)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) MenuMusic.release()
        super.onDestroy()
    }

    private val bumpCheck = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Game feel do controle remoto/gamepad: guarda o último direcional (o "tic" toca quando o foco
     * muda, em UiFeel.onFocusGained) e, se o foco não mudou logo depois, toca o "toc" da borda.
     * Voltar toca o som de voltar.
     */
    // RestrictedApi: falso positivo (Activity.dispatchKeyEvent é API pública).
    // GestureBackNavigation: o Voltar não é interceptado aqui, só toca o som; quem trata o Voltar
    // continuam sendo os BackHandlers (gesto preditivo intacto).
    @SuppressLint("RestrictedApi", "GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            UiFeel.lastDeviceId = event.deviceId
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val at = android.os.SystemClock.uptimeMillis()
                    UiFeel.lastDpadAt = at
                    if (event.repeatCount == 0) {
                        bumpCheck.removeCallbacksAndMessages(null)
                        bumpCheck.postDelayed({ if (UiFeel.lastFocusAt < at) UiFeel.bump() }, BUMP_DELAY_MS)
                    }
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B -> if (event.repeatCount == 0) UiFeel.back()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_GO_HOME = "go_home"
        private const val BUMP_DELAY_MS = 140L

        fun uiPrefs(snap: SettingsSnapshot, profileAccent: String? = null) = RetroUiPrefs(
            accent = runCatching { AccentColor.valueOf(profileAccent ?: snap.value(AppSettings.ACCENT)) }.getOrDefault(AccentColor.LARANJA),
            oledBlack = snap.value(AppSettings.THEME) == "oled",
            animations = runCatching { AnimationLevel.valueOf(snap.value(AppSettings.ANIMATIONS)) }.getOrDefault(AnimationLevel.COMPLETAS),
            heroAnimated = snap.value(AppSettings.HERO_ANIMATED),
            cardScale = when (snap.value(AppSettings.CARD_SIZE)) { "small" -> 0.85f; "large" -> 1.2f; else -> 1f },
        )
    }
}
