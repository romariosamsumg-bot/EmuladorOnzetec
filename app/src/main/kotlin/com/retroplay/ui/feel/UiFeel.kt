package com.retroplay.ui.feel

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import com.retroplay.R

/** Efeitos sonoros da interface (originais, tools/sound/build_sounds.py). */
enum class Sfx(val res: Int) {
    MOVE(R.raw.sfx_move),
    SELECT(R.raw.sfx_select),
    BACK(R.raw.sfx_back),
    BUMP(R.raw.sfx_bump),
    TOGGLE_ON(R.raw.sfx_toggle_on),
    TOGGLE_OFF(R.raw.sfx_toggle_off),
    LAUNCH(R.raw.sfx_launch),
    SUCCESS(R.raw.sfx_success),
    ERROR(R.raw.sfx_error),
}

/** Intensidade da vibração (celular) e do tremor do controle (gamepad). */
enum class Haptic { TICK, CLICK, HEAVY, ERROR }

/**
 * "Game feel" da interface: som e vibração em cada interação, em um só lugar.
 *
 * - Sons curtos via SoundPool (baixa latência, já carregados na memória: ~50 KB).
 * - Navegar com o direcional toca um "tic" quando o foco muda; na borda (não há para onde ir),
 *   um "toc" grave e um tremor leve no controle.
 * - Vibração: no celular, o motor do aparelho (efeitos prontos do Android 10+); na TV, o motor
 *   do controle que está sendo usado (quando ele tem).
 * Tudo respeita as opções de Configurações → Interface (sons, volume, vibração).
 */
object UiFeel {
    @Volatile var soundsOn = true
    @Volatile var volume = 0.5f
    @Volatile var hapticsOn = true

    private var pool: SoundPool? = null
    private val ids = IntArray(Sfx.entries.size)
    private val loaded = BooleanArray(Sfx.entries.size)
    private var appContext: Context? = null
    private var lastMoveAt = 0L

    /** Último toque de direcional (para saber se uma troca de foco veio do usuário). */
    @Volatile var lastDpadAt = 0L
    @Volatile var lastFocusAt = 0L

    /** Controle que mandou a última tecla (para o tremor sair nele). */
    @Volatile var lastDeviceId = -1

    fun init(context: Context) {
        if (pool != null) return
        appContext = context.applicationContext
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        p.setOnLoadCompleteListener { _, sampleId, status ->
            val i = ids.indexOf(sampleId)
            if (i >= 0 && status == 0) loaded[i] = true
        }
        Sfx.entries.forEach { ids[it.ordinal] = p.load(context.applicationContext, it.res, 1) }
        pool = p
    }

    fun play(sfx: Sfx, gain: Float = 1f) {
        if (!soundsOn || volume <= 0f) return
        val p = pool ?: return
        if (!loaded[sfx.ordinal]) return
        val v = (volume * gain).coerceIn(0f, 1f)
        p.play(ids[sfx.ordinal], v, v, 1, 0, 1f)
    }

    /** O foco mudou: "tic" se foi pelo direcional (e não rápido demais, ao segurar). */
    fun onFocusGained() {
        val now = SystemClock.uptimeMillis()
        lastFocusAt = now
        if (now - lastDpadAt < 300 && now - lastMoveAt > 35) {
            lastMoveAt = now
            play(Sfx.MOVE, 0.7f)
        }
    }

    /** Direcional sem troca de foco: bateu na borda. */
    fun bump() {
        play(Sfx.BUMP, 0.8f)
        haptic(Haptic.TICK)
    }

    fun select() {
        play(Sfx.SELECT)
        haptic(Haptic.CLICK)
    }

    fun back() = play(Sfx.BACK, 0.8f)

    fun toggle(on: Boolean) {
        play(if (on) Sfx.TOGGLE_ON else Sfx.TOGGLE_OFF)
        haptic(Haptic.CLICK)
    }

    fun success() {
        play(Sfx.SUCCESS)
        haptic(Haptic.CLICK)
    }

    fun error() {
        play(Sfx.ERROR)
        haptic(Haptic.ERROR)
    }

    fun launch() {
        play(Sfx.LAUNCH)
        haptic(Haptic.HEAVY)
    }

    /** Vibra o celular, ou faz o controle em uso tremer (TV). */
    fun haptic(kind: Haptic) {
        if (!hapticsOn) return
        val ctx = appContext ?: return
        runCatching {
            val pad = gamepadVibrator()
            if (pad != null) {
                // Controle: pulsos curtos (muitos ignoram a intensidade).
                val effect = when (kind) {
                    Haptic.TICK -> VibrationEffect.createOneShot(18, 60)
                    Haptic.CLICK -> VibrationEffect.createOneShot(14, 50)
                    Haptic.HEAVY -> VibrationEffect.createWaveform(longArrayOf(0, 70, 40, 110), intArrayOf(0, 160, 0, 255), -1)
                    Haptic.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 45, 60, 45), intArrayOf(0, 200, 0, 200), -1)
                }
                pad.vibrate(effect)
                return
            }
            val v = phoneVibrator(ctx) ?: return
            if (!v.hasVibrator()) return
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(
                    when (kind) {
                        Haptic.TICK -> VibrationEffect.EFFECT_TICK
                        Haptic.CLICK -> VibrationEffect.EFFECT_CLICK
                        Haptic.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                        Haptic.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    },
                )
            } else {
                VibrationEffect.createOneShot(if (kind == Haptic.HEAVY) 40 else 12, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            v.vibrate(effect)
        }
    }

    private fun gamepadVibrator(): Vibrator? {
        val dev = InputDevice.getDevice(lastDeviceId) ?: return null
        if (dev.isVirtual || dev.sources and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD) return null
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dev.vibratorManager.defaultVibrator else @Suppress("DEPRECATION") dev.vibrator
        return v.takeIf { it.hasVibrator() }
    }

    private fun phoneVibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
}

/** Preferências de sons/vibração/música lidas das Configurações. */
data class FeelPrefs(val sounds: Boolean, val volume: Float, val haptics: Boolean, val music: Boolean, val musicVolume: Float) {
    companion object {
        fun of(snap: com.retroplay.domain.settings.SettingsSnapshot) = FeelPrefs(
            sounds = snap.value(com.retroplay.domain.settings.AppSettings.NAV_SOUND),
            volume = snap.value(com.retroplay.domain.settings.AppSettings.UI_VOLUME).toFloat() / 100f,
            haptics = snap.value(com.retroplay.domain.settings.AppSettings.HAPTICS),
            music = snap.value(com.retroplay.domain.settings.AppSettings.MUSIC),
            musicVolume = snap.value(com.retroplay.domain.settings.AppSettings.MUSIC_VOLUME).toFloat() / 100f,
        )

        /** Aplica num processo à parte (o jogo roda em ":game"). */
        fun apply(p: FeelPrefs) {
            UiFeel.soundsOn = p.sounds
            UiFeel.volume = p.volume
            UiFeel.hapticsOn = p.haptics
        }
    }
}
