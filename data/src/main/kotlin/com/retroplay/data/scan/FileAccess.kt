package com.retroplay.data.scan

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.retroplay.domain.library.GameSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

/** Arquivo encontrado ao percorrer uma pasta monitorada. */
data class FoundFile(
    val uri: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    /** Pastas entre a raiz monitorada e o arquivo. */
    val parents: List<String>,
)

/** Abre arquivos de caminhos comuns ou de URIs do seletor do sistema (SAF), de forma uniforme. */
class FileAccess(private val resolver: ContentResolver) {

    fun openStream(uri: String): InputStream =
        if (isDocument(uri)) resolver.openInputStream(Uri.parse(uri)) ?: error("Não foi possível abrir $uri")
        else File(uri).inputStream()

    /** Apaga o arquivo. Lança [SecurityException] se não houver permissão de escrita. */
    fun delete(uri: String): Boolean =
        if (isDocument(uri)) DocumentsContract.deleteDocument(resolver, Uri.parse(uri))
        else File(uri).let { !it.exists() || it.delete() }

    /** Canal com posicionamento: permite ler só o índice de um .zip/.7z grande. */
    fun openChannel(uri: String): SeekableByteChannel =
        if (isDocument(uri)) {
            val pfd = resolver.openFileDescriptor(Uri.parse(uri), "r") ?: error("Não foi possível abrir $uri")
            val stream = FileInputStream(pfd.fileDescriptor)
            ClosingChannel(stream.channel) { stream.close(); pfd.close() }
        } else {
            FileInputStream(File(uri)).channel
        }

    /**
     * Percorre a pasta recursivamente (até [maxDepth]), ignorando pastas ocultas e "Android".
     * Lança [IOException] se a pasta não estiver acessível (pendrive removido, permissão perdida),
     * para que a varredura NÃO apague os jogos dessa pasta.
     */
    fun walk(rootUri: String, source: GameSource, maxDepth: Int = 6): Sequence<FoundFile> =
        if (source == GameSource.DOCUMENT) {
            val tree = Uri.parse(rootUri)
            val probe = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            resolver.query(probe, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.close()
                ?: throw IOException("Pasta inacessível: $rootUri")
            walkDocuments(tree, maxDepth)
        } else {
            val root = File(rootUri)
            if (!root.isDirectory || root.listFiles() == null) throw IOException("Pasta inacessível: $rootUri")
            walkFiles(root, maxDepth)
        }

    private fun walkFiles(root: File, maxDepth: Int): Sequence<FoundFile> = sequence {
        suspend fun SequenceScope<FoundFile>.visit(dir: File, parents: List<String>, depth: Int) {
            val children = dir.listFiles() ?: return
            for (f in children.sortedBy { it.name.lowercase() }) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (depth < maxDepth && f.name != "Android") visit(f, parents + f.name, depth + 1)
                } else {
                    yield(FoundFile(f.absolutePath, f.name, f.length(), f.lastModified(), parents))
                }
            }
        }
        visit(root, emptyList(), 0)
    }

    private fun walkDocuments(tree: Uri, maxDepth: Int): Sequence<FoundFile> = sequence {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        suspend fun SequenceScope<FoundFile>.visit(docId: String, parents: List<String>, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            val rows: List<Row> = resolver.query(children, projection, null, null, null)?.use { c ->
                val out = ArrayList<Row>()
                while (c.moveToNext()) {
                    val size: Long = if (c.isNull(3)) 0L else c.getLong(3)
                    val modified: Long = if (c.isNull(4)) 0L else c.getLong(4)
                    out.add(Row(c.getString(0), c.getString(1).orEmpty(), c.getString(2), size, modified))
                }
                out
            } ?: return
            for (r in rows.sortedBy { it.name.lowercase() }) {
                if (r.name.startsWith(".")) continue
                if (r.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (depth < maxDepth && r.name != "Android") visit(r.id, parents + r.name, depth + 1)
                } else {
                    yield(FoundFile(DocumentsContract.buildDocumentUriUsingTree(tree, r.id).toString(), r.name, r.size, r.modified, parents))
                }
            }
        }
        visit(DocumentsContract.getTreeDocumentId(tree), emptyList(), 0)
    }

    private data class Row(val id: String, val name: String, val mime: String?, val size: Long, val modified: Long)

    companion object {
        fun isDocument(uri: String) = uri.startsWith("content://")
    }
}

/** Canal que libera recursos extras (descritor do SAF) ao fechar. */
private class ClosingChannel(
    private val inner: java.nio.channels.FileChannel,
    private val onClose: () -> Unit,
) : SeekableByteChannel by inner {
    override fun close() {
        try { inner.close() } finally { onClose() }
    }
}
