package com.retroplay.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateInfoTest {
    private val json = """
        {"versionCode": 250, "versionName": "1.15.0", "notes": ["Highlights com som", "Menu novo"],
         "apk": {"tv": "/downloads/OnzetecGames.apk", "mobile": "https://cdn.exemplo.com/OnzetecGames-Celular.apk"}}
    """.trimIndent()

    @Test
    fun le_a_versao_e_resolve_o_apk_de_cada_app() {
        val tv = UpdateInfo.parse(json, "tv", "https://onzetecgames.pages.dev/version.json")!!
        assertEquals(250, tv.versionCode)
        assertEquals("1.15.0", tv.versionName)
        assertEquals(listOf("Highlights com som", "Menu novo"), tv.notes)
        assertEquals("https://onzetecgames.pages.dev/downloads/OnzetecGames.apk", tv.apkUrl)
        val phone = UpdateInfo.parse(json, "mobile", "https://onzetecgames.pages.dev/version.json")!!
        assertEquals("https://cdn.exemplo.com/OnzetecGames-Celular.apk", phone.apkUrl)
    }

    @Test
    fun json_invalido_ou_sem_https_e_ignorado() {
        assertNull(UpdateInfo.parse("{}", "tv", "https://x.dev/version.json"))
        assertNull(UpdateInfo.parse(json, "tv", "http://inseguro.dev/version.json"))
    }
}
