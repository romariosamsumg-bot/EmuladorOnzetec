package com.retroplay.data.core

import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreUrls
import com.retroplay.domain.core.InstalledCore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Falha de instalação com um [CoreError] amigável. */
class CoreInstallException(val error: CoreError, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Baixa o zip do núcleo do buildbot, valida e instala de forma atômica.
 * Nunca deixa um .so pela metade no lugar do definitivo.
 */
class CoreInstaller(
    private val client: OkHttpClient,
    private val storage: CoreStorage,
    /** Bytes livres no volume do diretório (injetável para testes). */
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val urlFor: (coreId: String, abi: String) -> String = CoreUrls::download,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun install(
        coreId: String,
        abi: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): InstalledCore {
        if (abi !in CoreUrls.SUPPORTED_ABIS) {
            throw CoreInstallException(CoreError.UNSUPPORTED_ABI, "ABI não suportado: $abi")
        }
        val tmpDir = storage.tempDir()
        val zipFile = File(tmpDir, "$coreId-$abi.zip")
        val soTmp = File(tmpDir, "$coreId-$abi.so")
        try {
            val version = download(urlFor(coreId, abi), zipFile, onProgress)
            val entryName = "${coreId}_libretro_android.so"
            val (sha, size) = extract(zipFile, entryName, soTmp)
            val target = storage.libraryFile(coreId, abi)
            target.parentFile?.mkdirs()
            // Mantém a versão em uso como "anterior" (Configurações → Núcleos → voltar versão).
            storage.backupCurrent(coreId, abi)
            if (!soTmp.renameTo(target)) {
                target.delete()
                if (!soTmp.renameTo(target)) throw CoreInstallException(CoreError.UNKNOWN, "Falha ao mover o núcleo")
            }
            val installed = InstalledCore(coreId, abi, target.absolutePath, version, sha, size, clock())
            storage.write(installed)
            return installed
        } finally {
            zipFile.delete()
            soTmp.delete()
        }
    }

    /** Versão publicada no buildbot (ETag/Last-Modified), sem baixar o arquivo. */
    fun remoteVersion(coreId: String, abi: String): String? = runCatching {
        client.newCall(Request.Builder().url(urlFor(coreId, abi)).head().build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.header("ETag")?.trim('"') ?: resp.header("Last-Modified")
        }
    }.getOrNull()

    private suspend fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit): String {
        val request = Request.Builder().url(url).build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw CoreInstallException(CoreError.NO_NETWORK, "Sem conexão com $url", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw CoreInstallException(CoreError.SERVER, "HTTP ${resp.code} em $url")
            }
            val body = resp.body
            val total = body.contentLength()
            // Zip + .so extraído (núcleos comprimem ~2-3x; margem de 4x).
            if (total > 0 && freeSpace(dest.parentFile!!) < total * 4) {
                throw CoreInstallException(CoreError.NO_SPACE, "Espaço insuficiente para baixar o núcleo")
            }
            var downloaded = 0L
            try {
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var lastReport = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            if (downloaded - lastReport >= 128 * 1024) {
                                onProgress(downloaded, total)
                                lastReport = downloaded
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (isNoSpace(e)) throw CoreInstallException(CoreError.NO_SPACE, "Sem espaço", e)
                throw CoreInstallException(CoreError.NO_NETWORK, "Download interrompido", e)
            }
            if (total > 0 && downloaded != total) {
                throw CoreInstallException(CoreError.NO_NETWORK, "Download incompleto ($downloaded de $total)")
            }
            onProgress(downloaded, if (total > 0) total else downloaded)
            return resp.header("ETag")?.trim('"')
                ?: resp.header("Last-Modified")
                ?: "desconhecida"
        }
    }

    /** Extrai a entrada do zip calculando SHA-256; retorna (hash, tamanho). */
    private suspend fun extract(zip: File, entryName: String, dest: File): Pair<String, Long> {
        try {
            ZipFile(zip).use { zf ->
                val entry = zf.getEntry(entryName)
                    ?: throw CoreInstallException(CoreError.CORRUPTED, "Arquivo $entryName ausente no zip")
                if (entry.size > 0 && freeSpace(dest.parentFile!!) < entry.size + SPACE_MARGIN) {
                    throw CoreInstallException(CoreError.NO_SPACE, "Espaço insuficiente para instalar o núcleo")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                zf.getInputStream(entry).use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            digest.update(buf, 0, n)
                            output.write(buf, 0, n)
                            size += n
                        }
                    }
                }
                if (size == 0L || !isElf(dest)) {
                    throw CoreInstallException(CoreError.CORRUPTED, "Núcleo inválido (não é uma biblioteca ELF)")
                }
                return digest.digest().joinToString("") { "%02x".format(it) } to size
            }
        } catch (e: ZipException) {
            throw CoreInstallException(CoreError.CORRUPTED, "Zip corrompido", e)
        } catch (e: IOException) {
            if (isNoSpace(e)) throw CoreInstallException(CoreError.NO_SPACE, "Sem espaço", e)
            throw CoreInstallException(CoreError.CORRUPTED, "Falha ao extrair o núcleo", e)
        }
    }

    private fun isElf(file: File): Boolean = file.inputStream().use { input ->
        val magic = ByteArray(4)
        input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
    }

    private fun isNoSpace(e: IOException): Boolean =
        e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true

    private companion object {
        const val SPACE_MARGIN = 8L * 1024 * 1024
    }
}
