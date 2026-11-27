package com.retroplay.ui.feel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.retroplay.R

/**
 * Música de fundo dos menus (original, em loop). Entra e sai com fade, para ao abrir um jogo
 * ou sair do app, e respeita o foco de áudio (pausa se outro app tocar som).
 */
object MenuMusic {
    private const val FADE_MS = 1200L
    private const val STEP_MS = 40L

    @Volatile var enabled = true
    @Volatile var volume = 0.2f

    private var player: MediaPlayer? = null
    private var current = 0f
    private var target = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null
    private var focus: AudioFocusRequest? = null

    /** Tela do app visível: começa (ou volta) com fade-in. */
    fun start(context: Context) {
        if (!enabled || volume <= 0f) return stop()
        val ctx = context.applicationContext
        if (player == null) {
            player = runCatching {
                MediaPlayer.create(ctx, R.raw.music_menu, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(), (ctx.getSystemService(AudioManager::class.java))?.generateAudioSessionId() ?: 0)
                    ?.apply { isLooping = true; setVolume(0f, 0f) }
            }.getOrNull() ?: return
            current = 0f
        }
        if (!requestFocus(ctx)) return
        runCatching { if (player?.isPlaying == false) player?.start() }
        fadeTo(volume)
    }

    /** Ao abrir um jogo ou sair: fade-out e pausa (continua do mesmo ponto na volta). */
    fun stop(fast: Boolean = false) {
        if (player == null) return
        if (fast) {
            target = 0f
            current = 0f
            handler.removeCallbacks(step)
            runCatching { player?.setVolume(0f, 0f); player?.pause() }
            abandonFocus()
        } else {
            fadeTo(0f)
        }
    }

    /** Volume novo nas Configurações (aplica na hora, com fade). */
    fun update(context: Context, on: Boolean, vol: Float) {
        enabled = on
        volume = vol
        if (on && vol > 0f) start(context) else stop()
    }

    fun release() {
        handler.removeCallbacks(step)
        runCatching { player?.release() }
        player = null
        abandonFocus()
    }

    private fun fadeTo(v: Float) {
        target = v
        handler.removeCallbacks(step)
        handler.post(step)
    }

    private val step = object : Runnable {
        override fun run() {
            val p = player ?: return
            val delta = volume.coerceAtLeast(0.05f) * STEP_MS / FADE_MS
            current = if (current < target) (current + delta).coerceAtMost(target) else (current - delta).coerceAtLeast(target)
            runCatching { p.setVolume(current, current) }
            if (current != target) {
                handler.postDelayed(this, STEP_MS)
            } else if (target == 0f) {
                runCatching { p.pause() }
                abandonFocus()
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop(fast = true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { player?.setVolume(current / 3, current / 3) }
            AudioManager.AUDIOFOCUS_GAIN -> runCatching { player?.setVolume(current, current) }
        }
    }

    private fun requestFocus(ctx: Context): Boolean {
        val am = audio ?: ctx.getSystemService(AudioManager::class.java)?.also { audio = it } ?: return true
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build().also { focus = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = audio ?: return
        focus?.let { am.abandonAudioFocusRequest(it) }
    }
}
