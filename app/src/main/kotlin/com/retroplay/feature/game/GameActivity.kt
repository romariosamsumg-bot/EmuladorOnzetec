package com.retroplay.feature.game

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.retroplay.R
import com.retroplay.data.rom.RomPrepareException
import com.retroplay.data.rom.RomPreparer
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemId
import com.retroplay.feature.game.input.InputAction
import com.retroplay.feature.game.input.InputRouter
import com.retroplay.feature.game.input.PortAssigner
import com.retroplay.feature.game.input.StickToDpad
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Tela de jogo: GLRetroView em tela cheia, entrada de gamepad/controle remoto e
 * salvamento da SRAM (saves "de bateria") ao pausar/sair.
 * Menu rápido, save states e acelerar/rewind chegam na Fase 6.
 */
@AndroidEntryPoint
class GameActivity : ComponentActivity() {

    @Inject lateinit var catalog: SystemCatalog
    @Inject lateinit var coreRepository: CoreRepository
    @Inject lateinit var romPreparer: RomPreparer

    private lateinit var container: FrameLayout
    private var retroView: GLRetroView? = null
    private var gameRunning = false
    private var sramFile: File? = null
    private val router = InputRouter()
    private val ports = PortAssigner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        setContentView(container)
        hideSystemBars()

        val system = intent.getStringExtra(GameLauncher.EXTRA_SYSTEM)?.let { catalog.byId(SystemId(it)) }
        val core = system?.cores?.firstOrNull { it.id == intent.getStringExtra(GameLauncher.EXTRA_CORE) }
        val romPath = intent.getStringExtra(GameLauncher.EXTRA_ROM_PATH)
        if (system == null || core == null || romPath == null) {
            showErrorAndExit(getString(R.string.game_error_generic))
            return
        }
        lifecycleScope.launch { start(system, core.id, File(romPath)) }
    }

    private suspend fun start(system: GameSystem, coreId: String, rom: File) {
        val core = system.cores.first { it.id == coreId }
        val installed = coreRepository.installed(core)
        if (installed == null) {
            showErrorAndExit(getString(R.string.game_error_core_missing, core.displayName))
            return
        }
        val prepared = try {
            withContext(Dispatchers.IO) { romPreparer.prepare(rom, system.extensions) }
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
        val sram = File(savesDir, rom.nameWithoutExtension + ".srm").also { sramFile = it }
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
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER),
        )
        lifecycle.addObserver(view)

        lifecycleScope.launch {
            view.getGLRetroErrors().collect { code -> showErrorAndExit(getString(errorMessage(code))) }
        }
        lifecycleScope.launch {
            view.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().first()
            gameRunning = true
        }
    }

    private fun errorMessage(code: Int): Int = when (code) {
        GLRetroView.ERROR_LOAD_LIBRARY -> R.string.game_error_load_library
        GLRetroView.ERROR_LOAD_GAME -> R.string.game_error_load_game
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> R.string.game_error_gl
        else -> R.string.game_error_generic
    }

    // ---------------------------------------------------------------- entrada

    // dispatchKeyEvent é API pública de Activity; o androidx marca o override como @RestrictTo
    // (falso positivo conhecido). Usamos dispatchKeyEvent para receber as teclas antes de qualquer View.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val view = retroView ?: return super.dispatchKeyEvent(event)
        val fromGamepad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        return when (val action = router.route(event.keyCode, fromGamepad)) {
            is InputAction.Forward -> {
                if (event.repeatCount == 0) {
                    view.sendKeyEvent(event.action, action.retroKeyCode, ports.portFor(event.deviceId, fromGamepad))
                }
                true
            }
            InputAction.Menu -> {
                // Age no "soltar" para o evento não vazar para a tela anterior.
                if (event.action == KeyEvent.ACTION_UP) finish()
                true
            }
            InputAction.Ignore -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val view = retroView
        if (view != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val port = ports.portFor(event.deviceId, isGamepad = true)
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_DPAD,
                StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_X), x),
                StickToDpad.combine(event.getAxisValue(MotionEvent.AXIS_HAT_Y), y),
                port,
            )
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, port)
            view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ),
                port,
            )
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ---------------------------------------------------------------- ciclo de vida

    override fun onPause() {
        super.onPause()
        saveSram()
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
    }
}
