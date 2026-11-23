package com.retroplay.diagnostics

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de erros local (Sobre → Exportar registro de erros). Guarda travamentos e erros
 * relevantes em filesDir/logs/errors.txt, limitado a [MAX_BYTES] (mantém o final).
 */
object ErrorLog {
    const val MAX_BYTES = 256 * 1024
    private const val TAG = "RetroPlay"

    fun file(dir: File): File = File(dir, "logs/errors.txt")

    /** Instala o registro de travamentos (cada processo, inclusive o ":game", chama isto). */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val process = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else app.packageName
            runCatching { record(app.filesDir, "CRASH em ${thread.name} ($process)", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun record(filesDir: File, message: String, error: Throwable? = null) {
        val f = file(filesDir)
        f.parentFile?.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            append("[").append(stamp).append("] ").append(message).append('\n')
            if (error != null) append(StringWriter().also { error.printStackTrace(PrintWriter(it)) }).append('\n')
        }
        f.appendText(text)
        trim(f)
        if (error != null) Log.e(TAG, message, error) else Log.w(TAG, message)
    }

    /** Mantém só o final do arquivo quando passa do limite. */
    fun trim(f: File, max: Int = MAX_BYTES) {
        if (f.length() <= max) return
        val bytes = f.readBytes()
        val keep = bytes.copyOfRange(bytes.size - max * 3 / 4, bytes.size)
        val cut = keep.indexOf('\n'.code.toByte()).let { if (it in 0 until keep.size - 1) it + 1 else 0 }
        f.writeBytes(keep.copyOfRange(cut, keep.size))
    }

    /** Cabeçalho com dados do aparelho, para o arquivo exportado. */
    fun header(versionName: String, abi: String?): String = buildString {
        append("Onzetec Games ").append(versionName).append('\n')
        append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        append("ABI: ").append(abi ?: "?").append("\n\n")
    }
}
