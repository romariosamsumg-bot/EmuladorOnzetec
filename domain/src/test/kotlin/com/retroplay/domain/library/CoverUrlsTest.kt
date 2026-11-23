package com.retroplay.domain.library

import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverUrlsTest {
    @Test
    fun `url segue o padrao do libretro-thumbnails`() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/master/Named_Boxarts/Super%20Mario%20World%20%28USA%29.png",
            CoverUrls.url(SystemRegistry.SNES, "Super Mario World (USA)", CoverType.BOXART),
        )
    }

    @Test
    fun `caracteres proibidos viram sublinhado`() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro-thumbnails/MAME/master/Named_Titles/The%20King%20of%20Fighters%20%2798%20_%20X.png",
            CoverUrls.url(SystemRegistry.ARCADE, "The King of Fighters '98 / X", CoverType.TITLE),
        )
        assertEquals("a_b_c_d", CoverUrls.sanitize("a&b:c?d"))
    }
}
