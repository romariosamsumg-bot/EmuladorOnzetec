package com.retroplay.data.info

import com.retroplay.domain.library.GameInfoSource
import com.retroplay.domain.library.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Resumo do jogo pela Wikipédia (gratuita, sem chave): procura o artigo do jogo no idioma do app
 * e, se não achar, na Wikipédia em inglês. Só aceita artigos que falam de jogo eletrônico, para
 * não mostrar o resumo de um filme ou de uma pessoa com o mesmo nome.
 *
 * Guarda o resultado em disco: cada jogo é buscado uma vez (sem resultado, tenta de novo em 7 dias).
 * Só o título do jogo e o nome do console são enviados.
 */
class WikipediaGameInfo(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val languages: () -> List<String>,
    private val baseUrl: (lang: String) -> String = { "https://$it.wikipedia.org" },
    private val clock: () -> Long = System::currentTimeMillis,
) : GameInfoSource {

    override suspend fun summary(title: String, systemName: String): GameSummary? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(title)
        if (clean.isBlank()) return@withContext null
        val langs = languages().distinct()
        val file = File(cacheDir, key(langs.joinToString(",") + "|" + systemName + "|" + clean) + ".json")
        readCache(file)?.let { return@withContext it.getOrNull() }
        var networkFailed = false
        for (lang in langs) {
            val found = runCatching { lookup(lang, clean, systemName) }
            if (found.isFailure) { networkFailed = true; continue }
            found.getOrNull()?.let { writeCache(file, it); return@withContext it }
        }
        // Sem internet: não grava (tenta de novo depois). Sem artigo: grava "nada" por 7 dias.
        if (!networkFailed) writeCache(file, null)
        null
    }

    private fun lookup(lang: String, title: String, systemName: String): GameSummary? {
        val base = baseUrl(lang).trimEnd('/')
        val search = "$base/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("list", "search")
            .addQueryParameter("srsearch", "$title $systemName").addQueryParameter("srlimit", "5")
            .addQueryParameter("format", "json").build()
        val page = pickArticle(get(search) ?: return null, title) ?: return null
        val summaryUrl = "$base/api/rest_v1/page/summary/".toHttpUrl().newBuilder().addPathSegment(page.replace(' ', '_')).build()
        return parseSummary(get(summaryUrl) ?: return null, if (lang == "pt") "Wikipédia" else "Wikipedia ($lang)")
    }

    private fun get(url: HttpUrl): String? {
        val req = Request.Builder().url(url).header("User-Agent", "OnzetecGames/1.0 (Android TV app)").build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body.string()
        }
    }

    private fun readCache(file: File): Result<GameSummary?>? {
        if (!file.isFile) return null
        return runCatching {
            val o = Json.parseToJsonElement(file.readText()).jsonObject
            val text = o["text"]?.jsonPrimitive?.contentOrNull
            if (text == null) {
                val at = o["at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                if (clock() - at > RETRY_MS) return null
                null
            } else GameSummary(text, o["source"]?.jsonPrimitive?.contentOrNull.orEmpty(), o["url"]?.jsonPrimitive?.contentOrNull)
        }.let { if (it.isFailure) null else it }
    }

    private fun writeCache(file: File, s: GameSummary?) {
        runCatching {
            file.parentFile?.mkdirs()
            val o = buildMap {
                put("at", kotlinx.serialization.json.JsonPrimitive(clock().toString()))
                if (s != null) {
                    put("text", kotlinx.serialization.json.JsonPrimitive(s.text))
                    put("source", kotlinx.serialization.json.JsonPrimitive(s.source))
                    s.url?.let { put("url", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            }
            file.writeText(JsonObject(o).toString())
        }
    }

    companion object {
        private const val RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private val GAME_WORDS = listOf("jogo eletrônico", "jogo de vídeo", "videojogo", "video game", "videogame", "videojuego", "jogo de", "game", "arcade")

        /** "Final Fantasy VII (Disco 2)" → "Final Fantasy VII". */
        fun cleanTitle(title: String) = title.replace(Regex("\\s*\\((Disco?|Disc|Rev|Beta|Proto)[^)]*\\)", RegexOption.IGNORE_CASE), "").trim()

        /**
         * Escolhe o artigo na resposta da busca: o primeiro cujo trecho fala de jogo eletrônico e
         * cujo título contém a primeira palavra do jogo (evita artigos que só citam o jogo).
         */
        fun pickArticle(json: String, title: String): String? {
            val results = Json.parseToJsonElement(json).jsonObject["query"]?.jsonObject?.get("search") as? JsonArray ?: return null
            val firstWord = title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).firstOrNull { it.length >= 2 } ?: return null
            return results.map { it.jsonObject }.firstOrNull { r ->
                val t = r["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val snippet = r["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
                firstWord in t.lowercase() && GAME_WORDS.any { it in snippet || it in t.lowercase() }
            }?.get("title")?.jsonPrimitive?.contentOrNull
        }

        /** Resumo da API REST (campo "extract"); ignora páginas de desambiguação. */
        fun parseSummary(json: String, source: String): GameSummary? {
            val o = Json.parseToJsonElement(json).jsonObject
            if (o["type"]?.jsonPrimitive?.contentOrNull == "disambiguation") return null
            val text = o["extract"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.length < 40) return null
            val url = o["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull
            return GameSummary(text, source, url)
        }

        private fun key(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
