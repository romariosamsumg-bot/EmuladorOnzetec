package com.retroplay.domain.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemDetectorTest {

    private val detector = SystemDetector(SystemRegistry)

    @Test
    fun `extensoes exclusivas identificam um unico sistema`() {
        assertEquals(ExtensionMatch.Unique(SystemRegistry.SNES), detector.matchByFileName("Jogo.SFC"))
        assertEquals(ExtensionMatch.Unique(SystemRegistry.SNES), detector.matchByFileName("jogo.smc"))
        assertEquals(ExtensionMatch.Unique(SystemRegistry.MEGA_DRIVE), detector.matchByFileName("/roms/jogo.md"))
        assertEquals(ExtensionMatch.Unique(SystemRegistry.MEGA_DRIVE), detector.matchByFileName("jogo.gen"))
        assertEquals(ExtensionMatch.Unique(SystemRegistry.MASTER_SYSTEM), detector.matchByFileName("jogo.sms"))
    }

    @Test
    fun `bin e exclusivo do mega drive entre os consoles suportados`() {
        assertEquals(ExtensionMatch.Unique(SystemRegistry.MEGA_DRIVE), detector.matchByFileName("jogo.bin"))
    }

    @Test
    fun `zip e ambiguo entre os 4 sistemas`() {
        val match = detector.matchByFileName("kof98.zip")
        assertTrue(match is ExtensionMatch.Ambiguous)
        assertEquals(4, (match as ExtensionMatch.Ambiguous).candidates.size)
    }

    @Test
    fun `extensao desconhecida ou ausente nao e suportada`() {
        assertEquals(ExtensionMatch.Unsupported, detector.matchByFileName("foto.jpg"))
        assertEquals(ExtensionMatch.Unsupported, detector.matchByFileName("semextensao"))
        assertEquals(ExtensionMatch.Unsupported, detector.matchByFileName(".oculto"))
        assertEquals(ExtensionMatch.Unsupported, detector.matchByFileName("terminaemponto."))
    }

    @Test
    fun `extensionOf lida com caminhos windows e unix`() {
        assertEquals("sfc", SystemDetector.extensionOf("C:\\roms\\a.b\\jogo.SFC"))
        assertEquals("7z", SystemDetector.extensionOf("/mnt/usb/pasta.x/jogo.7z"))
        assertNull(SystemDetector.extensionOf("/mnt/usb/pasta.x/jogo"))
    }
}
