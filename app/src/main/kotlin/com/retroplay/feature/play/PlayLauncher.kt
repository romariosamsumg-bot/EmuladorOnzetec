package com.retroplay.feature.play

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.library.Game
import com.retroplay.domain.system.CoreDescriptor
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.feature.game.GameLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.GameKey
import com.retroplay.domain.settings.SettingsRepository

/** Pedido para abrir um jogo na GameActivity. */
data class LaunchRequest(
    val system: GameSystem,
    val core: CoreDescriptor,
    val uri: String,
    val fileName: String,
    val title: String,
    val gameId: Long?,
    val loadSlot: Int? = null,
    /** Arcade: núcleo cujo DAT o romset satisfaz. */
    val coreHint: String? = null,
)

/**
 * Abre jogos de qualquer tela. Se o núcleo do console ainda não foi baixado, baixa e abre o
 * jogo assim que terminar, expondo o progresso em [coreDownload].
 */
class PlayLauncher(
    private val scope: CoroutineScope,
    private val cores: CoreRepository,
    private val catalog: SystemCatalog,
    private val settings: SettingsRepository? = null,
) {
    private val _launches = Channel<LaunchRequest>(Channel.BUFFERED)
    val launches: Flow<LaunchRequest> = _launches.receiveAsFlow()

    /** Estado do núcleo sendo baixado para um jogo pendente (null = nada pendente). */
    private val _coreDownload = MutableStateFlow<CoreInstallState?>(null)
    val coreDownload: StateFlow<CoreInstallState?> = _coreDownload.asStateFlow()
    private var pendingJob: Job? = null

    fun play(game: Game, loadSlot: Int? = null) {
        val system = game.systemId?.let { catalog.byId(it) } ?: return
        play(LaunchRequest(system, system.defaultCore, game.uri, game.fileName, game.title, game.id, loadSlot, game.coreHint))
    }

    /**
     * Núcleo do jogo: escolha do usuário para o jogo → núcleo que o romset satisfaz (arcade) →
     * escolha para o console → padrão do console.
     */
    suspend fun coreFor(system: GameSystem, fileName: String, coreHint: String? = null): CoreDescriptor =
        chooseCore(system, settings?.current(), GameKey.of(system.id, fileName), coreHint)

    fun play(original: LaunchRequest) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            val request = original.copy(core = coreFor(original.system, original.fileName, original.coreHint))
            if (cores.installed(request.core) != null) {
                _launches.send(request)
                return@launch
            }
            cores.install(request.core)
            cores.observe(request.core).first { state ->
                _coreDownload.value = state
                state is CoreInstallState.Installed || state is CoreInstallState.Failed
            }.let { final ->
                if (final is CoreInstallState.Installed) {
                    _coreDownload.value = null
                    _launches.send(request)
                }
            }
        }
    }

    class Factory @Inject constructor(
        private val cores: CoreRepository,
        private val catalog: SystemCatalog,
        private val settings: SettingsRepository,
    ) {
        fun create(scope: CoroutineScope) = PlayLauncher(scope, cores, catalog, settings)
    }
}

/** Regra de escolha do núcleo (pura, testável). */
fun chooseCore(system: GameSystem, snap: com.retroplay.domain.settings.SettingsSnapshot?, gameKey: String, coreHint: String?): CoreDescriptor {
    fun byId(id: String?) = id?.takeIf { it.isNotEmpty() }?.let { c -> system.cores.firstOrNull { it.id == c } }
    return byId(snap?.get(AppSettings.CORE, com.retroplay.domain.settings.ScopeRef.game(gameKey)))
        ?: byId(coreHint)
        ?: byId(snap?.value(AppSettings.CORE, system.id))
        ?: system.defaultCore
}

/** Abre a GameActivity a cada pedido emitido pelo [PlayLauncher]. */
@Composable
fun LaunchGamesEffect(launcher: PlayLauncher) {
    val context = LocalContext.current
    LaunchedEffect(launcher) {
        launcher.launches.collect { context.startGame(it) }
    }
}

fun Context.startGame(r: LaunchRequest) {
    // O jogo roda em outro processo: libera as capas da memória para sobrar RAM ao emulador
    // (numa TV de 2 GB isso faz diferença). O cache em disco fica, e as capas voltam rápido.
    runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
    // Game feel: som de "abrir jogo", tremor/vibração forte, música sai e a tela do jogo entra com zoom.
    com.retroplay.ui.feel.UiFeel.launch()
    com.retroplay.ui.feel.MenuMusic.stop(fast = true)
    val options = android.app.ActivityOptions.makeCustomAnimation(this, com.retroplay.R.anim.game_open_enter, com.retroplay.R.anim.game_open_exit)
    startActivity(GameLauncher.intent(this, r.system, r.core, r.uri, r.fileName, r.title, r.gameId, r.loadSlot), options.toBundle())
}
