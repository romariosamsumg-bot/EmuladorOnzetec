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
 * Highlights: grava continuamente os últimos segundos do jogo num buffer em memória: o vídeo já
 * comprimido em H.264 e o som do jogo em PCM (lido do grampo de áudio do LibretroDroid modificado,
 * third_party/libretrodroid). "Salvar" junta os últimos 5/10/15 s num MP4 com vídeo e som (AAC),
 * sem parar o jogo.
 *
 * - A tela do jogo é copiada ~[FPS] vezes por segundo (PixelCopy, já reduzida para 360p) e
 *   desenhada na superfície de entrada do codificador de vídeo do aparelho.
 * - Um quadro-chave por segundo: o clipe sempre começa num quadro-chave.
 * - Pausas (menu do jogo) não viram "vídeo parado": os tempos são refeitos ao salvar.
 * Memória: ~2 Mbit/s × [KEEP_SECONDS] ≈ 5 MB.
 */
class HighlightRecorder(private val view: SurfaceView) {

    private class Sample(val data: ByteArray, val ptsUs: Long, val key: Boolean)

    /** Trecho de som: PCM estéreo intercalado, na taxa do núcleo, começando em [startNs]. */
    private class AudioChunk(val pcm: ShortArray, val startNs: Long)

    private val audioLock = Any()
    private val audio = ArrayDeque<AudioChunk>()
    private var audioRate = 0
    private val audioBuf = ShortArray(16384)
    private var audioOn = false

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
        startAudio()
    }

    /** Liga o grampo de áudio do núcleo e drena a cada 100 ms (sem o grampo: highlight sem som). */
    private fun startAudio() {
        if (!audioOn) {
            audioOn = runCatching { com.swordfish.libretrodroid.LibretroDroid.setAudioTapEnabled(true) }.isSuccess
        }
        if (audioOn) {
            workerHandler.removeCallbacks(drainAudio)
            workerHandler.post(drainAudio)
        }
    }

    private val drainAudio = object : Runnable {
        override fun run() {
            if (!audioOn) return
            runCatching { pullAudio() }
            if (running) workerHandler.postDelayed(this, 100)
        }
    }

    @Synchronized
    private fun pullAudio() {
        val rate = com.swordfish.libretrodroid.LibretroDroid.audioTapSampleRate()
        if (rate <= 0) return
        val endNs = com.swordfish.libretrodroid.LibretroDroid.audioTapLastWriteNs()
        val parts = ArrayList<ShortArray>()
        var total = 0
        while (true) {
            val n = com.swordfish.libretrodroid.LibretroDroid.readAudioTap(audioBuf)
            if (n <= 0) break
            parts += audioBuf.copyOf(n)
            total += n
            if (n < audioBuf.size) break
        }
        if (total == 0) return
        val pcm = ShortArray(total)
        var o = 0
        parts.forEach { System.arraycopy(it, 0, pcm, o, it.size); o += it.size }
        // O último quadro do lote foi escrito em [endNs]: o lote começa (amostras / taxa) antes.
        val startNs = endNs - (total / 2) * 1_000_000_000L / rate
        synchronized(audioLock) {
            if (rate != audioRate) { audio.clear(); audioRate = rate }
            audio.addLast(AudioChunk(pcm, startNs))
            var frames = audio.sumOf { it.pcm.size / 2 }.toLong()
            while (audio.size > 1 && frames > (KEEP_SECONDS + 2L) * rate) {
                frames -= audio.removeFirst().pcm.size / 2
            }
        }
    }

    /** Pausa a captura (menu aberto, app em segundo plano); o que já foi gravado fica. */
    fun pause() {
        running = false
        main.removeCallbacks(tick)
    }

    fun release() {
        pause()
        if (audioOn) runCatching { com.swordfish.libretrodroid.LibretroDroid.setAudioTapEnabled(false) }
        audioOn = false
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
        val durationUs = times.last() + 1_000_000L / FPS
        // Som do mesmo trecho: do 1º quadro do vídeo em diante, com a mesma duração.
        val encodedAudio = runCatching { encodeAudio(clip.first().ptsUs * 1000, durationUs) }.getOrNull()
        val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val track = muxer.addTrack(fmt)
            val audioTrack = encodedAudio?.let { muxer.addTrack(it.format) }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            clip.forEachIndexed { i, s ->
                info.set(0, s.data.size, times[i], if (s.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, java.nio.ByteBuffer.wrap(s.data), info)
            }
            if (encodedAudio != null && audioTrack != null) {
                encodedAudio.samples.forEach { a ->
                    info.set(0, a.data.size, a.ptsUs, 0)
                    muxer.writeSampleData(audioTrack, java.nio.ByteBuffer.wrap(a.data), info)
                }
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
        }
        return durationUs / 1000
    }

    private class EncodedAudio(val format: MediaFormat, val samples: List<Sample>)

    /** Junta o som a partir de [fromNs] (mesmo relógio do vídeo), reamostra e codifica em AAC. */
    private fun encodeAudio(fromNs: Long, durationUs: Long): EncodedAudio? {
        // Pega o que ainda estava no grampo (o menu acabou de abrir).
        if (audioOn) runCatching { pullAudio() }
        val (chunks, rate) = synchronized(audioLock) { audio.map { it.startNs to it.pcm } to audioRate }
        if (rate <= 0 || chunks.isEmpty()) return null
        val pcm = sliceAudio(chunks, rate, fromNs, durationUs)
        if (pcm.size < 2 * rate / 10) return null // menos de 0,1 s de som
        val out = resampleStereo(pcm, rate, AAC_RATE)
        return encodeAac(out)
    }

    private fun encodeAac(pcm: ShortArray): EncodedAudio? {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AAC_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            val info = MediaCodec.BufferInfo()
            val samples = ArrayList<Sample>()
            var outFormat: MediaFormat? = null
            var pos = 0 // em amostras (L+R)
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && guard++ < 100_000) {
                if (!inputDone) {
                    val i = enc.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val buf = enc.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf((buf.capacity() / 2) and 1.inv(), 2048, pcm.size - pos)
                        val ptsUs = (pos / 2).toLong() * 1_000_000L / AAC_RATE
                        if (n <= 0) {
                            enc.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, pos, n)
                            enc.queueInputBuffer(i, 0, n * 2, ptsUs, 0)
                            pos += n
                        }
                    }
                }
                val o = enc.dequeueOutputBuffer(info, 5_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = enc.outputFormat
                    o >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val b = enc.getOutputBuffer(o)!!
                            b.position(info.offset); b.limit(info.offset + info.size)
                            samples += Sample(ByteArray(info.size).also { b.get(it) }, info.presentationTimeUs, false)
                        }
                        enc.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            val f = outFormat ?: return null
            return if (samples.isEmpty()) null else EncodedAudio(f, samples)
        } finally {
            runCatching { enc.stop() }
            runCatching { enc.release() }
        }
    }

    companion object {
        /** Taxa do AAC (os núcleos usam taxas como 32040 Hz, que o codificador não aceita). */
        const val AAC_RATE = 44_100

        /**
         * Som do trecho: a partir de [fromNs], [durationUs] de duração (estéreo intercalado).
         * Trechos em que o jogo estava pausado não geram som, então juntar em ordem já acompanha
         * o vídeo (que também encolhe as pausas).
         */
        internal fun sliceAudio(chunks: List<Pair<Long, ShortArray>>, rate: Int, fromNs: Long, durationUs: Long): ShortArray {
            val wantFrames = (durationUs * rate / 1_000_000L).toInt()
            val out = ShortArray(wantFrames * 2)
            var w = 0
            for ((startNs, pcm) in chunks) {
                val frames = pcm.size / 2
                val endNs = startNs + frames * 1_000_000_000L / rate
                if (endNs <= fromNs) continue
                val skip = if (startNs < fromNs) ((fromNs - startNs) * rate / 1_000_000_000L).toInt().coerceIn(0, frames) else 0
                val n = minOf((frames - skip) * 2, out.size - w)
                if (n <= 0) break
                System.arraycopy(pcm, skip * 2, out, w, n)
                w += n
                if (w >= out.size) break
            }
            return if (w == out.size) out else out.copyOf(w)
        }

        /** Reamostragem linear (estéreo) de [from] Hz para [to] Hz. */
        internal fun resampleStereo(pcm: ShortArray, from: Int, to: Int): ShortArray {
            if (from == to) return pcm
            val inFrames = pcm.size / 2
            val outFrames = (inFrames.toLong() * to / from).toInt()
            val out = ShortArray(outFrames * 2)
            val step = from.toDouble() / to
            for (i in 0 until outFrames) {
                val x = i * step
                val a = x.toInt().coerceAtMost(inFrames - 1)
                val b = (a + 1).coerceAtMost(inFrames - 1)
                val t = x - a
                for (c in 0..1) {
                    out[i * 2 + c] = (pcm[a * 2 + c] * (1 - t) + pcm[b * 2 + c] * t).toInt().toShort()
                }
            }
            return out
        }

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
