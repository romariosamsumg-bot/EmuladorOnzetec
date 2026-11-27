package com.retroplay.feature.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileDescriptor

/** Um print ou highlight salvo pelo app. */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val dateMs: Long,
    val sizeBytes: Long,
    val durationMs: Long? = null,
) {
    /** Nome do jogo: o arquivo é "<jogo>_<horário>.png|mp4". */
    val gameName: String get() = name.substringBeforeLast('.').substringBeforeLast('_').replace('_', ' ').ifBlank { name }
}

/**
 * Prints (Imagens/Onzetec Games) e highlights (Filmes/Onzetec Games). No Android 10+ ficam na
 * galeria (MediaStore, sem permissão); no 8 e 9, na pasta do app. A tela Mídia lista os dois.
 */
open class MediaLibrary(private val context: Context) {

    open fun list(): List<MediaItem> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) queryStore(false) + queryStore(true) else listFiles()
    }.getOrDefault(emptyList()).sortedByDescending { it.dateMs }

    open fun delete(item: MediaItem): Boolean = runCatching {
        if (item.uri.scheme == "file") File(item.uri.path!!).delete()
        else context.contentResolver.delete(item.uri, null, null) > 0
    }.getOrDefault(false)

    /** Salva um print (PNG). */
    fun saveImage(baseName: String, bitmap: Bitmap): Boolean {
        val name = "${safe(baseName)}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
            return true
        }
        val dir = imagesDir().apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }

    /** Cria um MP4 de highlight; [write] grava no descritor e retorna false se não deu. */
    fun saveVideo(baseName: String, write: (FileDescriptor) -> Boolean): Boolean {
        val name = "${safe(baseName)}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val ok = runCatching { resolver.openFileDescriptor(uri, "rw")!!.use { write(it.fileDescriptor) } }.getOrDefault(false)
            if (!ok) {
                resolver.delete(uri, null, null)
                return false
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return true
        }
        val file = File(videosDir().apply { mkdirs() }, name)
        val ok = runCatching { java.io.RandomAccessFile(file, "rw").use { write(it.fd) } }.getOrDefault(false)
        if (!ok) file.delete()
        return ok
    }

    private fun queryStore(video: Boolean): List<MediaItem> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cols = buildList {
            add(MediaStore.MediaColumns._ID); add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED); add(MediaStore.MediaColumns.SIZE)
            if (video) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(
            collection, cols,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("%/$FOLDER/%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += MediaItem(
                    uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                    name = c.getString(1) ?: "",
                    isVideo = video,
                    dateMs = c.getLong(2) * 1000,
                    sizeBytes = c.getLong(3),
                    durationMs = if (video) c.getLong(4).takeIf { it > 0 } else null,
                )
            }
        }
        return out
    }

    private fun listFiles(): List<MediaItem> =
        (imagesDir().listFiles().orEmpty().filter { it.extension == "png" }.map { it to false } +
            videosDir().listFiles().orEmpty().filter { it.extension == "mp4" }.map { it to true })
            .map { (f, video) -> MediaItem(Uri.fromFile(f), f.name, video, f.lastModified(), f.length()) }

    private fun imagesDir() = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures")
    private fun videosDir() = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies")

    companion object {
        const val FOLDER = "Onzetec Games"

        /** Nome de arquivo seguro (o jogo pode ter / ou : no nome). */
        fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80).ifBlank { "jogo" }
    }
}
