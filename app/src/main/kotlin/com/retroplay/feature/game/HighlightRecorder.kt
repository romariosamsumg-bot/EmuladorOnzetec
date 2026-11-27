package com.retroplay.feature.game

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import java.io.FileDescriptor

/**
 * Highlights: grava continuamente os últimos segundos do jogo (só vídeo) num buffer em memória,
 * já comprimido em H.264. "Salvar" junta os últimos 5/10/15 s num MP4, sem parar o jogo.
 *
 * - A tela do jogo é copiada ~[FPS] vezes por segundo (PixelCopy, já reduzida para 360p) e
 *   desenhada na superfície de entrada do codificador de vídeo do aparelho.
 * - Um quadro-chave por segundo: o clipe sempre começa num quadro-chave.
 * - Pausas (menu do jogo) não viram "vídeo parado": os tempos são refeitos ao salvar.
 * Memória: ~2 Mbit/s × [KEEP_SECONDS] ≈ 5 MB.
 */
class HighlightRecorder(private val view: SurfaceView) {

    private class Sample(val data: ByteArray, val ptsUs: Long, val key: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("highlights").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val lock = Any()
    private val samples = ArrayDeque<Sample>()
    private var format: MediaFormat? = null
    private var codec: MediaCodec? = null
    private var input: Surface? = null
    private var frame: Bitmap? = null
    private var running = false
    private var copying = false
    private var failed = false

    /** Segundos de jogo já guardados (para o menu mostrar o que dá para salvar). */
    val availableSeconds: Int get() = synchronized(lock) { (activeUs(samples) / 1_000_000L).toInt() }

    fun start() {
        if (running || failed) return
        if (codec == null && !setUp()) return
        running = true
        main.post(tick)
    }

    /** Pausa a captura (menu aberto, app em segundo plano); o que já foi gravado fica. */
    fun pause() {
        running = false
        main.removeCallbacks(tick)
    }

    fun release() {
        pause()
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { input?.release() }
        codec = null
        input = null
        worker.quitSafely()
        synchronized(lock) { samples.clear() }
    }

    private fun setUp(): Boolean {
        if (view.width <= 0 || view.height <= 0) return false
        // 360 linhas, largura múltipla de 16 na proporção da tela (ex.: 640×360).
        val h = HEIGHT
        val w = ((h * view.width.toFloat() / view.height) / 16).toInt().coerceAtLeast(8) * 16
        return runCatching {
            val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.setCallback(callback, workerHandler)
            c.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            input = c.createInputSurface()
            c.start()
            codec = c
            frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            true
        }.getOrElse {
            // Aparelho sem codificador compatível: os highlights ficam desligados nesta sessão.
            failed = true
            runCatching { codec?.release() }
            codec = null
            false
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            capture()
            main.postDelayed(this, 1000L / FPS)
        }
    }

    private fun capture() {
        val bmp = frame ?: return
        val surface = input ?: return
        if (copying || !view.holder.surface.isValid) return
        copying = true
        try {
            PixelCopy.request(view, bmp, { result ->
                copying = false
                if (result == PixelCopy.SUCCESS && running) {
                    runCatching {
                        val canvas = surface.lockHardwareCanvas()
                        canvas.drawBitmap(bmp, null, Rect(0, 0, bmp.width, bmp.height), null)
                        surface.unlockCanvasAndPost(canvas)
                    }
                }
            }, main)
        } catch (e: IllegalArgumentException) {
            copying = false
        }
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            runCatching {
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                    val buf = codec.getOutputBuffer(index)!!
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size).also { buf.get(it) }
                    synchronized(lock) {
                        samples.addLast(Sample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0))
                        trim()
                    }
                }
            }
            runCatching { codec.releaseOutputBuffer(index, false) }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            failed = true
            pause()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            this@HighlightRecorder.format = format
        }
    }

    /** Mantém só os últimos [KEEP_SECONDS] (de jogo) e começa sempre num quadro-chave. */
    private fun trim() {
        while (samples.size > 1 && activeUs(samples) > KEEP_SECONDS * 1_000_000L) samples.removeFirst()
        while (samples.isNotEmpty() && !samples.first().key) samples.removeFirst()
    }

    /**
     * Grava os últimos [seconds] segundos em [fd] (MP4). Retorna a duração gravada (ms) ou null
     * se ainda não há o que salvar.
     */
    fun save(seconds: Int, fd: FileDescriptor): Long? {
        val fmt = format ?: return null
        val clip = synchronized(lock) { pick(samples.toList(), seconds * 1_000_000L) }
        if (clip.isEmpty()) return null
        val times = retime(clip)
        val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = muxer.addTrack(fmt)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            clip.forEachIndexed { i, s ->
                info.set(0, s.data.size, times[i], if (s.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, java.nio.ByteBuffer.wrap(s.data), info)
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
        }
        return times.last() / 1000 + 1000L / FPS
    }

    companion object {
        const val FPS = 24
        const val HEIGHT = 360
        const val BITRATE = 2_000_000
        const val KEEP_SECONDS = 16

        /** Intervalo máximo entre quadros contado como "jogo": lacunas maiores são pausas. */
        private const val MAX_GAP_US = 250_000L

        private fun gap(a: Long, b: Long) = (b - a).coerceIn(0L, MAX_GAP_US)

        private fun activeUs(list: Collection<Sample>): Long {
            var total = 0L
            var prev: Long? = null
            for (s in list) {
                prev?.let { total += gap(it, s.ptsUs) }
                prev = s.ptsUs
            }
            return total
        }

        /** Os últimos [windowUs] de jogo, começando no quadro-chave anterior mais próximo. */
        private fun pick(all: List<Sample>, windowUs: Long): List<Sample> {
            if (all.isEmpty()) return emptyList()
            var i = all.lastIndex
            var acc = 0L
            while (i > 0 && acc < windowUs) {
                acc += gap(all[i - 1].ptsUs, all[i].ptsUs)
                i--
            }
            while (i > 0 && !all[i].key) i--
            return if (all[i].key) all.subList(i, all.size) else emptyList()
        }

        /** Tempos contínuos a partir de 0 (pausas encolhidas para no máximo [MAX_GAP_US]). */
        private fun retime(clip: List<Sample>): LongArray {
            val out = LongArray(clip.size)
            for (i in 1 until clip.size) out[i] = out[i - 1] + gap(clip[i - 1].ptsUs, clip[i].ptsUs).coerceAtLeast(1)
            return out
        }

        /** Para testes: escolhe o trecho a salvar a partir de (pts, quadro-chave). */
        internal fun pickForTest(pts: List<Pair<Long, Boolean>>, windowUs: Long): List<Long> =
            pick(pts.map { Sample(ByteArray(0), it.first, it.second) }, windowUs).map { it.ptsUs }

        internal fun activeForTest(pts: List<Long>): Long = activeUs(pts.map { Sample(ByteArray(0), it, true) })
    }
}
