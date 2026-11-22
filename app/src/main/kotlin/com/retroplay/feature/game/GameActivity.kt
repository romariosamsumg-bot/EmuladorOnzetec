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
    @Inject lateinit var saves: SaveStateStore

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

    private val router = InputRouter()
    private val ports = PortAssigner()
    private val hotkeys = HotkeyDetector()

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
    private val rewind = RewindBuffer(REWIND_BUDGET_BYTES)
    private var rewindJob: Job? = null
    private var rewinding = false

    /** Última miniatura capturada (ao abrir o menu), usada nos saves. */
    @Volatile private var lastThumbnail: ByteArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(container)
        hideSystemBars()

        val sys = intent.getStringExtra(GameLauncher.EXTRA_SYSTEM)?.let { catalog.byId(SystemId(it)) }
        val core = sys?.cores?.firstOrNull { it.id == intent.getStringExtra(GameLauncher.EXTRA_CORE) }
        val romUri = intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH)
        if (sys == null || core == null || romUri == null) {
            showErrorAndExit(getString(R.string.game_error_generic))
            return
        }
        system = sys
        coreId = core.id
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
        val prepared = try {
            withContext(Dispatchers.IO) {
                romPreparer.prepare(
                    romUri, fileName, system.extensions,
                    // Romsets de arcade são lidos pelo núcleo como estão (nunca extraídos).
                    extractArchives = system.identification == IdentificationStrategy.CONTENT_HASH,
                )
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
        val savesDir = File(filesDir, "saves/${system.id.value}").apply { mkdirs() }
        val systemDir = File(filesDir, "system").apply { mkdirs() }
        val sram = File(savesDir, fileName.substringBeforeLast('.') + ".srm").also { sramFile = it }
        val sramBytes = withContext(Dispatchers.IO) { sram.takeIf { it.isFile }?.readBytes() }

        val data = GLRetroViewData(this).apply {
            coreFilePath = installed.libraryPath
            gameFilePath = prepared.absolutePath
            systemDirectory = systemDir.absolutePath
            savesDirectory = savesDir.absolutePath
            saveRAMState = sramBytes
            shader = ShaderConfig.Default
            preferLowLatencyAudio = true
            rumbleEventsEnabled = false
            skipDuplicateFrames = false
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
            if (gameId > 0) ioScope.launch { library.recordPlayStarted(gameId, System.currentTimeMillis()) }
            // "Continuar do save": carrega o estado pedido.
            if (loadSlot >= 0) loadState(loadSlot, showMessage = false)
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

    /** Camada Compose: menu, FPS e avisos curtos. */
    private fun overlay() = ComposeView(this).apply {
        setContent {
            RetroPlayTheme {
                Box(Modifier.fillMaxSize()) {
                    if (showFps && menu == null) {
                        Text("$fps FPS", color = Color.Yellow, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                    }
                    toast?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) }
                    menu?.let { GameMenu(it, this@GameActivity) }
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
        GLRetroView.ERROR_LOAD_GAME -> R.string.game_error_load_game
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> R.string.game_error_gl
        else -> R.string.game_error_generic
    }

    // ================================================================== menu

    private fun openMenu() {
        val view = retroView ?: return
        if (menu != null || !gameRunning) return
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
            coreOptions = if ((page ?: current.page) == MenuPage.CORE_OPTIONS) {
                runCatching { retroView?.getVariables().orEmpty().mapNotNull { v -> v.key?.let { it to v.value.orEmpty() } } }.getOrDefault(emptyList())
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
        retroView?.shader = when (shader) {
            ShaderChoice.DEFAULT -> ShaderConfig.Default
            ShaderChoice.CRT -> ShaderConfig.CRT
            ShaderChoice.LCD -> ShaderConfig.LCD
            ShaderChoice.SHARP -> ShaderConfig.Sharp
        }
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

    override fun exit() {
        exiting = true
        val thumb = lastThumbnail
        ioScope.launch {
            writeState(SaveSlot.AUTO, thumb, onEmulationThread = true)
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun setPaused(paused: Boolean) {
        val view = retroView ?: return
        // Congela a emulação (o GL para de chamar step) e silencia o áudio.
        view.renderMode = if (paused) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view.audioEnabled = !paused
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
                speed = if (speed == 1) lastFastSpeed else 1
                retroView?.frameSpeed = speed
                flash(if (speed == 1) getString(R.string.speed_normal) else "${speed}x")
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
        if (menu != null) return super.dispatchKeyEvent(translateForMenu(event))
        if (fromGamepad && event.repeatCount == 0) {
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
                if (event.repeatCount == 0) view.sendKeyEvent(event.action, action.retroKeyCode, ports.portFor(event.deviceId, fromGamepad))
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
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            val dx = StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_X), x)
            val dy = StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_Y), y)
            if (menu != null) {
                // Direcional/analógico do gamepad também navega no menu.
                hatToKey(hatX, dx, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
                hatToKey(hatY, dy, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
                hatX = dx; hatY = dy
                return true
            }
            val port = ports.portFor(event.deviceId, isGamepad = true)
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
            writeState(SaveSlot.AUTO, lastThumbnail, onEmulationThread = false)
            captureThumbnail { thumb -> thumb?.let { saves.writeThumbnail(system.id, fileName, SaveSlot.AUTO, it) } }
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

    /** Screenshot em Imagens/Retrô Play (MediaStore, sem permissão no Android 10+). */
    private fun saveScreenshot(bitmap: Bitmap): Boolean {
        val name = "${fileName.substringBeforeLast('.')}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Retro Play")
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
        const val REWIND_CAPTURES_PER_SECOND = 6
        /** Orçamento padrão do rewind (Configurações → Emulação na Fase 7). */
        const val REWIND_BUDGET_BYTES = 32L * 1024 * 1024
    }
}
