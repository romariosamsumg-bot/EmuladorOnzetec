package com.retroplay.data.info

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WikipediaGameInfoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val search = """{"query":{"search":[
        {"title":"Super Mario World (filme)","snippet":"é um filme de animação"},
        {"title":"Super Mario World","snippet":"é um <span>jogo eletrônico</span> de plataforma lançado em 1990"}]}}"""
    private val summary = """{"type":"standard","title":"Super Mario World","extract":"Super Mario World é um jogo eletrônico de plataforma desenvolvido e publicado pela Nintendo para o Super Nintendo.","content_urls":{"desktop":{"page":"https://pt.wikipedia.org/wiki/Super_Mario_World"}}}"""

    @Test
    fun acha_o_artigo_do_jogo_e_guarda_em_cache() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body(search).build())
            server.enqueue(MockResponse.Builder().body(summary).build())
            val info = WikipediaGameInfo(OkHttpClient(), tmp.root, { listOf("pt") }, { server.url("/").toString() })
            val s = info.summary("Super Mario World", "Super Nintendo")!!
            assertEquals("Wikipédia", s.source)
            assertEquals(true, s.text.startsWith("Super Mario World é um jogo"))
            assertEquals("https://pt.wikipedia.org/wiki/Super_Mario_World", s.url)
            // Pulou o filme; pediu o resumo do artigo certo.
            server.takeRequest()
            assertEquals("/api/rest_v1/page/summary/Super_Mario_World", server.takeRequest().url.encodedPath)
            // Segunda vez: do cache, sem rede.
            assertEquals(s, info.summary("Super Mario World", "Super Nintendo"))
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun sem_artigo_de_jogo_ou_sem_internet_nao_inventa() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().body("""{"query":{"search":[{"title":"Outra Coisa","snippet":"um filme"}]}}""").build())
            val info = WikipediaGameInfo(OkHttpClient(), tmp.root, { listOf("pt") }, { server.url("/").toString() })
            assertNull(info.summary("Jogo Obscuro", "Arcade"))
        }
        // Servidor fora do ar: null e nada gravado (tenta de novo depois).
        val offline = WikipediaGameInfo(OkHttpClient(), tmp.newFolder("o"), { listOf("pt") }, { "http://127.0.0.1:1/" })
        assertNull(offline.summary("Sonic", "Mega Drive"))
        assertEquals(0, tmp.root.resolve("o").listFiles()!!.size)
        assertEquals("Final Fantasy VII", WikipediaGameInfo.cleanTitle("Final Fantasy VII (Disco 2)"))
    }
}
