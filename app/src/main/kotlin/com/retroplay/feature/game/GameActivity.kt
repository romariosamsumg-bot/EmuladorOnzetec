package com.retroplay.feature.game

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.data.rom.RomPrepareException
import com.retroplay.data.rom.RomPreparer
import com.retroplay.data.saves.SaveStateStore
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.library.LibraryRepository
import com.retroplay.data.settings.CoreOptionsCache
import com.retroplay.domain.settings.AppSettings
import com.retroplay.core.common.DeviceKind
import com.retroplay.ui.components.TvCanvas
import com.retroplay.ui.components.applyPhoneWindow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.settings.ConsoleCoreOptions
import android.app.ActivityManager
import com.retroplay.domain.settings.CoreOption
import com.retroplay.domain.settings.GameKey
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import com.retroplay.domain.settings.SettingsSnapshot
import com.retroplay.feature.game.input.MappingCodec
import com.retroplay.feature.game.input.RetroPadKeys
import com.retroplay.domain.saves.FpsCounter
import com.retroplay.domain.saves.RewindBuffer
import com.retroplay.domain.saves.SaveSlot
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.IdentificationStrategy
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import com.retroplay.feature.game.input.Hotkey
import com.retroplay.feature.game.input.HotkeyDetector
import com.retroplay.feature.game.input.InputAction
import com.retroplay.feature.game.input.InputRouter
import com.retroplay.feature.game.input.PortAssigner
import com.retroplay.feature.game.input.StickToDpad
import com.retroplay.feature.game.session.AspectChoice
import com.retroplay.feature.game.session.CoreAspect
import com.retroplay.feature.game.session.GameMenu
import com.retroplay.feature.game.session.GameMenuActions
import com.retroplay.feature.game.session.GameMenuState
import com.retroplay.feature.game.session.MenuPage
import com.retroplay.feature.game.session.ShaderChoice
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Tela de jogo: GLRetroView em tela cheia + menu rápido (Compose) por cima.
 * Voltar (controle remoto), Guide ou Select+Start abrem o menu; o jogo fica congelado e mudo
 * enquanto ele está aberto. Salva SRAM e o save automático ao sair e ao perder o foco (Home).
 */
@AndroidEntryPoint
class GameActivity : ComponentActivity(), GameMenuActions {

    @Inject lateinit var catalog: SystemCatalog
    @Inject lateinit var coreRepository: CoreRepository
    @Inject lateinit var romPreparer: RomPreparer
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var allSaves: SaveStateStore

    /** Saves do perfil ativo, fixado ao abrir o jogo. */
    private lateinit var saves: SaveStateStore
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var coreOptionsCache: CoreOptionsCache
    @Inject lateinit var dat: com.retroplay.domain.library.DatLookup

    /** Escopo que sobrevive ao finish(): grava estatísticas e saves mesmo ao sair do jogo. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var container: FrameLayout
    private var retroView: GLRetroView? = null
    private lateinit var system: GameSystem
    private lateinit var fileName: String
    private var coreId = ""
    private var gameRunning = false
    private var sramFile: File? = null
    private var gameId: Long = -1
    private var sessionStart = 0L
    private var exiting = false
    private var isArcade = false
    private var arcadeNeoGeo = false

    // ---- controle na tela (celular)
    private var padSpec by mutableStateOf<com.retroplay.feature.game.touch.VirtualPadSpec?>(null)
    private var padVisible by mutableStateOf(false)
    private var padMode = "auto"
    private var padOpacity by mutableFloatStateOf(0.6f)
    private var padHaptics = true
    private var padState = com.retroplay.feature.game.touch.PadState()
    private var padType: com.retroplay.domain.settings.PadType? = null

    // Entrada (reconfigurada pelas Configurações em start()).
    private var router = InputRouter()
    private val ports = PortAssigner()
    private var hotkeys = HotkeyDetector()
    private var hotkeysEnabled = true
    private var analogToDpad = true
    private var deadzone = 0.5f
    private var turboButtons: Set<Int> = emptySet()
    private val turboJobs = HashMap<Pair<Int, Int>, Job>()
    private var portsByDescriptor: Map<String, Int> = emptyMap()
    private var gameKey = ""

    // Preferências da sessão
    private var autoSave = true
    private var muteInMenu = true
    private var audioOn = true
    private var ffHoldMode = false
    private var slotCount = 9

    // ---- estado observado pela UI Compose sobre o jogo
    private var menu by mutableStateOf<GameMenuState?>(null)
    private var fps by mutableIntStateOf(0)
    private var showFps by mutableStateOf(false)
    private var toast by mutableStateOf<String?>(null)

    private var speed = 1
    private var lastFastSpeed = 2
    private var shader = ShaderChoice.DEFAULT
    private var aspect: AspectChoice? = null
    private val fpsCounter = FpsCounter()

    // ---- rewind
    private var rewindEnabled = false
    private var rewind = RewindBuffer(REWIND_BUDGET_BYTES)
    private var rewindJob: Job? = null
    private var rewinding = false

    /** Última miniatura capturada (ao abrir o menu), usada nos saves. */
    @Volatile private var lastThumbnail: ByteArray? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.retroplay.diagnostics.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(container)
        hideSystemBars()
        applyPhoneWindow()

        val sys = intent.getStringExtra(GameLauncher.EXTRA_SYSTEM)?.let { catalog.byId(SystemId(it)) }
        val core = sys?.cores?.firstOrNull { it.id == intent.getStringExtra(GameLauncher.EXTRA_CORE) }
        val romUri = intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH)
        if (sys == null || core == null || romUri == null) {
            showErrorAndExit(getString(R.string.game_error_generic))
            return
        }
        system = sys
        coreId = core.id
        gameKey = GameKey.of(sys.id, intent.getStringExtra(GameLauncher.EXTRA_FILE_NAME) ?: romUri.substringAfterLast('/'))
        fileName = intent.getStringExtra(GameLauncher.EXTRA_FILE_NAME) ?: romUri.substringAfterLast('/')
        gameId = intent.getLongExtra(GameLauncher.EXTRA_GAME_ID, -1)
        aspect = if (CoreAspect.forCore(coreId) != null) AspectChoice.ORIGINAL else null
        val loadSlot = intent.getIntExtra(GameLauncher.EXTRA_LOAD_SLOT, -1)
        lifecycleScope.launch { start(romUri, loadSlot) }
    }

    private suspend fun start(romUri: String, loadSlot: Int) {
        val core = system.cores.first { it.id == coreId }
        val installed = coreRepository.installed(core)
        if (installed == null) {
            showErrorAndExit(getString(R.string.game_error_core_missing, core.displayName))
            return
        }
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        isArcade = system.identification == IdentificationStrategy.ARCADE_ROMSET
        val warnings = mutableListOf<ArcadeWarning>()
        val prepared = try {
            if (isArcade) {
                // Neo Geo: BIOS e romset "pai" precisam estar onde o FBNeo procura.
                val check = withContext(Dispatchers.IO) {
                    ArcadeLaunch.check(system, fileName, dat, library.observeGames(system.id).first(), library.observeBios().first(), coreId)
                }
                when (check) {
                    ArcadeCheck.BiosMissing -> { showErrorAndExit(getString(R.string.neogeo_bios_missing)); return }
                    is ArcadeCheck.ParentMissing -> { showErrorAndExit(getString(R.string.neogeo_parent_missing, check.parent + ".zip")); return }
                    is ArcadeCheck.Ready -> withContext(Dispatchers.IO) {
                        warnings += check.warnings
                        arcadeNeoGeo = check.neoGeo
                        check.bios?.let { romPreparer.stageBios(it.uri, systemDir) }
                        val rom = romPreparer.prepareArcade(romUri, fileName, check.companions)
                        // Os companheiros ficam sempre ao lado do romset (mesma pasta ou pasta montada no cache).
                        val needed = romPreparer.uncompressedBytes(listOf(rom) + check.companions.map { File(rom.parentFile, it.fileName) })
                        val mem = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
                        if (ArcadeLaunch.isLowMemory(needed, mem.availMem)) warnings += ArcadeWarning.LOW_MEMORY
                        rom
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    romPreparer.prepare(romUri, fileName, system.extensions, extractArchives = true)
                }
            }
        } catch (e: RomPrepareException) {
            showErrorAndExit(
                getString(
                    when (e.reason) {
                        RomPrepareException.Reason.NOT_FOUND -> R.string.rom_error_not_found
                        RomPrepareException.Reason.CORRUPTED -> R.string.rom_error_corrupted
                        RomPrepareException.Reason.NO_ROM_INSIDE -> R.string.rom_error_no_rom_inside
                        RomPrepareException.Reason.NO_SPACE -> R.string.error_no_space
                    },
                ),
            )
            return
        }
        warnings.forEach { w ->
            flash(getString(if (w == ArcadeWarning.LOW_MEMORY) R.string.neogeo_low_memory else R.string.neogeo_bios_incomplete))
        }
        // Cada perfil tem seus saves (de bateria e save states); o perfil 1 usa as pastas originais.
        val profileId = settings.current().value(AppSettings.ACTIVE_PROFILE).toLongOrNull() ?: Profile.DEFAULT_ID
        saves = allSaves.forProfile(profileId)
        val savesDir = File(filesDir, listOfNotNull("saves", Profile.dirName(profileId), system.id.value).joinToString("/")).apply { mkdirs() }
        val sram = File(savesDir, fileName.substringBeforeLast('.') + ".srm").also { sramFile = it }
        val sramBytes = withContext(Dispatchers.IO) { sram.takeIf { it.isFile }?.readBytes() }
        val snap = settings.current()
        applySettings(snap)

        val data = GLRetroViewData(this).apply {
            coreFilePath = installed.libraryPath
            gameFilePath = prepared.absolutePath
            systemDirectory = systemDir.absolutePath
            savesDirectory = savesDir.absolutePath
            saveRAMState = sramBytes
            shader = shaderConfig(this@GameActivity.shader)
            preferLowLatencyAudio = snap.value(AppSettings.AUDIO_LATENCY) == "low"
            rumbleEventsEnabled = false
            // "Modo de baixo consumo": não redesenha quadros repetidos.
            skipDuplicateFrames = snap.value(AppSettings.LOW_POWER)
            variables = coreVariables(snap)
        }
        val view = GLRetroView(this, data).apply { isFocusable = false }
        retroView = view
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        container.addView(overlay(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lifecycle.addObserver(view)

        lifecycleScope.launch {
            view.getGLRetroErrors().collect { code -> showErrorAndExit(getString(errorMessage(code))) }
        }
        lifecycleScope.launch {
            view.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().first()
            gameRunning = true
            sessionStart = SystemClock.elapsedRealtime()
            padType?.let { applyPadType(view, it) }
            view.audioEnabled = audioOn
            // Guarda a lista de opções do núcleo para a tela de Configurações.
            ioScope.launch { runCatching { coreOptionsCache.save(coreId, currentCoreOptions()) } }
            if (gameId > 0) ioScope.launch { library.recordPlayStarted(gameId, System.currentTimeMillis()) }
            // "Continuar do save": carrega o estado pedido; senão, auto-load (se ligado).
            when {
                loadSlot >= 0 -> loadState(loadSlot, showMessage = false)
                snap.value(AppSettings.AUTO_LOAD, system.id, gameKey) && saves.read(system.id, fileName, SaveSlot.AUTO) != null ->
                    loadState(SaveSlot.AUTO, showMessage = false)
            }
        }
        lifecycleScope.launch {
            var lastUpdate = 0L
            view.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().collect {
                val now = System.nanoTime()
                val value = fpsCounter.onFrame(now)
                if (showFps && now - lastUpdate > 500_000_000L) {
                    fps = value
                    lastUpdate = now
                }
            }
        }
    }

    /** Aplica as configurações (global → console → jogo) a esta sessão. */
    private fun applySettings(snap: SettingsSnapshot) {
        val sys = system.id
        shader = when (snap.value(AppSettings.SHADER, sys, gameKey)) {
            "crt" -> ShaderChoice.CRT
            "lcd" -> ShaderChoice.LCD
            "sharp" -> ShaderChoice.SHARP
            "smooth" -> ShaderChoice.SMOOTH
            else -> ShaderChoice.DEFAULT
        }
        aspect = if (CoreAspect.forCore(coreId) == null) null
        else if (snap.value(AppSettings.ASPECT, sys, gameKey) == "pixel") AspectChoice.PIXEL_PERFECT else AspectChoice.ORIGINAL
        showFps = snap.value(AppSettings.SHOW_FPS)
        audioOn = snap.value(AppSettings.AUDIO_ENABLED, sys, gameKey)
        muteInMenu = snap.value(AppSettings.MUTE_IN_MENU)
        val custom = MappingCodec.decode(snap.value(AppSettings.MAPPING, sys, gameKey))
        router = InputRouter(
            gamepadMap = MappingCodec.gamepadMap(custom, snap.value(AppSettings.NINTENDO_LAYOUT)),
            remoteMap = if (snap.value(AppSettings.REMOTE_AS_GAMEPAD)) InputRouter.DEFAULT_REMOTE else emptyMap(),
        )
        turboButtons = MappingCodec.decodeTurbo(snap.value(AppSettings.TURBO, sys, gameKey)).map { RetroPadKeys.keyCodeFor(it) }.toSet()
        analogToDpad = snap.value(AppSettings.ANALOG_TO_DPAD)
        deadzone = snap.value(AppSettings.DEADZONE).toInt() / 100f
        hotkeysEnabled = snap.value(AppSettings.HOTKEYS)
        hotkeys = HotkeyDetector(
            modifier = if (snap.value(AppSettings.HOTKEY_MODIFIER) == "l3") KeyEvent.KEYCODE_BUTTON_THUMBL else KeyEvent.KEYCODE_BUTTON_SELECT,
        )
        portsByDescriptor = MappingCodec.decodePorts(snap.value(AppSettings.PORTS))
        lastFastSpeed = snap.value(AppSettings.FF_SPEED, sys, gameKey).toInt()
        ffHoldMode = snap.value(AppSettings.FF_MODE) == "hold"
        rewindEnabled = snap.value(AppSettings.REWIND, sys, gameKey)
        rewind = RewindBuffer(snap.value(AppSettings.REWIND_MB).toLong() * 1024 * 1024)
        autoSave = snap.value(AppSettings.AUTO_SAVE, sys, gameKey)
        slotCount = snap.value(AppSettings.SLOTS).toInt()
        padMode = snap.value(AppSettings.TOUCH_PAD)
        padOpacity = snap.value(AppSettings.TOUCH_PAD_OPACITY).toInt() / 100f
        padHaptics = snap.value(AppSettings.TOUCH_HAPTICS)
        val sixButtons = snap.value(com.retroplay.domain.settings.ConsoleSettings.MD_PAD, sys, gameKey) == "6"
        padSpec = com.retroplay.feature.game.touch.VirtualPadSpecs.forSystem(system, sixButtons, arcadeNeoGeo)
        padVisible = when (padMode) {
            "always" -> true
            "never" -> false
            else -> !DeviceKind.isTv(this) && DeviceKind.hasTouchscreen(this) && !DeviceKind.hasGamepad()
        }
    }

    /** Estado novo do controle na tela: aperta/solta só o que mudou e vibra ao apertar. */
    private fun onPadState(next: com.retroplay.feature.game.touch.PadState) {
        val view = retroView ?: return
        val prev = padState
        padState = next
        var pressedNew = false
        (next.buttons - prev.buttons).forEach { view.sendKeyEvent(KeyEvent.ACTION_DOWN, RetroPadKeys.keyCodeFor(it), 0); pressedNew = true }
        (prev.buttons - next.buttons).forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, RetroPadKeys.keyCodeFor(it), 0) }
        if (next.dx != prev.dx || next.dy != prev.dy) {
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, next.dx.toFloat(), next.dy.toFloat(), 0)
            if (next.dx != 0 || next.dy != 0) pressedNew = true
        }
        if (pressedNew && padHaptics) container.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        if (next.menu && !prev.menu) {
            releasePad()
            openMenu()
        }
    }

    /** Solta tudo (ao abrir o menu, esconder o controle ou sair). */
    private fun releasePad() {
        val view = retroView
        if (view != null) {
            padState.buttons.forEach { view.sendKeyEvent(KeyEvent.ACTION_UP, RetroPadKeys.keyCodeFor(it), 0) }
            if (padState.dx != 0 || padState.dy != 0) view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, 0)
        }
        padState = com.retroplay.feature.game.touch.PadState()
    }

    /** Modo automático: controle físico em uso esconde o da tela. */
    private fun onPhysicalInput() {
        if (padMode == "auto" && padVisible) {
            releasePad()
            padVisible = false
        }
    }

    /**
     * Variáveis do núcleo: opções do console (modo do Neo Geo, região, FM...), depois as opções
     * de núcleo editadas à mão (global < console < jogo), que prevalecem, e a proporção.
     */
    private fun coreVariables(snap: SettingsSnapshot): Array<Variable> {
        val prefix = AppSettings.coreOptionPrefix(coreId)
        val setup = ConsoleCoreOptions.setup(system.id, coreId, snap, gameKey, if (isArcade) ArcadeLaunch.romsetName(fileName) else null)
        padType = setup.padType
        val merged = LinkedHashMap<String, String>(setup.variables)
        listOf(ScopeRef.Global, ScopeRef.system(system.id), ScopeRef.game(gameKey)).forEach { scope ->
            snap.keysIn(scope, prefix).forEach { (k, v) -> merged[k.removePrefix(prefix)] = v }
        }
        CoreAspect.forCore(coreId)?.let { opt ->
            if (aspect == AspectChoice.PIXEL_PERFECT) merged[opt.key] = opt.pixelPerfect
        }
        return merged.map { (k, v) -> Variable(k, v) }.toTypedArray()
    }

    /** Mega Drive no genesis_plus_gx: controle de 3 ou 6 botões nas portas 1 e 2. */
    private fun applyPadType(view: GLRetroView, type: com.retroplay.domain.settings.PadType) {
        val wanted = ConsoleCoreOptions.padDescription(type)
        val controllers = runCatching { view.getControllers() }.getOrNull().orEmpty()
        for (port in 0 until minOf(2, maxOf(controllers.size, 2))) {
            val id = controllers.getOrNull(port)?.firstOrNull { it.description == wanted }?.id ?: ConsoleCoreOptions.padFallbackId(type)
            runCatching { view.setControllerType(port, id) }
        }
    }

    private fun currentCoreOptions(): List<CoreOption> =
        retroView?.getVariables().orEmpty().mapNotNull { v ->
            val key = v.key ?: return@mapNotNull null
            CoreOption.parse(key, v.description.orEmpty(), v.value)
        }

    private fun shaderConfig(choice: ShaderChoice): ShaderConfig = when (choice) {
        ShaderChoice.DEFAULT -> ShaderConfig.Default
        ShaderChoice.CRT -> ShaderConfig.CRT
        ShaderChoice.LCD -> ShaderConfig.LCD
        ShaderChoice.SHARP -> ShaderConfig.Sharp
        ShaderChoice.SMOOTH -> ShaderConfig.CUT2()
    }

    /** Camada Compose: menu, FPS e avisos curtos. */
    private fun overlay() = ComposeView(this).apply {
        setContent {
            RetroPlayTheme {
                TvCanvas {
                Box(Modifier.fillMaxSize()) {
                    val spec = padSpec
                    if (spec != null && menu == null) {
                        if (padVisible) {
                            com.retroplay.feature.game.touch.VirtualPadOverlay(spec, padOpacity, androidx.tv.material3.MaterialTheme.colorScheme.primary, ::onPadState)
                        } else if (padMode == "auto" && DeviceKind.hasTouchscreen(this@GameActivity)) {
                            // Tocou na tela com o controle escondido: ele volta.
                            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { padVisible = true }) })
                        }
                    }
                    if (showFps && menu == null) {
                        Text("$fps FPS", color = Color.Yellow, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                    }
                    toast?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) }
                    menu?.let { GameMenu(it, this@GameActivity) }
                }
                }
            }
        }
    }

    private fun flash(message: String) {
        toast = message
        lifecycleScope.launch {
            delay(2_000)
            if (toast == message) toast = null
        }
    }

    private fun errorMessage(code: Int): Int = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY -> R.string.game_error_load_library
        // Neo Geo: quase sempre romset incompleto ou de outra versão (não é a do FBNeo).
        GLRetroView.ERROR_LOAD_GAME -> if (isArcade) R.string.neogeo_load_failed else R.string.game_error_load_game
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> R.string.game_error_gl
        else -> R.string.game_error_generic
    }

    // ================================================================== menu

    private fun openMenu() {
        val view = retroView ?: return
        if (menu != null || !gameRunning) return
        releasePad()
        stopRewind()
        // Captura a tela do jogo antes de pausar (vira a miniatura dos saves).
        captureThumbnail { lastThumbnail = it }
        setPaused(true)
        hotkeys.reset()
        menu = GameMenuState(title = intent.getStringExtra(GameLauncher.EXTRA_TITLE) ?: fileName, system = system)
        refreshMenu()
        view.requestRender()
    }

    private fun refreshMenu(page: MenuPage? = null) {
        val current = menu ?: return
        menu = current.copy(
            page = page ?: current.page,
            slots = saves.list(system.id, fileName),
            speed = speed,
            rewindEnabled = rewindEnabled,
            rewindAvailable = rewind.size > 0,
            shader = shader,
            aspect = aspect,
            showFps = showFps,
            slotCount = slotCount,
            coreOptions = if ((page ?: current.page) == MenuPage.CORE_OPTIONS) {
                runCatching { currentCoreOptions() }.getOrDefault(emptyList())
            } else current.coreOptions,
        )
    }

    override fun resume() {
        menu = null
        setPaused(false)
    }

    override fun openPage(page: MenuPage) = refreshMenu(page)

    override fun save(slot: Int) {
        val thumb = lastThumbnail
        ioScope.launch {
            val ok = writeState(slot, thumb, onEmulationThread = true)
            withContext(Dispatchers.Main) {
                flash(getString(if (ok) R.string.saved_slot else R.string.save_failed, slot))
                refreshMenu(MenuPage.MAIN)
            }
        }
    }

    override fun load(slot: Int) {
        lifecycleScope.launch {
            loadState(slot, showMessage = true)
            resume()
        }
    }

    private suspend fun loadState(slot: Int, showMessage: Boolean) {
        val view = retroView ?: return
        val ok = withContext(Dispatchers.IO) {
            val bytes = saves.read(system.id, fileName, slot) ?: return@withContext false
            runCatching { view.unserializeState(bytes) }.getOrDefault(false)
        }
        if (showMessage || !ok) flash(getString(if (ok) R.string.loaded_slot else R.string.load_failed, slot))
        if (ok) rewind.clear()
    }

    /** Serializa o estado e grava; [onEmulationThread]=false só com a emulação parada. */
    private fun writeState(slot: Int, thumbnail: ByteArray?, onEmulationThread: Boolean): Boolean {
        val view = retroView ?: return false
        return runCatching {
            val state = view.serializeState(useEmulationThread = onEmulationThread)
            if (state.isEmpty()) return false
            saves.write(system.id, fileName, slot, state, thumbnail)
            true
        }.onFailure { Log.e(TAG, "Falha ao salvar estado", it) }.getOrDefault(false)
    }

    override fun reset() {
        retroView?.reset()
        rewind.clear()
        resume()
    }

    override fun insertCoin() {
        resume()
        val view = retroView ?: return
        lifecycleScope.launch {
            delay(150)
            view.sendKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_SELECT, 0)
            delay(120)
            view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_SELECT, 0)
        }
    }

    override fun cycleSpeed() {
        speed = when (speed) { 1 -> 2; 2 -> 4; 4 -> 8; else -> 1 }
        if (speed > 1) lastFastSpeed = speed
        retroView?.frameSpeed = speed
        refreshMenu()
    }

    override fun toggleRewind() {
        rewindEnabled = !rewindEnabled
        if (!rewindEnabled) rewind.clear()
        refreshMenu()
    }

    override fun rewindSeconds() {
        val state = rewind.rewind(REWIND_CAPTURES_PER_SECOND * 5) ?: return
        val view = retroView ?: return
        ioScope.launch {
            runCatching { view.unserializeState(state) }
            withContext(Dispatchers.Main) { resume() }
        }
    }

    override fun cycleShader() {
        shader = ShaderChoice.entries[(shader.ordinal + 1) % ShaderChoice.entries.size]
        retroView?.shader = shaderConfig(shader)
        retroView?.requestRender()
        refreshMenu()
    }

    override fun cycleAspect() {
        val option = CoreAspect.forCore(coreId) ?: return
        aspect = if (aspect == AspectChoice.ORIGINAL) AspectChoice.PIXEL_PERFECT else AspectChoice.ORIGINAL
        val value = if (aspect == AspectChoice.ORIGINAL) option.original else option.pixelPerfect
        runCatching { retroView?.updateVariables(Variable(option.key, value)) }
        refreshMenu()
    }

    override fun toggleFps() {
        showFps = !showFps
        refreshMenu()
    }

    override fun screenshot() {
        val view = retroView ?: return
        capture(view.width, view.height) { bitmap ->
            if (bitmap == null) {
                flash(getString(R.string.screenshot_failed))
                return@capture
            }
            ioScope.launch {
                val ok = runCatching { saveScreenshot(bitmap) }.getOrDefault(false)
                withContext(Dispatchers.Main) { flash(getString(if (ok) R.string.screenshot_saved else R.string.screenshot_failed)) }
            }
        }
    }

    /** Opção do núcleo (menu do jogo): vale na hora e fica gravada para este jogo. */
    override fun setCoreOption(key: String, value: String) {
        runCatching { retroView?.updateVariables(Variable(key, value)) }
        ioScope.launch { settings.setRaw(AppSettings.coreOptionKey(coreId, key), ScopeRef.game(gameKey), value) }
        val current = menu ?: return
        menu = current.copy(coreOptions = current.coreOptions.map { if (it.key == key) it.copy(current = value) else it })
    }

    override fun exit() {
        exiting = true
        val thumb = lastThumbnail
        ioScope.launch {
            if (autoSave) writeState(SaveSlot.AUTO, thumb, onEmulationThread = true)
            withContext(Dispatchers.Main) {
                // "Sair" leva direto à tela Início (não à página de onde o jogo foi aberto).
                startActivity(
                    android.content.Intent(this@GameActivity, com.retroplay.MainActivity::class.java)
                        .putExtra(com.retroplay.MainActivity.EXTRA_GO_HOME, true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                finish()
            }
        }
    }

    private fun setPaused(paused: Boolean) {
        val view = retroView ?: return
        // Congela a emulação (o GL para de chamar step) e silencia o áudio.
        view.renderMode = if (paused) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view.audioEnabled = audioOn && !(paused && muteInMenu)
    }

    // ================================================================== rewind / acelerar

    private fun startRewindCapture() {
        if (rewindJob != null) return
        rewindJob = ioScope.launch {
            while (isActive) {
                delay(1000L / REWIND_CAPTURES_PER_SECOND)
                val view = retroView ?: continue
                if (!rewindEnabled || menu != null || !gameRunning) continue
                if (rewinding) {
                    rewind.pop()?.let { runCatching { view.unserializeState(it) } } ?: run { rewinding = false }
                } else {
                    runCatching { view.serializeState() }.getOrNull()?.let(rewind::push)
                }
            }
        }
    }

    private fun stopRewind() {
        rewinding = false
    }

    private fun onHotkey(h: Hotkey) {
        when (h) {
            Hotkey.MENU -> openMenu()
            Hotkey.FAST_FORWARD -> {
                speed = if (ffHoldMode || speed == 1) lastFastSpeed else 1
                retroView?.frameSpeed = speed
                flash(if (speed == 1) getString(R.string.speed_normal) else "${speed}x")
            }
            Hotkey.FAST_FORWARD_RELEASE -> if (ffHoldMode) {
                speed = 1
                retroView?.frameSpeed = 1
            }
            Hotkey.REWIND_START -> if (rewindEnabled) rewinding = true else flash(getString(R.string.rewind_disabled_hint))
            Hotkey.REWIND_STOP -> rewinding = false
            Hotkey.QUICK_SAVE -> {
                captureThumbnail { thumb ->
                    val ok = writeState(QUICK_SLOT, thumb, onEmulationThread = true)
                    lifecycleScope.launch { flash(getString(if (ok) R.string.saved_slot else R.string.save_failed, QUICK_SLOT)) }
                }
            }
            Hotkey.QUICK_LOAD -> lifecycleScope.launch { loadState(QUICK_SLOT, showMessage = true) }
        }
    }

    // ================================================================== entrada

    // dispatchKeyEvent é API pública de Activity; o androidx marca o override como @RestrictTo
    // (falso positivo conhecido). Usamos dispatchKeyEvent para receber as teclas antes de qualquer View.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = retroView ?: return super.dispatchKeyEvent(event)
        val fromGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        if (fromGamepad) onPhysicalInput()
        if (menu != null) return super.dispatchKeyEvent(translateForMenu(event))
        if (fromGamepad && hotkeysEnabled && event.repeatCount == 0) {
            val hotkey = hotkeys.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN)
            if (hotkey != null) {
                // Solta Select e o botão do atalho no núcleo, para não ficarem "presos".
                val port = ports.portFor(event.deviceId, true)
                view.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_SELECT, port)
                (router.route(event.keyCode, true) as? InputAction.Forward)?.let { view.sendKeyEvent(KeyEvent.ACTION_UP, it.retroKeyCode, port) }
                onHotkey(hotkey)
                return true
            }
        }
        return when (val action = router.route(event.keyCode, fromGamepad)) {
            is InputAction.Forward -> {
                if (event.repeatCount == 0) {
                    val port = portFor(event.deviceId, fromGamepad)
                    if (action.retroKeyCode in turboButtons) turbo(view, action.retroKeyCode, port, event.action == KeyEvent.ACTION_DOWN)
                    else view.sendKeyEvent(event.action, action.retroKeyCode, port)
                }
                true
            }
            InputAction.Menu -> {
                // Age no "soltar" para o evento não vazar para o menu recém-aberto.
                if (event.action == KeyEvent.ACTION_UP) openMenu()
                true
            }
            InputAction.Ignore -> super.dispatchKeyEvent(event)
        }
    }

    /** Porta do jogador: a escolhida nas Configurações para este controle, ou a ordem de uso. */
    private fun portFor(deviceId: Int, isGamepad: Boolean): Int {
        if (isGamepad && !ports.has(deviceId)) {
            InputDevice.getDevice(deviceId)?.descriptor?.let { portsByDescriptor[it] }?.let { ports.assign(deviceId, it) }
        }
        return ports.portFor(deviceId, isGamepad)
    }

    /** Turbo: enquanto o botão está pressionado, aperta e solta ~10 vezes por segundo. */
    private fun turbo(view: GLRetroView, key: Int, port: Int, down: Boolean) {
        val id = key to port
        turboJobs.remove(id)?.cancel()
        if (!down) {
            view.sendKeyEvent(KeyEvent.ACTION_UP, key, port)
            return
        }
        turboJobs[id] = lifecycleScope.launch {
            var pressed = false
            while (isActive) {
                pressed = !pressed
                view.sendKeyEvent(if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key, port)
                delay(TURBO_HALF_PERIOD_MS)
            }
        }
    }

    /** No menu, o gamepad navega: A = OK, B = Voltar. */
    private fun translateForMenu(e: KeyEvent): KeyEvent {
        val code = when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_MODE -> KeyEvent.KEYCODE_BACK
            else -> return e
        }
        return KeyEvent(e.downTime, e.eventTime, e.action, code, e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, InputDevice.SOURCE_KEYBOARD)
    }

    private var hatX = 0f
    private var hatY = 0f

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = retroView
        if (view != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            onPhysicalInput()
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val dx = StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_X), x, deadzone, analogToDpad)
            val dy = StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_Y), y, deadzone, analogToDpad)
            if (menu != null) {
                // Direcional/analógico do gamepad também navega no menu.
                hatToKey(hatX, dx, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
                hatToKey(hatY, dy, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
                hatX = dx; hatY = dy
                return true
            }
            val port = portFor(event.deviceId, isGamepad = true)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dx, dy, port)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, port)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ), port)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    @SuppressLint("RestrictedApi")
    private fun hatToKey(old: Float, new: Float, negative: Int, positive: Int) {
        if (old == new) return
        fun send(code: Int, action: Int) = super.dispatchKeyEvent(KeyEvent(action, code))
        if (old < 0) send(negative, KeyEvent.ACTION_UP)
        if (old > 0) send(positive, KeyEvent.ACTION_UP)
        if (new < 0) send(negative, KeyEvent.ACTION_DOWN)
        if (new > 0) send(positive, KeyEvent.ACTION_DOWN)
    }

    // ================================================================== ciclo de vida

    override fun onResume() {
        super.onResume()
        if (gameRunning) sessionStart = SystemClock.elapsedRealtime()
        startRewindCapture()
        // Ao voltar (ex.: depois do Home), abre o menu em vez de retomar de surpresa.
        if (gameRunning && menu == null && wasPausedBySystem) openMenu()
        wasPausedBySystem = false
    }

    private var wasPausedBySystem = false

    override fun onPause() {
        super.onPause()
        rewindJob?.cancel()
        rewindJob = null
        saveSram()
        if (!gameRunning) return
        if (!exiting) {
            // Botão Home / perda de foco: save automático (a emulação já está parada aqui).
            wasPausedBySystem = true
            if (autoSave) {
                writeState(SaveSlot.AUTO, lastThumbnail, onEmulationThread = false)
                captureThumbnail { thumb -> thumb?.let { saves.writeThumbnail(system.id, fileName, SaveSlot.AUTO, it) } }
            }
        }
        if (gameId > 0 && sessionStart > 0) {
            val played = SystemClock.elapsedRealtime() - sessionStart
            sessionStart = 0
            ioScope.launch { library.addPlayTime(gameId, played) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /**
     * A emulação já está pausada aqui (o GLRetroView reage ao ON_PAUSE antes do onPause),
     * então lemos a SRAM direto, sem passar pela thread de emulação (evita travar).
     */
    private fun saveSram() {
        val view = retroView ?: return
        val target = sramFile ?: return
        if (!gameRunning) return
        runCatching {
            val bytes = view.serializeSRAM(useEmulationThread = false)
            if (bytes.isNotEmpty()) {
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            }
        }.onFailure { Log.e(TAG, "Falha ao salvar SRAM", it) }
    }

    // ================================================================== captura de tela

    /** Captura a tela do jogo e devolve um PNG 4:3 pequeno; [onDone] roda numa thread de fundo. */
    private fun captureThumbnail(onDone: (ByteArray?) -> Unit) {
        capture(THUMB_W, THUMB_H) { bitmap ->
            ioScope.launch {
                if (bitmap == null) return@launch onDone(null)
                // Recorta o centro 4:3 (o jogo fica no meio da tela 16:9) e comprime fora da thread principal.
                val w = bitmap.height * 4 / 3
                val crop = Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, 0, w, bitmap.height)
                onDone(ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.PNG, 90, it) }.toByteArray())
            }
        }
    }

    private fun capture(width: Int, height: Int, onDone: (Bitmap?) -> Unit) {
        val view = retroView
        if (view == null || view.width == 0 || width <= 0 || height <= 0) return onDone(null)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(view, bitmap, { result -> onDone(if (result == PixelCopy.SUCCESS) bitmap else null) }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            onDone(null)
        }
    }

    /** Screenshot em Imagens/Onzetec Games (MediaStore, sem permissão no Android 10+). */
    private fun saveScreenshot(bitmap: Bitmap): Boolean {
        val name = "${fileName.substringBeforeLast('.')}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Onzetec Games")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
            return true
        }
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showErrorAndExit(message: String) {
        if (isFinishing) return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.game_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private companion object {
        const val TAG = "GameActivity"
        const val THUMB_W = 384
        const val THUMB_H = 216
        const val QUICK_SLOT = 9
        const val TURBO_HALF_PERIOD_MS = 50L
        const val REWIND_CAPTURES_PER_SECOND = 6
        /** Orçamento padrão do rewind (Configurações → Emulação na Fase 7). */
        const val REWIND_BUDGET_BYTES = 32L * 1024 * 1024
    }
}
