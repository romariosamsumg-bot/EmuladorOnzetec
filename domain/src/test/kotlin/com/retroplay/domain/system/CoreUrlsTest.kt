package com.retroplay.domain.system

import com.retroplay.domain.core.CoreUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreUrlsTest {
    @Test
    fun `url nightly segue o padrao do buildbot`() {
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/snes9x2010_libretro_android.so.zip",
            CoreUrls.download("snes9x2010", "armeabi-v7a"),
        )
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/genesis_plus_gx_libretro_android.so.zip",
            CoreUrls.download(SystemRegistry.MEGA_DRIVE.defaultCore.id, "arm64-v8a"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `abi desconhecido e rejeitado`() {
        CoreUrls.download("snes9x2010", "mips")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `id com caracteres perigosos e rejeitado`() {
        CoreUrls.download("../x", "arm64-v8a")
    }
}
